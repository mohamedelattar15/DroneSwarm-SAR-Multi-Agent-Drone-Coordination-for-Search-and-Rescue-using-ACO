package agents;

import environment.Grid;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.MetricsExporter;
import utils.SimulationConfig;
import utils.SimulationRuntimeControl;
import utils.Statistics;

/**
 * Agent Environnement : gère la grille, les phéromones, les obstacles et la validation.
 */
public class EnvironmentAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentAgent.class);
    public static final String SERVICE_NAME = "EnvironmentService";

    private Grid grid;
    private SimulationFrame simFrame;
    private Statistics stats;
    private SimulationConfig config;
    private int iteration = 0;
    private int lastReinforcementIteration = -80;
    private MetricsExporter metricsExporter;

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
        try { DFService.register(this, dfd); log.info("EnvironmentAgent enregistré DF"); }
        catch (FIPAException e) { log.error("Échec DF", e); }
    }

    private void createDrones() {
        for (int i = 0; i < config.getDroneCount(); i++) {
            try {
                getContainerController().createNewAgent(
                    "Drone_" + i, "agents.DroneAgent",
                    new Object[]{grid, stats, config}
                ).start();
            } catch (Exception e) {
                log.error("Erreur création drone {}", i, e);
            }
        }
        log.info("{} drones créés", config.getDroneCount());
    }

    /**
     * Crée un VictimAgent par victime, nommé "Victim_<x>_<y>" afin que les drones
     * puissent retrouver l'agent correspondant à une position via le DF.
     */
    private void createVictims() {
        int index = 0;
        for (environment.Position p : grid.getVictimPositions()) {
            try {
                getContainerController().createNewAgent(
                    "Victim_" + p.x + "_" + p.y, "agents.VictimAgent", null
                ).start();
                index++;
            } catch (Exception e) {
                log.error("Erreur création victime {}", p, e);
            }
        }
        log.info("{} agents victimes créés", index);
    }

    private int lastVictimCount = 0;

    private class RecruitmentListener extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) { block(); return; }
            String content = msg.getContent();
            if (content == null || !content.startsWith("VICTIM_FOUND")) return;

            double factor = 0.3;
            String pathSignature = null;
            String[] parts = content.split(":");
            if (parts.length >= 2) {
                try { factor = Double.parseDouble(parts[1]); } catch (NumberFormatException ignored) {}
            }
            if (parts.length >= 3) pathSignature = parts[2];

            if (iteration - lastReinforcementIteration < config.getRecruitmentCooldown()) {
                sendFeedback(msg.getSender(), "PATH_REJECTED:COOLDOWN");
                return;
            }

            if (stats.getBestPath() != null) {
                // Lire le nombre de victimes uniques AVANT toute modification
                int uniqueBefore = stats.getUniqueVictimsFound();

                // ✅ Validation tolérante : signature exacte OU similarité >= seuil
                boolean accepted = stats.matchesCurrentBest(pathSignature)
                        || stats.isSimilarToCurrentBest(stats.getBestPath(),
                                config.getPathSimilarityThreshold());

                if (accepted) {
                    lastReinforcementIteration = iteration;
                    log.info("[VALIDATION] Victime confirmée! Renfort x{}% (confirmations: {}/{})",
                            Math.round(factor * 100), stats.getConfirmationCount(), Statistics.FULL_THRESHOLD);
                    grid.applyEliteReinforcement(stats.getBestPath(), factor);
                    grid.setBestPath(stats.getBestPath());
                    grid.setVictimPaths(stats.getAllVictimPaths());

                    if (simFrame != null) {
                        simFrame.onBestPathFound(stats.getBestPath().size() - 1);
                    }
                    sendFeedback(msg.getSender(), "PATH_ACCEPTED:" + factor + ":" + stats.getConfirmationCount());
                } else {
                    // Chemin rejeté mais on met à jour l'affichage
                    grid.setBestPath(stats.getBestPath());
                    grid.setVictimPaths(stats.getAllVictimPaths());

                    if (simFrame != null) {
                        simFrame.onBestPathFound(stats.getBestPath().size() - 1);
                    }
                    sendFeedback(msg.getSender(), "PATH_REJECTED:SIGNATURE");
                }
            }
        }
    }

    /**
     * ✅ Aligne l'affichage des victimes sur la source unique (Grid).
     * Appelée à chaque tick, elle rattrape toute victime détectée pendant un
     * cooldown ou en l'absence de bestPath, ce qui évitait l'affichage "1/5".
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

    private class EvaporationBehaviour extends TickerBehaviour {
        public EvaporationBehaviour(Agent a, long period) { super(a, period); }

        @Override
        protected void onTick() {
            if (SimulationRuntimeControl.isPaused()) return;
            stats.tick();
            grid.evaporateDifferentiated(stats.getBestPath(), stats.getConfirmationCount());

            if (stats.getBestPath() != null && stats.getConfirmationCount() >= Statistics.MEDIUM_THRESHOLD
                    && iteration % 20 == 0) {
                double factor = stats.getConfirmationCount() >= Statistics.FULL_THRESHOLD ? 0.5 : 0.25;
                grid.applyEliteReinforcement(stats.getBestPath(), factor);
            }

            if (stats.isStagnating()) {
                log.warn("[STAGNATION] Itération {} → diversification", iteration);
                grid.partialReset(stats.getBestPath());
                stats.resetStagnation();
                broadcastToDrones("DIVERSIFY");
            }

            // ✅ Synchronisation de l'UI sur la source unique (Grid), indépendamment
            // du flux de validation/cooldown : évite que l'affichage reste bloqué.
            syncVictimDisplay();

            iteration++;
            // Export CSV toutes les 10 itérations
            if (iteration % 10 == 0) {
                metricsExporter.recordIteration(
                    stats.getShortestPath(),
                    stats.getConfirmationCount(),
                    iteration,
                    stats.getIterationsSinceImprovement(),
                    config.getDroneCount(),
                    stats.getUniqueVictimsFound()
                );
            }

            if (iteration % 50 == 0) {
                log.info("--- Rapport Itération {} ---", iteration);
                stats.printStats();
            }
        }
    }

    private void sendFeedback(AID receiver, String content) {
        ACLMessage fb = new ACLMessage(ACLMessage.INFORM);
        fb.addReceiver(receiver);
        fb.setConversationId("drone-feedback");
        fb.setContent(content);
        send(fb);
    }

    private void broadcastToDrones(String content) {
        ACLMessage broadcast = new ACLMessage(ACLMessage.INFORM);
        for (int i = 0; i < config.getDroneCount(); i++) {
            broadcast.addReceiver(new AID("Drone_" + i, AID.ISLOCALNAME));
        }
        broadcast.setConversationId("drone-broadcast");
        broadcast.setContent(content);
        send(broadcast);
    }

    @Override
    protected void takeDown() {
        // Exporter le meilleur chemin à la fin
        if (stats.getBestPath() != null) {
            metricsExporter.recordBestPath(stats.getBestPath(), stats.getShortestPath());
        }
        metricsExporter.close();

        if (simFrame != null) javax.swing.SwingUtilities.invokeLater(() -> simFrame.shutdown());
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        log.info("EnvironmentAgent terminé");
    }
}
