package agents;

import agents.protocol.MessageProtocol;
import domain.victim.VictimRegistry;
import environment.Grid;
import environment.Position;
import environment.SimulationFrame;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.MetricsExporter;
import utils.SimulationConfig;
import utils.SimulationRuntimeControl;
import utils.Statistics;

/**
 * Agent Environnement : orchestre la grille, l'évaporation et la validation collective.
 * <p>
 * <b>Rôle :</b> infrastructure JADE (behaviours, messagerie) et orchestration.
 * La logique métier est déléguée au domaine ({@link domain.pheromone.PheromoneField},
 * {@link VictimRegistry}).
 */
public class EnvironmentAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentAgent.class);
    public static final String SERVICE_NAME = MessageProtocol.SERVICE_ENVIRONMENT;

    private Grid grid;
    private SimulationFrame simFrame;
    private Statistics stats;
    private SimulationConfig config;
    private int iteration = 0;
    private int lastReinforcementIteration = -80;
    private MetricsExporter metricsExporter;
    private int lastVictimCount = 0;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        this.config = (args != null && args.length > 0 && args[0] instanceof SimulationConfig)
                ? (SimulationConfig) args[0] : SimulationConfig.defaults();

        this.grid = new Grid(config);
        this.stats = new Statistics();
        this.stats.setStagnationThreshold(config.getStagnationThreshold());
        SimulationRuntimeControl.initialize(config);
        registerWithDF();

        log.info("=== Environnement Créé ===");
        log.info("Base: {} | Victimes: {} | Obstacles: {} | Grille: {}x{}",
                grid.getNestPosition(), config.getVictimCount(), config.getObstacleCount(),
                config.getGridWidth(), config.getGridHeight());

        createDrones();
        createVictims();
        javax.swing.SwingUtilities.invokeLater(() -> simFrame = new SimulationFrame(grid, config));

        this.metricsExporter = new MetricsExporter();

        addBehaviour(new EvaporationBehaviour(this, config.getTickTime() * 2));
        addBehaviour(new RecruitmentListener());
    }

    private void registerWithDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_NAME);
        sd.setName(SERVICE_NAME);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            log.info("EnvironmentAgent enregistré DF");
        } catch (FIPAException e) {
            log.error("Échec DF", e);
        }
    }

    private void createDrones() {
        for (int i = 0; i < config.getDroneCount(); i++) {
            try {
                getContainerController().createNewAgent(
                        MessageProtocol.PREFIX_DRONE + i, "agents.DroneAgent",
                        new Object[]{grid, stats, config}).start();
            } catch (Exception e) {
                log.error("Erreur création drone {}", i, e);
            }
        }
        log.info("{} drones créés", config.getDroneCount());
    }

    /**
     * Crée un VictimAgent par victime, nommé "Victim_&lt;x&gt;_&lt;y&gt;" afin que les drones
     * puissent retrouver l'agent correspondant à une position via le DF.
     */
    private void createVictims() {
        int index = 0;
        for (Position p : grid.getVictimPositions()) {
            try {
                getContainerController().createNewAgent(
                        MessageProtocol.victimName(p.x, p.y), "agents.VictimAgent", null).start();
                index++;
            } catch (Exception e) {
                log.error("Erreur création victime {}", p, e);
            }
        }
        log.info("{} agents victimes créés", index);
    }

    // -------------------------------------------------------------------------
    // Behaviour : validation collective des détections
    // -------------------------------------------------------------------------

    private class RecruitmentListener extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) {
                block();
                return;
            }
            String content = msg.getContent();
            if (content == null || !content.startsWith(MessageProtocol.VICTIM_FOUND)) return;

            String[] parts = content.split(":");
            double factor = parseFactor(parts);
            String pathSignature = (parts.length >= 3) ? parts[2] : null;

            if (iteration - lastReinforcementIteration < config.getRecruitmentCooldown()) {
                sendFeedback(msg.getSender(), MessageProtocol.PATH_REJECTED + ":COOLDOWN");
                return;
            }

            List<Position> bestPath = stats.getBestPath();
            if (bestPath == null) return;

            if (isAccepted(pathSignature, bestPath)) {
                lastReinforcementIteration = iteration;
                log.info("[VALIDATION] Victime confirmée! Renfort x{}% (confirmations: {}/{})",
                        Math.round(factor * 100), stats.getConfirmationCount(),
                        VictimRegistry.FULL_THRESHOLD);
                grid.applyEliteReinforcement(bestPath, factor);
                if (simFrame != null) simFrame.onBestPathFound(bestPath.size() - 1);
                sendFeedback(msg.getSender(), MessageProtocol.PATH_ACCEPTED + ":" + factor
                        + ":" + stats.getConfirmationCount());
            } else {
                if (simFrame != null) simFrame.onBestPathFound(bestPath.size() - 1);
                sendFeedback(msg.getSender(), MessageProtocol.PATH_REJECTED + ":SIGNATURE");
            }
        }

        private double parseFactor(String[] parts) {
            if (parts.length >= 2) {
                try {
                    return Double.parseDouble(parts[1]);
                } catch (NumberFormatException ignored) {
                    // valeur par défaut ci-dessous
                }
            }
            return 0.3;
        }

        /** Validation tolérante : signature exacte OU similarité suffisante. */
        private boolean isAccepted(String signature, List<Position> bestPath) {
            if (stats.matchesCurrentBest(signature)) return true;
            return stats.similarityToCurrentBest(bestPath) >= config.getPathSimilarityThreshold();
        }
    }

    // -------------------------------------------------------------------------
    // Behaviour : évaporation + stagnation + métriques
    // -------------------------------------------------------------------------

    private class EvaporationBehaviour extends TickerBehaviour {
        public EvaporationBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (SimulationRuntimeControl.isPaused()) return;

            stats.tick();
            grid.evaporateDifferentiated(stats.getBestPath(), stats.getConfirmationCount());

            reinforceEliteIfConfirmed();
            diversifyIfStagnating();
            syncVictimDisplay();

            iteration++;
            exportMetricsIfNeeded();
            printReportIfNeeded();
        }

        private void reinforceEliteIfConfirmed() {
            List<Position> best = stats.getBestPath();
            if (best != null && stats.getConfirmationCount() >= VictimRegistry.MEDIUM_THRESHOLD
                    && iteration % 20 == 0) {
                double factor = stats.getConfirmationCount() >= VictimRegistry.FULL_THRESHOLD
                        ? 0.5 : 0.25;
                grid.applyEliteReinforcement(best, factor);
            }
        }

        private void diversifyIfStagnating() {
            if (!stats.isStagnating()) return;
            log.warn("[STAGNATION] Itération {} → diversification", iteration);
            grid.partialReset(stats.getBestPath());
            stats.resetStagnation();
            broadcastToDrones(MessageProtocol.DIVERSIFY);
        }

        private void exportMetricsIfNeeded() {
            if (iteration % 10 != 0) return;
            metricsExporter.recordIteration(
                    stats.getShortestPath(),
                    stats.getConfirmationCount(),
                    iteration,
                    stats.getIterationsSinceImprovement(),
                    config.getDroneCount(),
                    stats.getUniqueVictimsFound());
        }

        private void printReportIfNeeded() {
            if (iteration % 50 != 0) return;
            log.info("--- Rapport Itération {} ---", iteration);
            stats.printStats();
        }
    }

    /**
     * Aligne l'affichage des victimes sur la source unique (Grid).
     * Appelée à chaque tick, elle rattrape toute victime détectée pendant un
     * cooldown ou en l'absence de bestPath.
     */
    private void syncVictimDisplay() {
        if (simFrame == null) return;
        int found = grid.getVictimsFound();
        if (found > lastVictimCount) {
            for (int i = lastVictimCount; i < found; i++) {
                simFrame.onVictimFound();
            }
            lastVictimCount = found;
        }
    }

    // -------------------------------------------------------------------------
    // Messagerie
    // -------------------------------------------------------------------------

    private void sendFeedback(AID receiver, String content) {
        ACLMessage fb = new ACLMessage(ACLMessage.INFORM);
        fb.addReceiver(receiver);
        fb.setConversationId(MessageProtocol.CONV_DRONE_FEEDBACK);
        fb.setContent(content);
        send(fb);
    }

    private void broadcastToDrones(String content) {
        ACLMessage broadcast = new ACLMessage(ACLMessage.INFORM);
        for (int i = 0; i < config.getDroneCount(); i++) {
            broadcast.addReceiver(new AID(MessageProtocol.PREFIX_DRONE + i, AID.ISLOCALNAME));
        }
        broadcast.setConversationId(MessageProtocol.CONV_DRONE_BROADCAST);
        broadcast.setContent(content);
        send(broadcast);
    }

    @Override
    protected void takeDown() {
        if (stats.getBestPath() != null) {
            metricsExporter.recordBestPath(stats.getBestPath(), stats.getShortestPath());
        }
        metricsExporter.close();
        if (simFrame != null) javax.swing.SwingUtilities.invokeLater(() -> simFrame.shutdown());
        try {
            DFService.deregister(this);
        } catch (FIPAException ignored) {
            // rien à faire
        }
        log.info("EnvironmentAgent terminé");
    }
}
