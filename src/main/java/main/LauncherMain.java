package main;

import agents.protocol.MessageProtocol;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.SimulationConfig;
import utils.SimulationScenario;

/**
 * Point d'entrée de la simulation DroneSwarm-SAR.
 * Lance la plateforme JADE et crée les agents.
 */
public class LauncherMain {

    private static final Logger log = LoggerFactory.getLogger(LauncherMain.class);

    public static void main(String[] args) {
        try {
            boolean enableSniffer = shouldEnableSniffer(args);
            SimulationConfig config = resolveConfig(args);

            log.info("========================================");
            log.info("  🚁 DroneSwarm-SAR");
            log.info("  Search and Rescue using ACO");
            log.info("========================================");
            log.info("Configuration: {}", config);

            Runtime rt = Runtime.instance();
            rt.setCloseVM(true);

            Profile p = new ProfileImpl();
            p.setParameter("gui", "true");

            ContainerController mainContainer = rt.createMainContainer(p);

            log.info("Création des agents...");

            // 1. EnvironmentAgent (grille, obstacles, phéromones)
            mainContainer.createNewAgent(
                MessageProtocol.AGENT_ENVIRONMENT, "agents.EnvironmentAgent", new Object[]{config}
            ).start();

            Thread.sleep(500);

            // 2. BaseAgent (logistique)
            mainContainer.createNewAgent(
                MessageProtocol.AGENT_BASE, "agents.BaseAgent", new Object[]{config}
            ).start();

            // 3. VictimAgents : créés par l'EnvironmentAgent (il connaît les positions)

            if (enableSniffer) {
                log.info("Lancement du Sniffer JADE...");
                mainContainer.createNewAgent(
                    MessageProtocol.AGENT_SNIFFER, "jade.tools.sniffer.Sniffer",
                    new Object[]{"Environment;Base;Drone*;Victim*"}
                ).start();
            }

            log.info("🚁 Simulation lancée! {} drones recherchent {} victimes.",
                    config.getDroneCount(), config.getVictimCount());

        } catch (Exception e) {
            log.error("Erreur lors du lancement:", e);
        }
    }

    private static boolean shouldEnableSniffer(String[] args) {
        if (args == null) return true;
        for (String arg : args) {
            if ("--no-sniffer".equalsIgnoreCase(arg)) return false;
        }
        return true;
    }

    /** ✅ Permet de choisir un scénario via --scenario=<nom> (demo, standard, urban, disaster, nocturnal) */
    private static SimulationConfig resolveConfig(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith("--scenario=")) {
                    String name = arg.substring("--scenario=".length()).trim();
                    SimulationScenario scenario = SimulationScenario.findByName(name);
                    log.info("Scénario sélectionné: {}", scenario);
                    return scenario.getConfig();
                }
            }
        }
        return SimulationConfig.defaults();
    }
}
