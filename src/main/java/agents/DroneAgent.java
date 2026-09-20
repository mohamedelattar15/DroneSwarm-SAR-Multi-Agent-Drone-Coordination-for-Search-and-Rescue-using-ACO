package agents;

import agents.protocol.MessageProtocol;
import domain.aco.AntColonyOptimizer;
import domain.drone.DroneModel;
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
 * <p>
 * <b>Rôle :</b> infrastructure JADE (behaviours, messagerie) et orchestration du pas.
 * La logique métier est déléguée au domaine :
 * {@link AntColonyOptimizer} (sélection ACO) et {@link DroneModel} (état/batterie).
 */
public class DroneAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(DroneAgent.class);
    public static final String SERVICE_NAME = MessageProtocol.SERVICE_DRONE;

    /** Force du biais directionnel. */
    private static final double DIRECTIONAL_BIAS_STRENGTH = 2.0;
    /** Poids de la répulsion entre drones. */
    private static final double DRONE_REPULSION_WEIGHT = 0.3;
    /** Rayon de répulsion entre drones. */
    private static final int REPULSION_RADIUS = 8;
    /** Fenêtre d'anti-boucle (nombre de pas récents). */
    private static final int RECENT_WINDOW = 12;

    private static final AtomicInteger droneCount = new AtomicInteger(0);

    private Grid grid;
    private Statistics stats;
    private SimulationConfig config;
    private final AntColonyOptimizer aco = new AntColonyOptimizer();
    private DroneModel model;
    private final Random random = new Random();

    private Position position;
    private final List<Position> path = new ArrayList<>();
    private Position lastDirection;
    private Position preferredDirection;
    private double totalDistance;
    private int droneId;
    private AID environmentAID;
    private AID baseAID;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 3) {
            log.error("DroneAgent: arguments requis (grid, stats, config)");
            doDelete();
            return;
        }
        this.grid = (Grid) args[0];
        this.stats = (Statistics) args[1];
        this.config = (SimulationConfig) args[2];
        this.model = new DroneModel(config.getMaxDroneSteps());

        this.position = new Position(grid.getNestPosition().x, grid.getNestPosition().y);
        this.path.add(position);
        this.droneId = droneCount.incrementAndGet();
        this.preferredDirection = computePreferredDirection();

        registerWithDF();
        this.environmentAID = lookupAgent(MessageProtocol.SERVICE_ENVIRONMENT);
        this.baseAID = lookupAgent(MessageProtocol.SERVICE_BASE);

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
            if (result.length > 0) return result[0].getName();
        } catch (FIPAException e) {
            log.warn("Drone #{}: Recherche DF échouée {}", droneId, serviceName);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Behaviour : cycle de déplacement
    // -------------------------------------------------------------------------

    private class DroneMoveBehaviour extends TickerBehaviour {

        DroneMoveBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (SimulationRuntimeControl.isPaused()) return;

            model.tick();

            if (allVictimsFound()) {
                if (!model.getState().equals(DroneModel.State.IDLE)) {
                    log.info("Drone #{}: Toutes les victimes sont trouvées → arrêt", droneId);
                    model.onMissionComplete();
                }
                return;
            }

            if (model.shouldReturnForBattery()) {
                log.debug("Drone #{}: Batterie faible, retour à la base", droneId);
                model.onBatteryLow();
            }
            if (model.isLost()) {
                log.warn("Drone #{}: Perdu, réinitialisation", droneId);
                model.onLost();
            }
            if (model.isReturning() && position.equals(grid.getNestPosition())) {
                onArrivedAtBase();
                return;
            }

            model.onDeparture();

            // Détection avant et après déplacement (couvre les cases traversées)
            checkVictimDetection();
            moveDrone();
            checkVictimDetection();
        }
    }

    private void onArrivedAtBase() {
        if (model.getState().equals(DroneModel.State.RETURNING) && baseAID != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(baseAID);
            msg.setOntology(MessageProtocol.ONTOLOGY_DRONE_RETURNED);
            msg.setContent(MessageProtocol.VICTIM_RESCUED);
            send(msg);
        }
        path.clear();
        path.add(position);
        model.onArrivedAtBase();
        totalDistance = 0;
        lastDirection = null;
    }

    // -------------------------------------------------------------------------
    // Détection
    // -------------------------------------------------------------------------

    /**
     * Détection par rayon, active dans tous les états (y compris RETURNING).
     * Le retour à la base n'est pas interrompu : le drone continue de chercher.
     */
    private void checkVictimDetection() {
        Position victimPos = getNearestUndiscoveredVictim();
        if (victimPos == null) return;

        int dist = position.manhattanTo(victimPos);
        if (dist > config.getPerceptionRadius()) return;

        grid.addVictimPath(buildPathTo(victimPos));
        model.onVictimFound();
        log.info("Drone #{}: victime détectée à {} (distance {}) [état: {}]",
                droneId, victimPos, dist, model.getState());

        depositPheromones();

        List<Position> detectedPath = buildPathTo(victimPos);
        double detectedDistance = computePathDistance(detectedPath);
        Statistics.PathUpdate update = stats.recordAntPath(detectedPath, detectedDistance);

        if (environmentAID != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(environmentAID);
            msg.setConversationId(MessageProtocol.CONV_VICTIM_FOUND);
            msg.setContent(MessageProtocol.VICTIM_FOUND + ":" + update.getReinforcementFactor()
                    + ":" + update.getBestPathSignature() + ":" + victimPos.x + "," + victimPos.y);
            send(msg);
            log.info("[SEND] Drone #{} → Environment (victime à {}, renfort {}%)",
                    droneId, victimPos, Math.round(update.getReinforcementFactor() * 100));
        }

        notifyVictimAgent(victimPos);
    }

    /** Vrai si toutes les victimes de la grille ont été trouvées. */
    private boolean allVictimsFound() {
        return grid.getVictimsFound() >= grid.getVictimPositions().size();
    }

    /** Construit le chemin courant complété par la position de la victime. */
    private List<Position> buildPathTo(Position victimPos) {
        List<Position> detected = new ArrayList<>(path);
        if (detected.isEmpty() || !detected.get(detected.size() - 1).equals(victimPos)) {
            detected.add(new Position(victimPos.x, victimPos.y));
        }
        return detected;
    }

    // -------------------------------------------------------------------------
    // Déplacement
    // -------------------------------------------------------------------------

    private void moveDrone() {
        Position nextPos;
        if (model.isReturning()) {
            if (path.size() <= 1) return;
            nextPos = path.get(path.size() - 2);
            path.remove(path.size() - 1);
        } else {
            nextPos = chooseNextPosition();
        }
        if (nextPos == null) return;

        totalDistance += position.distanceTo(nextPos);
        lastDirection = new Position(nextPos.x - position.x, nextPos.y - position.y);
        position = nextPos;

        if (model.isExploring()) {
            path.add(new Position(position.x, position.y));
        }
        grid.updateDronePosition(getLocalName(), position);
        grid.addNegativePheromone(position, 0.1);
    }

    private Position chooseNextPosition() {
        List<Position> neighbors = grid.getValidNeighbors(position);
        if (neighbors.isEmpty()) return null;

        Position previous = (path.size() > 1) ? path.get(path.size() - 2) : null;
        List<Position> otherDrones = new ArrayList<>(grid.getDronePositions().values());
        List<Position> undiscovered = undiscoveredVictims();

        AntColonyOptimizer.StepContext ctx = new AntColonyOptimizer.StepContext(
                position, lastDirection, previous, preferredDirection, otherDrones,
                path, undiscovered,
                SimulationRuntimeControl.getAlpha(),
                SimulationRuntimeControl.getBeta(),
                SimulationRuntimeControl.getExplorationRate(),
                config.getVisitedPathPenalty(),
                config.getBacktrackPenalty(),
                DIRECTIONAL_BIAS_STRENGTH,
                DRONE_REPULSION_WEIGHT,
                REPULSION_RADIUS,
                RECENT_WINDOW);

        return aco.selectNext(neighbors, grid.getPheromones(), ctx);
    }

    private List<Position> undiscoveredVictims() {
        List<Position> result = new ArrayList<>();
        for (Position victim : grid.getVictimPositions()) {
            if (!grid.isVictimAlreadyFound(victim)) result.add(victim);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Écoute des retours de l'environnement
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
            if (content == null) return;

            if (content.startsWith(MessageProtocol.PATH_ACCEPTED)) {
                log.debug("[RECV] Drone #{} chemin accepté", droneId);
            } else if (content.startsWith(MessageProtocol.PATH_REJECTED)) {
                model.boostExploration(config.getExplorationBoostTicks());
                log.debug("[RECV] Drone #{} chemin rejeté, exploration boostée", droneId);
            } else if (content.startsWith(MessageProtocol.DIVERSIFY)) {
                model.boostExploration(config.getDiversificationBoostTicks());
                log.debug("[RECV] Drone #{} diversification", droneId);
            } else if (content.startsWith(MessageProtocol.VICTIM_CONFIRMED)) {
                log.info("[RECV] Drone #{} : victime confirmée par {} ({})",
                        droneId, msg.getSender().getLocalName(), content);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Messagerie victime
    // -------------------------------------------------------------------------

    /** Envoie VICTIM_DETECTED à l'agent VictimAgent le plus proche. */
    private void notifyVictimAgent(Position victimPos) {
        if (victimPos == null) return;
        AID victimAID = lookupNearestVictimAgent(victimPos);
        if (victimAID == null) {
            log.debug("Drone #{}: aucun VictimAgent trouvé pour {}", droneId, victimPos);
            return;
        }
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(victimAID);
        msg.setOntology(MessageProtocol.ONTOLOGY_VICTIM_DETECTED);
        msg.setContent(MessageProtocol.VICTIM_DETECTED + ":" + victimPos.x + "," + victimPos.y);
        send(msg);
        log.info("[SEND] Drone #{} → {} (détection en {})",
                droneId, victimAID.getLocalName(), victimPos);
    }

    /** Recherche l'agent victime le plus proche (position encodée dans son nom). */
    private AID lookupNearestVictimAgent(Position victimPos) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(MessageProtocol.SERVICE_VICTIM);
        template.addServices(sd);
        try {
            DFAgentDescription[] results = DFService.search(this, template);
            AID nearest = null;
            int bestDist = Integer.MAX_VALUE;
            for (DFAgentDescription dfd : results) {
                Position p = parseVictimPosition(dfd.getName().getLocalName());
                if (p == null) continue;
                int dist = p.manhattanTo(victimPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = dfd.getName();
                }
            }
            return nearest;
        } catch (FIPAException e) {
            log.warn("Drone #{}: recherche VictimAgent échouée", droneId);
            return null;
        }
    }

    private Position parseVictimPosition(String localName) {
        if (localName == null || !localName.startsWith(MessageProtocol.PREFIX_VICTIM)) return null;
        String[] parts = localName.split("_");
        if (parts.length < 3) return null;
        try {
            return new Position(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    /** Direction préférée répartie uniformément autour de la base. */
    private Position computePreferredDirection() {
        int[][] directions = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
        };
        int dirIndex = (droneId - 1) % directions.length;
        int range = Math.max(config.getGridWidth(), config.getGridHeight()) / 2;
        return new Position(directions[dirIndex][0] * range, directions[dirIndex][1] * range);
    }

    /** Victime non découverte la plus proche dans le rayon de perception. */
    private Position getNearestUndiscoveredVictim() {
        Position nearest = null;
        int minDist = Integer.MAX_VALUE;
        int radius = config.getPerceptionRadius();
        for (Position victim : grid.getVictimPositions()) {
            if (grid.isVictimAlreadyFound(victim)) continue;
            int dist = position.manhattanTo(victim);
            if (dist <= radius && dist < minDist) {
                minDist = dist;
                nearest = victim;
            }
        }
        return nearest;
    }

    private void depositPheromones() {
        double amount = 100.0 / (1.0 + totalDistance);
        for (Position pos : path) {
            grid.addPheromone(pos, amount);
        }
        for (Position victim : grid.getVictimPositions()) {
            if (grid.isVictimAlreadyFound(victim)) {
                grid.addNegativePheromone(victim, 2.0);
            }
        }
        log.info("Drone #{} a déposé des phéromones. Distance: {}",
                droneId, String.format("%.2f", totalDistance));
    }

    private double computePathDistance(List<Position> p) {
        double dist = 0;
        for (int i = 1; i < p.size(); i++) {
            dist += p.get(i - 1).distanceTo(p.get(i));
        }
        return dist;
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {
            // rien à faire
        }
        log.debug("Drone #{} terminé", droneId);
    }
}
