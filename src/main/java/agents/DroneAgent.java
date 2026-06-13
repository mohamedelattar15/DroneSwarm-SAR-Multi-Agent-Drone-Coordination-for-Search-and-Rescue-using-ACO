package agents;

import environment.Grid;
import environment.Position;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.SimulationConfig;
import utils.SimulationRuntimeControl;
import utils.Statistics;

/**
 * Agent Drone autonome pour la recherche et le sauvetage.
 * Explore la zone sinistrée, détecte les victimes et dépose des phéromones.
 */
public class DroneAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(DroneAgent.class);
    public static final String SERVICE_NAME = "DroneService";

    public enum DroneState { EXPLORING, RETURNING, IDLE, RETURNING_EMPTY }

    private Position position;
    private final List<Position> path = new ArrayList<>();
    private Grid grid;
    private Statistics stats;
    private SimulationConfig config;
    private Position lastDirection;
    private boolean hasFoundVictim;
    private boolean hasRecruited;
    private double totalDistance;
    private DroneState state = DroneState.EXPLORING;
    private static final AtomicInteger droneCount = new AtomicInteger(0);
    private int droneId;
    private int explorationBoostTicks;
    private int stepsSinceLastFind;
    private int stepsSinceLastReturn;
    private AID environmentAID;
    private AID baseAID;
    private final Random random = new Random();
    /** Biais directionnel : pousse le drone vers une zone spécifique */
    private Position preferredDirection;
    /** Force du biais directionnel (0.0 = aucun, 1.0 = max) */
    private static final double DIRECTIONAL_BIAS_STRENGTH = 2.0;
    /** Poids de la répulsion entre drones */
    private static final double DRONE_REPULSION_WEIGHT = 0.3;
    /** Rayon de répulsion entre drones */
    private static final int REPULSION_RADIUS = 8;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 3) {
            log.error("DroneAgent: arguments requis (grid, stats, config)");
            doDelete();
            return;
        }
        this.grid   = (Grid)             args[0];
        this.stats  = (Statistics)       args[1];
        this.config = (SimulationConfig) args[2];

        this.position = new Position(grid.getNestPosition().x, grid.getNestPosition().y);
        this.path.add(position);
        this.droneId = droneCount.incrementAndGet();

        // ✅ Biais directionnel : chaque drone a une direction préférée
        // pour couvrir des zones différentes dès le départ
        this.preferredDirection = computePreferredDirection();

        registerWithDF();
        this.environmentAID = lookupAgent(EnvironmentAgent.SERVICE_NAME);
        this.baseAID        = lookupAgent(BaseAgent.SERVICE_NAME);

        grid.updateDronePosition(getLocalName(), position);
        log.debug("Drone #{} prêt à la base {}, direction préférée: ({},{})",
                droneId, position, preferredDirection.x, preferredDirection.y);

        addBehaviour(new DroneMoveBehaviour(this, config.getTickTime()));
        addBehaviour(new EnvironmentFeedbackListener());
    }

    private void registerWithDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_NAME);
        sd.setName("Drone-" + droneId);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            log.error("Drone #{}: Échec DF", droneId, e);
        }
    }

    private AID lookupAgent(String serviceName) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceName);
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                return result[0].getName();
            }
        } catch (FIPAException e) {
            log.warn("Drone #{}: Recherche DF échouée {}", droneId, serviceName);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Behaviour : déplacement périodique
    // -------------------------------------------------------------------------

    private class DroneMoveBehaviour extends TickerBehaviour {

        public DroneMoveBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (SimulationRuntimeControl.isPaused()) {
                return;
            }
            if (explorationBoostTicks > 0) {
                explorationBoostTicks--;
            }

            stepsSinceLastFind++;
            stepsSinceLastReturn++;

            // Timeout d'exploration : batterie faible → retour à la base
            if (state == DroneState.EXPLORING
                    && stepsSinceLastFind > config.getMaxDroneSteps()) {
                log.debug("Drone #{}: Batterie faible, retour à la base", droneId);
                state = DroneState.RETURNING_EMPTY;
                stepsSinceLastFind = 0;
            }

            // Timeout de retour : sécurité anti-blocage
            if ((state == DroneState.RETURNING || state == DroneState.RETURNING_EMPTY)
                    && stepsSinceLastReturn > config.getMaxDroneSteps()) {
                log.warn("Drone #{}: Perdu, réinitialisation", droneId);
                state = DroneState.IDLE;
                stepsSinceLastReturn = 0;
            }

            // Arrivée à la base
            if ((state == DroneState.RETURNING || state == DroneState.RETURNING_EMPTY)
                    && position.equals(grid.getNestPosition())) {

                if (hasFoundVictim && baseAID != null) {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(baseAID);
                    msg.setOntology(BaseAgent.ONTOLOGY_DRONE_RETURNED);
                    msg.setContent("VICTIM_RESCUED");
                    send(msg);
                }

                path.clear();
                path.add(position);
                hasFoundVictim       = false;
                hasRecruited         = false;
                state                = DroneState.IDLE;
                totalDistance        = 0;
                lastDirection        = null;
                stepsSinceLastFind   = 0;
                stepsSinceLastReturn = 0;
                return;
            }

            // Fin de l'IDLE → nouveau départ
            if (state == DroneState.IDLE) {
                state = DroneState.EXPLORING;
            }

            moveDrone();

            // Vérification victime à portée
            if (!hasFoundVictim && isVictimNearby()) {
                hasFoundVictim = true;
                state = DroneState.RETURNING;
                stepsSinceLastFind = 0;

                // Ajouter la position exacte de la victime au chemin
                Position victimPos = getNearestVictim();
                if (victimPos != null && !position.equals(victimPos)) {
                    path.add(new Position(victimPos.x, victimPos.y));
                    totalDistance += position.distanceTo(victimPos);
                }

                // Simplifier le chemin et déposer les phéromones
                List<Position> cleanedPath = simplifyPath(path);
                path.clear();
                path.addAll(cleanedPath);
                totalDistance = computePathDistance(cleanedPath);
                depositPheromones();

                // Enregistrer et notifier l'environnement
                Position victimPosFinal = path.isEmpty() ? null : path.get(path.size() - 1);
                Statistics.PathUpdate update = stats.recordAntPath(new ArrayList<>(path), totalDistance);
                double reinforceFactor = update.getReinforcementFactor();

                if (!hasRecruited && environmentAID != null) {
                    hasRecruited = true;
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(environmentAID);
                    msg.setConversationId("drone-found-victim");
                    // Inclure la position de la victime pour aider l'environnement
                    String victimStr = (victimPosFinal != null) ? victimPosFinal.x + "," + victimPosFinal.y : "unknown";
                    msg.setContent("VICTIM_FOUND:" + reinforceFactor + ":" + update.getBestPathSignature()
                            + ":" + victimStr);
                    log.info("[SEND] Drone #{} → Environment (victime trouvée à {}, renfort {}%)",
                            droneId, victimStr, Math.round(reinforceFactor * 100));
                    send(msg);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Behaviour : écoute des retours de l'environnement
    // -------------------------------------------------------------------------

    private class EnvironmentFeedbackListener extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) {
                block();
                return;
            }
            String content = msg.getContent();
            if (content == null) {
                return;
            }

            if (content.startsWith("PATH_ACCEPTED")) {
                log.debug("[RECV] Drone #{} chemin accepté", droneId);
            } else if (content.startsWith("PATH_REJECTED")) {
                explorationBoostTicks = Math.max(explorationBoostTicks, config.getExplorationBoostTicks());
                log.debug("[RECV] Drone #{} chemin rejeté, exploration boostée", droneId);
            } else if (content.startsWith("DIVERSIFY")) {
                explorationBoostTicks = Math.max(explorationBoostTicks, config.getDiversificationBoostTicks());
                log.debug("[RECV] Drone #{} diversification", droneId);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Déplacement
    // -------------------------------------------------------------------------

    private void moveDrone() {
        Position nextPos;

        if (state == DroneState.RETURNING || state == DroneState.RETURNING_EMPTY) {
            if (path.size() > 1) {
                nextPos = path.get(path.size() - 2);
                path.remove(path.size() - 1);
            } else {
                return;
            }
        } else {
            nextPos = chooseNextPosition();
        }

        if (nextPos != null) {
            double dist = position.distanceTo(nextPos);
            totalDistance += dist;
            lastDirection = new Position(nextPos.x - position.x, nextPos.y - position.y);
            position = nextPos;

            if (state == DroneState.EXPLORING) {
                path.add(new Position(position.x, position.y));
            }
            grid.updateDronePosition(getLocalName(), position);
            // ✅ Déposer une phéromone de présence légère pour éviter
            // que d'autres drones ne viennent dans la même zone
            grid.addNegativePheromone(position, 0.1);
        }
    }

    private Position chooseNextPosition() {
        List<Position> neighbors = grid.getValidNeighbors(position);
        if (neighbors.isEmpty()) {
            return null;
        }

        Position previousPosition = path.size() > 1 ? path.get(path.size() - 2) : null;

        List<Position> filtered = new ArrayList<>(neighbors);
        if (previousPosition != null && filtered.size() > 1) {
            filtered.removeIf(n -> n.equals(previousPosition));
        }
        if (filtered.isEmpty()) {
            filtered = neighbors;
        }

        double explorationRate = SimulationRuntimeControl.getExplorationRate();
        if (random.nextDouble() < explorationRate) {
            return filtered.get(random.nextInt(filtered.size()));
        }

        double[] probs = new double[filtered.size()];
        double sum = 0;
        double alpha = SimulationRuntimeControl.getAlpha();
        double beta  = SimulationRuntimeControl.getBeta();

        for (int i = 0; i < filtered.size(); i++) {
            Position neighbor = filtered.get(i);
            double pheromone = grid.getPheromone(neighbor);

            // Zones dangereuses (phéromone négative) traitées comme neutres
            if (pheromone < 0) {
                pheromone = 0.0;
            }

            double persistence = 1.0;
            if (lastDirection != null) {
                int dx = neighbor.x - position.x;
                int dy = neighbor.y - position.y;
                if (dx == lastDirection.x && dy == lastDirection.y) {
                    persistence = 3.0;
                }
            }

            double victimAttraction = victimAttraction(neighbor);
            double visitPenalty     = hasVisitedRecently(neighbor) ? config.getVisitedPathPenalty() : 1.0;
            double backtrackPenalty = neighbor.equals(previousPosition) ? config.getBacktrackPenalty() : 1.0;
            // ✅ Biais directionnel : pousse vers la zone préférée
            double directionalBias = computeDirectionalBias(neighbor);
            // ✅ Répulsion entre drones : évite les zones avec d'autres drones
            double repulsion = computeDroneRepulsion(neighbor);

            probs[i] = Math.pow(pheromone + 1.0, alpha)
                     * Math.pow(victimAttraction, beta)
                     * persistence
                     * visitPenalty
                     * backtrackPenalty
                     * directionalBias
                     * repulsion;
            sum += probs[i];
        }

        double r = random.nextDouble() * sum;
        double cumulative = 0;
        for (int i = 0; i < probs.length; i++) {
            cumulative += probs[i];
            if (r <= cumulative) {
                return filtered.get(i);
            }
        }
        return filtered.get(filtered.size() - 1);
    }

    // -------------------------------------------------------------------------
    // Biais directionnel et répulsion
    // -------------------------------------------------------------------------

    /**
     * Calcule une direction préférée pour ce drone,
     * afin que chaque drone explore une zone différente.
     * Les directions sont réparties uniformément autour de la base.
     */
    private Position computePreferredDirection() {
        int totalDrones = config.getDroneCount();
        // Répartir les drones en 8 directions principales (N, NE, E, SE, S, SO, O, NO)
        int[][] directions = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
        };
        // Chaque drone prend une direction selon son index
        int dirIndex = (droneId - 1) % directions.length;
        int dx = directions[dirIndex][0];
        int dy = directions[dirIndex][1];
        // Étendre la direction pour couvrir une grande zone
        int range = Math.max(config.getGridWidth(), config.getGridHeight()) / 2;
        return new Position(dx * range, dy * range);
    }

    /**
     * Calcule un facteur de répulsion basé sur la proximité des autres drones.
     * Plus il y a de drones proches, plus la répulsion est forte.
     */
    private double computeDroneRepulsion(Position neighbor) {
        double repulsion = 0.0;
        for (Position otherPos : grid.getDronePositions().values()) {
            int dist = Math.abs(neighbor.x - otherPos.x) + Math.abs(neighbor.y - otherPos.y);
            if (dist > 0 && dist <= REPULSION_RADIUS) {
                // Répulsion inversement proportionnelle à la distance
                repulsion += (REPULSION_RADIUS - dist + 1.0) / REPULSION_RADIUS;
            }
        }
        return 1.0 - (repulsion * DRONE_REPULSION_WEIGHT);
    }

    /**
     * Calcule l'attraction vers la direction préférée du drone.
     * Plus le voisin est dans la direction préférée, plus l'attraction est forte.
     */
    private double computeDirectionalBias(Position neighbor) {
        if (preferredDirection == null) return 1.0;
        int dx = neighbor.x - grid.getNestPosition().x;
        int dy = neighbor.y - grid.getNestPosition().y;
        // Produit scalaire entre la direction du voisin et la direction préférée
        double dot = dx * preferredDirection.x + dy * preferredDirection.y;
        if (dot <= 0) return 0.5; // Direction opposée : pénalisé
        // Normaliser
        double norm = Math.sqrt(dx*dx + dy*dy) * Math.sqrt(preferredDirection.x*preferredDirection.x + preferredDirection.y*preferredDirection.y);
        if (norm == 0) return 1.0;
        double cosAngle = dot / norm;
        return 1.0 + Math.max(0, cosAngle) * DIRECTIONAL_BIAS_STRENGTH;
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private double victimAttraction(Position neighbor) {
        double maxAttraction = 0;
        for (Position victim : grid.getVictimPositions()) {
            // Ignorer les victimes déjà trouvées
            if (grid.isVictimAlreadyFound(victim)) {
                continue;
            }
            if (neighbor.equals(victim)) {
                return 100.0;
            }
            int dist = Math.abs(neighbor.x - victim.x) + Math.abs(neighbor.y - victim.y);
            double attraction = 1.0 + (50.0 / (dist + 1));
            if (attraction > maxAttraction) {
                maxAttraction = attraction;
            }
        }
        return maxAttraction;
    }

    private boolean isVictimNearby() {
        for (Position victim : grid.getVictimPositions()) {
            if (position.equals(victim)) {
                return true;
            }
            int dist = Math.abs(position.x - victim.x) + Math.abs(position.y - victim.y);
            if (dist <= config.getPerceptionRadius()) {
                return true;
            }
        }
        return false;
    }

    private Position getNearestVictim() {
        Position nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (Position victim : grid.getVictimPositions()) {
            int dist = Math.abs(position.x - victim.x) + Math.abs(position.y - victim.y);
            if (dist < minDist) {
                minDist = dist;
                nearest = victim;
            }
        }
        return nearest;
    }

    private boolean hasVisitedRecently(Position candidate) {
        int start = Math.max(0, path.size() - 12);
        for (int i = start; i < path.size(); i++) {
            if (path.get(i).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void depositPheromones() {
        double amount = 100.0 / (1.0 + totalDistance);
        for (Position pos : path) {
            grid.addPheromone(pos, amount);
        }
        // Déposer des phéromones négatives sur les victimes déjà trouvées
        // pour éviter que les drones retournent vers elles
        for (Position victim : grid.getVictimPositions()) {
            if (grid.isVictimAlreadyFound(victim)) {
                grid.addNegativePheromone(victim, 2.0);
            }
        }
        log.info("Drone #{} a déposé des phéromones. Distance: {}",
                droneId, String.format("%.2f", totalDistance));
    }

    private List<Position> simplifyPath(List<Position> rawPath) {
        List<Position> simple = new ArrayList<>();
        for (Position cell : rawPath) {
            int idx = simple.indexOf(cell);
            if (idx >= 0) {
                simple.subList(idx, simple.size()).clear();
            }
            simple.add(new Position(cell.x, cell.y));
        }
        return simple;
    }

    private double computePathDistance(List<Position> p) {
        double dist = 0;
        for (int i = 1; i < p.size(); i++) {
            dist += p.get(i - 1).distanceTo(p.get(i));
        }
        return dist;
    }

    // -------------------------------------------------------------------------

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {}
        log.debug("Drone #{} terminé", droneId);
    }
}