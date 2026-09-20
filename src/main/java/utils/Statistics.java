package utils;

import domain.victim.VictimRegistry;
import environment.Position;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Statistiques de mission et validation collective.
 * <p>
 * <b>Façade</b> sur {@link VictimRegistry} : ce dernier est la source unique de
 * vérité pour les victimes. Cette classe ajoute le suivi de la stagnation et
 * l'exposition des métriques pour l'agent Environnement.
 */
public class Statistics {

    private static final Logger log = LoggerFactory.getLogger(Statistics.class);

    /** Seuils de validation collective (délégués au registre). */
    public static final int MEDIUM_THRESHOLD = VictimRegistry.MEDIUM_THRESHOLD;
    public static final int FULL_THRESHOLD = VictimRegistry.FULL_THRESHOLD;

    /** Résultat d'un enregistrement de détection (façade du registre). */
    public static class PathUpdate {
        private final VictimRegistry.DetectionResult delegate;

        PathUpdate(VictimRegistry.DetectionResult delegate) {
            this.delegate = delegate;
        }

        public double getReinforcementFactor() { return delegate.getReinforcementFactor(); }
        public int getConfirmationCount() { return delegate.getConfirmationCount(); }
        public boolean isNewVictim() { return delegate.isNewVictim(); }
        public boolean isImprovedPath() { return delegate.isImprovedPath(); }
        public String getBestPathSignature() { return delegate.getSignature(); }
    }

    private final VictimRegistry registry = new VictimRegistry();
    private int totalDiscoveries = 0;
    private int iterationsSinceImprovement = 0;
    private int stagnationThreshold = 300;

    // -------------------------------------------------------------------------
    // Enregistrement
    // -------------------------------------------------------------------------

    /** Enregistre une détection de victime et retourne le facteur de renfort. */
    public synchronized PathUpdate recordAntPath(List<Position> path, double distance) {
        totalDiscoveries++;
        VictimRegistry.DetectionResult result = registry.record(path, distance);
        if (result.isImprovedPath()) {
            iterationsSinceImprovement = 0;
        }
        return new PathUpdate(result);
    }

    // -------------------------------------------------------------------------
    // Stagnation
    // -------------------------------------------------------------------------

    public synchronized void tick() { iterationsSinceImprovement++; }

    public synchronized boolean isStagnating() {
        return getBestPath() != null && iterationsSinceImprovement >= stagnationThreshold;
    }

    public synchronized void resetStagnation() { iterationsSinceImprovement = 0; }

    public synchronized int getIterationsSinceImprovement() { return iterationsSinceImprovement; }

    public synchronized void setStagnationThreshold(int threshold) {
        this.stagnationThreshold = Math.max(1, threshold);
    }

    // -------------------------------------------------------------------------
    // Délégation au registre
    // -------------------------------------------------------------------------

    public synchronized int getUniqueVictimsFound() { return registry.getVictimCount(); }

    public synchronized List<List<Position>> getAllVictimPaths() { return registry.getAllPaths(); }

    public synchronized List<Position> getBestPath() { return registry.getBestPath(); }

    public synchronized String getBestPathSignature() { return registry.getBestPathSignature(); }

    public synchronized int getConfirmationCount() { return registry.getConfirmationCount(); }

    public synchronized double getShortestPath() { return registry.getShortestDistance(); }

    public synchronized boolean matchesCurrentBest(String signature) {
        return registry.matchesCurrentBest(signature);
    }

    public synchronized double similarityToCurrentBest(List<Position> candidate) {
        return registry.similarityToCurrentBest(candidate);
    }

    // -------------------------------------------------------------------------
    // Rapport
    // -------------------------------------------------------------------------

    public synchronized void printStats() {
        log.info("=== STATISTIQUES MISSION ===");
        double shortest = getShortestPath();
        log.info("Meilleur chemin: {}",
                shortest == Double.MAX_VALUE ? "N/A" : String.format("%.2f", shortest));
        log.info("Victimes uniques trouvées: {}", registry.getVictimCount());
        log.info("Confirmations courantes: {}/{}", registry.getConfirmationCount(), FULL_THRESHOLD);
        log.info("Découvertes totales: {}", totalDiscoveries);
        log.info("Stagnation: {}/{}", iterationsSinceImprovement, stagnationThreshold);
    }
}
