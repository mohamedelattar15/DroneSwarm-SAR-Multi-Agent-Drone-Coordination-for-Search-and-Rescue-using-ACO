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
    private static int droneCount = 0;
    private int droneId;
    private int explorationBoostTicks;
    private int stepsSinceLastFind;
    private int stepsSinceLastReturn;
    private AID environmentAID;
    private final Random random = new Random();

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

        this.position = new Position(grid.getNestPosition().x, grid.getNestPosition().y);
        this.path.add(position);
        this.droneId = ++droneCount;

        registerWithDF();
        this.environmentAID = lookupAgent(EnvironmentAgent.SERVICE_NAME);

        grid.updateDronePosition(getLocalName(), position);
        log.debug("Drone #{} prêt à la base {}", droneId, position);

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
        try { DFService.register(this, dfd); } catch (FIPAException e) {
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

    private class DroneMoveBehaviour extends TickerBehaviour {
        public DroneMoveBehaviour(Agent a, long period) { super(a, period); }

        @Override
        protected void onTick() {
            if (SimulationRuntimeControl.isPaused()) return;
            if (explorationBoostTicks > 0) explorationBoostTicks--;

            stepsSinceLastFind++;
            stepsSinceLastReturn++;

            // Timeout d'exploration : retour à la base (batterie faible)
            if (state == DroneState.EXPLORING && stepsSinceLastFind > config.getMaxDroneSteps()) {
                log.debug("Drone #{}: Batterie faible, retour à la base", droneId);
                state = DroneState.RETURNING_EMPTY;
                stepsSinceLastFind = 0;
            }

            // Timeout de retour : sécurité
            if ((state == DroneState.RETURNING || state == DroneState.RETURNING_EMPTY)
                    && stepsSinceLastReturn > config.getMaxDroneSteps()) {
                log.warn("Drone #{}: Perdu, réinitialisation", droneId);
                state = DroneState.IDLE;
                stepsSinceLastReturn = 0;
            }

            // Arrivée à la base
            if ((state == DroneState.RETURNING || state == DroneState.RETURNING_EMPTY)
                    && position.equals(grid.getNestPosition())) {
                // Notifier la base du retour
                if (hasFoundVictim) {
                    notifyBaseReturn();
                }
                path.clear();
                path.add(position);
                hasFoundVictim = false;
                hasRecruited = false;
                state = DroneState.IDLE;
                totalDistance = 0;
                lastDirection = null;
                stepsSinceLastFind = 0;
                stepsSinceLastReturn = 0;
                return;
            }

            // Fin de l'IDLE → nouveau départ
            if (state == DroneState.IDLE) {
                state = DroneState.EXPLORING;
            }

            moveDrone();

            // Vérifier si on a trouvé une victime
            if (!hasFoundVictim && isVictimNearby()) {
                hasFoundVictim = true;
                state = DroneState.RETURNING;
                stepsSinceLastFind = 0;

                // Simplifier le chemin et déposer les phéromones
                List<Position> cleanedPath = simplifyPath(path);
                path.clear();
                path.addAll(cleanedPath);
                totalDistance = computePathDistance(cleanedPath);
                depositPheromones();

                // Enregistrer et notifier
                Statistics.PathUpdate update = stats.recordAntPath(new ArrayList<>(path), totalDistance);
                double reinforceFactor = update.getReinforcementFactor();

                if (!hasRecruited && environmentAID != null) {
                    hasRecruited = true;
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(environmentAID);
                    msg.setConversationId("drone-found-victim");
                    msg.setContent("VICTIM_FOUND:" + reinforceFactor + ":" + update.getBestPathSignature());
                    log.info("[SEND] Drone #{} → Environment (victime trouvée, renfort {}%)",
                            droneId, Math.round(reinforceFactor * 100));
                    send(msg);
                }
            }
        }
    }

    private class EnvironmentFeedbackListener extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) { block(); return; }
            String content = msg.getContent();
            if (content == null) return;

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

    private void moveDrone() {
        Position nextPos;
        if (state == DroneState.RETURNING || state == DroneState.RETURNING_EMPTY) {
            if (path.size() > 1) {
                nextPos = path.get(path.size() - 2);
                path.remove(path.size() - 1);
            } else return;
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
        }
    }

    private Position chooseNextPosition() {
        List<Position> neighbors = grid.getValidNeighbors(position);
        if (neighbors.isEmpty()) return null;

        Position previousPosition = path.size() > 1 ? path.get(path.size() - 2) : null;
        List<Position> filtered = new ArrayList<>(neighbors);
        if (previousPosition != null && filtered.size() > 1) {
            filtered.removeIf(n -> n.equals(previousPosition));
        }
        if (filtered.isEmpty()) filtered = neighbors;

        double explorationRate = SimulationRuntimeControl.getExplorationRate();
        if (random.nextDouble() < explorationRate) {
            return filtered.get(random.nextInt(filtered.size()));
        }

        double[] probs = new double[filtered.size()];
        double sum = 0;
        double alpha = SimulationRuntimeControl.getAlpha();
        double beta = SimulationRuntimeControl.getBeta();

        for (int i = 0; i < filtered.size(); i++) {
            Position neighbor = filtered.get(i);
            double pheromone = grid.getPheromone(neighbor);

            // Éviter les phéromones négatives (zones dangereuses)
            if (pheromone < 0) pheromone = -pheromone * 0.3;

            double persistence = 1.0;
            if (lastDirection != null) {
                int dx = neighbor.x - position.x;
                int dy = neighbor.y - position.y;
                if (dx == lastDirection.x && dy == lastDirection.y) persistence = 3.0;
            }

            double victimAttraction = victimAttraction(neighbor);
            double visitPenalty = hasVisitedRecently(neighbor) ? config.getVisitedPathPenalty() : 1.0;
            double backtrackPenalty = neighbor.equals(previousPosition) ? config.getBacktrackPenalty() : 1.0;

            probs[i] = Math.pow(pheromone + 1.0, alpha) * Math.pow(victimAttraction, beta)
                    * persistence * visitPenalty * backtrackPenalty;
            sum += probs[i];
        }

        double r = random.nextDouble() * sum;
        double cumulative = 0;
        for (int i = 0; i < probs.length; i++) {
            cumulative += probs[i];
            if (r <= cumulative) return filtered.get(i);
        }
        return filtered.get(filtered.size() - 1);
    }

    private double victimAttraction(Position neighbor) {
        double maxAttraction = 0;
        for (Position victim : grid.getVictimPositions()) {
            if (neighbor.equals(victim)) return 100.0;
            int dist = Math.abs(neighbor.x - victim.x) + Math.abs(neighbor.y - victim.y);
            double attraction = 1.0 + (50.0 / (dist + 1));
            if (attraction > maxAttraction) maxAttraction = attraction;
        }
        return maxAttraction;
    }

    private boolean isVictimNearby() {
        for (Position victim : grid.getVictimPositions()) {
            if (position.equals(victim)) return true;
            int dist = Math.abs(position.x - victim.x) + Math.abs(position.y - victim.y);
            if (dist <= config.getPerceptionRadius()) return true;
        }
        return false;
    }

    private boolean hasVisitedRecently(Position candidate) {
        int start = Math.max(0, path.size() - 12);
        for (int i = start; i < path.size(); i++) {
            if (path.get(i).equals(candidate)) return true;
        }
        return false;
    }

    private void depositPheromones() {
        double amount = 100.0 / (1.0 + totalDistance);
        for (Position pos : path) {
            grid.addPheromone(pos, amount);
        }
        log.info("Drone #{} a déposé des phéromones. Distance: {}", droneId, String.format("%.2f", totalDistance));
    }

    private List<Position> simplifyPath(List<Position> rawPath) {
        List<Position> simple = new ArrayList<>();
        for (Position cell : rawPath) {
            int idx = simple.indexOf(cell);
            if (idx >= 0) simple.subList(idx, simple.size()).clear();
            simple.add(new Position(cell.x, cell.y));
        }
        return simple;
    }

    private double computePathDistance(List<Position> p) {
        double dist = 0;
        for (int i = 1; i < p.size(); i++) dist += p.get(i - 1).distanceTo(p.get(i));
        return dist;
    }

    /** Notifier la base de secours du retour avec ou sans victime */
    private void notifyBaseReturn() {
        AID baseAID = lookupAgent(agents.BaseAgent.SERVICE_NAME);
        if (baseAID == null) return;
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(baseAID);
        msg.setOntology(agents.BaseAgent.ONTOLOGY_DRONE_RETURNED);
        msg.setContent(hasFoundVictim ? "VICTIM_RESCUED" : "RETURN_EMPTY");
        send(msg);
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        log.debug("Drone #{} terminé", droneId);
    }
}
