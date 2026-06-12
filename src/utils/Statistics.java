package utils;

import environment.Position;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Statistiques et validation collective des chemins.
 */
public class Statistics {

    private static final Logger log = LoggerFactory.getLogger(Statistics.class);

    public static class PathUpdate {
        private final double reinforcementFactor;
        private final int confirmationCount;
        private final boolean newBestPath;
        private final String bestPathSignature;

        public PathUpdate(double factor, int count, boolean newBest, String sig) {
            this.reinforcementFactor = factor;
            this.confirmationCount = count;
            this.newBestPath = newBest;
            this.bestPathSignature = sig;
        }

        public double getReinforcementFactor() { return reinforcementFactor; }
        public int getConfirmationCount() { return confirmationCount; }
        public boolean isNewBestPath() { return newBestPath; }
        public String getBestPathSignature() { return bestPathSignature; }
    }

    private double shortestPathFound = Double.MAX_VALUE;
    private List<Position> bestPath = null;
    private String bestPathSignature = null;
    private int confirmationCount = 0;
    private int totalDiscoveries = 0;
    private int iterationsSinceImprovement = 0;
    private static final int STAGNATION_THRESHOLD = 300;

    public static final int MEDIUM_THRESHOLD = 2;
    public static final int FULL_THRESHOLD = 3;

    public synchronized PathUpdate recordAntPath(List<Position> path, double distance) {
        List<Position> normalizedPath = normalizePath(path);
        String candidateSignature = buildSignature(normalizedPath);
        boolean newBestPath = false;
        totalDiscoveries++;

        if (bestPath == null || distance + 1e-9 < shortestPathFound) {
            shortestPathFound = distance;
            bestPath = new ArrayList<>(normalizedPath);
            bestPathSignature = candidateSignature;
            confirmationCount = 1;
            iterationsSinceImprovement = 0;
            newBestPath = true;
        } else if (isConfirmationOfBestPath(normalizedPath, distance, candidateSignature)) {
            confirmationCount++;
        }

        return new PathUpdate(reinforcementFactorFor(confirmationCount), confirmationCount,
                newBestPath, bestPathSignature);
    }

    public synchronized void tick() { iterationsSinceImprovement++; }

    public synchronized boolean isStagnating() {
        return shortestPathFound < Double.MAX_VALUE && iterationsSinceImprovement >= STAGNATION_THRESHOLD;
    }

    public synchronized void resetStagnation() {
        iterationsSinceImprovement = 0;
        confirmationCount = Math.max(0, confirmationCount - 1);
    }

    public synchronized int getConfirmationCount() { return confirmationCount; }
    public synchronized List<Position> getBestPath() { return bestPath == null ? null : new ArrayList<>(bestPath); }
    public synchronized double getShortestPath() { return shortestPathFound; }
    public synchronized String getBestPathSignature() { return bestPathSignature; }

    public synchronized boolean matchesCurrentBest(String signature) {
        return signature != null && signature.equals(bestPathSignature);
    }

    private double reinforcementFactorFor(int confirmations) {
        if (confirmations >= FULL_THRESHOLD) return 1.0;
        if (confirmations >= MEDIUM_THRESHOLD) return 0.6;
        return 0.3;
    }

    private boolean isConfirmationOfBestPath(List<Position> path, double distance, String candidateSignature) {
        if (bestPath == null || bestPathSignature == null) return false;
        double maxGap = Math.max(1.0, shortestPathFound * Constants.DISTANCE_TOLERANCE_RATIO);
        if (Math.abs(distance - shortestPathFound) > maxGap) return false;
        if (bestPathSignature.equals(candidateSignature)) return true;
        return computeSimilarity(bestPath, path) >= Constants.PATH_SIMILARITY_THRESHOLD;
    }

    private List<Position> normalizePath(List<Position> path) {
        List<Position> normalized = new ArrayList<>();
        Position previous = null;
        for (Position p : path) {
            if (!p.equals(previous)) {
                normalized.add(new Position(p.x, p.y));
                previous = p;
            }
        }
        return normalized;
    }

    private String buildSignature(List<Position> path) {
        StringBuilder sb = new StringBuilder();
        for (Position p : path) {
            if (sb.length() > 0) sb.append("->");
            sb.append(p.x).append(',').append(p.y);
        }
        return sb.toString();
    }

    private double computeSimilarity(List<Position> first, List<Position> second) {
        Set<Position> firstSet = new HashSet<>(first);
        Set<Position> secondSet = new HashSet<>(second);
        int intersection = 0;
        for (Position p : firstSet) { if (secondSet.contains(p)) intersection++; }
        int refSize = Math.max(firstSet.size(), secondSet.size());
        return refSize == 0 ? 0.0 : (double) intersection / refSize;
    }

    public synchronized void printStats() {
        log.info("=== STATISTIQUES MISSION ===");
        log.info("Meilleur chemin: {}", shortestPathFound == Double.MAX_VALUE ? "N/A" :
                String.format("%.2f", shortestPathFound));
        log.info("Confirmations: {}/{}", confirmationCount, FULL_THRESHOLD);
        log.info("Découvertes: {}", totalDiscoveries);
        log.info("Stagnation: {}/{}", iterationsSinceImprovement, STAGNATION_THRESHOLD);
    }
}
