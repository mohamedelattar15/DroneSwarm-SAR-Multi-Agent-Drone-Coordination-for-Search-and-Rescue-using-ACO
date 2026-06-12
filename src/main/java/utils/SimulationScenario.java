package utils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * Scénarios de simulation pré-configurés pour différents cas d'usage.
 * Permet de lancer une simulation avec des paramètres adaptés à un contexte spécifique.
 */
public class SimulationScenario implements Serializable {

    private final String name;
    private final String description;
    private final SimulationConfig config;

    private SimulationScenario(String name, String description, SimulationConfig config) {
        this.name = name;
        this.description = description;
        this.config = config;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public SimulationConfig getConfig() { return config; }

    @Override
    public String toString() {
        return name + " - " + description;
    }

    // ========== Scénarios prédéfinis ==========

    /** Scénario de base : petite zone, peu d'obstacles, démonstration rapide */
    public static SimulationScenario demo() {
        SimulationConfig c = SimulationConfig.defaults();
        c.setDroneCount(5);
        c.setVictimCount(2);
        c.setObstacleCount(10);
        c.setGridWidth(30);
        c.setGridHeight(30);
        return new SimulationScenario("Démo", "Petite zone, 5 drones, 2 victimes, démonstration rapide", c);
    }

    /** Scénario standard : zone moyenne, équilibre exploration/exploitation */
    public static SimulationScenario standard() {
        SimulationConfig c = SimulationConfig.defaults();
        c.setDroneCount(10);
        c.setVictimCount(5);
        c.setObstacleCount(30);
        c.setGridWidth(60);
        c.setGridHeight(60);
        c.setAlpha(1.0);
        c.setBeta(1.5);
        c.setRandomExploration(0.20);
        return new SimulationScenario("Standard", "Zone 60x60, 10 drones, 5 victimes, 30 obstacles", c);
    }

    /** Scénario urbain : beaucoup d'obstacles, exploration difficile */
    public static SimulationScenario urban() {
        SimulationConfig c = SimulationConfig.defaults();
        c.setDroneCount(20);
        c.setVictimCount(8);
        c.setObstacleCount(80);
        c.setGridWidth(80);
        c.setGridHeight(80);
        c.setAlpha(1.5);      // Plus de poids aux phéromones
        c.setBeta(2.0);       // Plus de poids à l'heuristique
        c.setRandomExploration(0.30); // Plus d'exploration
        c.setPerceptionRadius(5);
        c.setStagnationThreshold(200); // Diversification plus rapide
        return new SimulationScenario("Urbain", "Zone dense 80x80, 20 drones, 8 victimes, 80 obstacles", c);
    }

    /** Scénario catastrophe : grande zone, peu de drones, priorité à la couverture */
    public static SimulationScenario disaster() {
        SimulationConfig c = SimulationConfig.defaults();
        c.setDroneCount(8);
        c.setVictimCount(12);
        c.setObstacleCount(50);
        c.setGridWidth(100);
        c.setGridHeight(100);
        c.setAlpha(0.5);      // Moins de poids aux phéromones
        c.setBeta(2.5);       // Plus de poids à l'heuristique (guidage)
        c.setRandomExploration(0.40); // Beaucoup d'exploration
        c.setPerceptionRadius(6);
        c.setMaxDroneSteps(5000); // Autonomie étendue
        c.setStagnationThreshold(150); // Diversification rapide
        return new SimulationScenario("Catastrophe", "Grande zone 100x100, 8 drones, 12 victimes, couverture prioritaire", c);
    }

    /** Scénario nocturne : perception réduite, exploration lente */
    public static SimulationScenario nocturnal() {
        SimulationConfig c = SimulationConfig.defaults();
        c.setDroneCount(15);
        c.setVictimCount(6);
        c.setObstacleCount(40);
        c.setGridWidth(60);
        c.setGridHeight(60);
        c.setPerceptionRadius(2);  // Perception réduite
        c.setRandomExploration(0.35);
        c.setDroneStepIntervalMs(300); // Vol plus lent
        c.setTickTime(300L);
        return new SimulationScenario("Nocturne", "Perception réduite (rayon 2), vol lent, 15 drones", c);
    }

    /** Tous les scénarios disponibles */
    public static List<SimulationScenario> getAll() {
        return Arrays.asList(demo(), standard(), urban(), disaster(), nocturnal());
    }

    /** Trouver un scénario par nom */
    public static SimulationScenario findByName(String name) {
        for (SimulationScenario s : getAll()) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return standard();
    }
}
