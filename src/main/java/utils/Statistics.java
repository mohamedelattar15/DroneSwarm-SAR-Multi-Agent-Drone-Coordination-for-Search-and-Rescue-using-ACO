package utils;

import environment.Position;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Statistiques et validation collective des chemins.
 * Suit les victimes par leur position finale pour éviter les doublons.
 */
public class Statistics {

    private static final Logger log = LoggerFactory.getLogger(Statistics.class);

    public static class PathUpdate {
        private final double reinforcementFactor;
        private final int confirmationCount;
        private final boolean newBestPath;
        private final String bestPathSignature;
        private final boolean isNewVictim;

        public PathUpdate(double factor, int count, boolean newBest, String sig, boolean newVictim) {
            this.reinforcementFactor = factor;
            this.confirmationCount = count;
            this.newBestPath = newBest;
            this.bestPathSignature = sig;
            this.isNewVictim = newVictim;
        }

        public double getReinforcementFactor() { return reinforcementFactor; }
        public int getConfirmationCount() { return confirmationCount; }
        public boolean isNewBestPath() { return newBestPath; }
        public String getBestPathSignature() { return bestPathSignature; }
        public boolean isNewVictim() { return isNewVictim; }
    }

    /** Suivi des victimes trouvées : position -> meilleur chemin connu */
    private final Map<Position, VictimRecord> victimsMap = new HashMap<>();
    private List<Position> bestPath = null;
    private String bestPathSignature = null;
    private int confirmationCount = 0;
    private int totalDiscoveries = 0;
    private int iterationsSinceImprovement = 0;
    private int stagnationThreshold = 300;

    public static final int MEDIUM_THRESHOLD = 2;
    public static final int FULL_THRESHOLD = 3;

    /** ✅ Configure le seuil de stagnation depuis SimulationConfig */
    public synchronized void setStagnationThreshold(int threshold) {
        this.stagnationThreshold = Math.max(1, threshold);
    }

    /** Enregistrement pour une victime spécifique */
    private static class VictimRecord {
        final List<Position> path;
        final double distance;
        final String signature;
        int confirmations;

        VictimRecord(List<Position> path, double distance, String signature) {
            this.path = path;
            this.distance = distance;
            this.signature = signature;
            this.confirmations = 1;
        }
    }

    public synchronized PathUpdate recordAntPath(List<Position> path, double distance) {
        List<Position> normalizedPath = normalizePath(path);
        String candidateSignature = buildSignature(normalizedPath);
        boolean newBestPath = false;
        boolean isNewVictim = false;
        totalDiscoveries++;

        // Identifier la position de la victime (dernier point du chemin)
        Position victimPos = normalizedPath.isEmpty() ? null : normalizedPath.get(normalizedPath.size() - 1);

        if (victimPos != null && victimsMap.containsKey(victimPos)) {
            // Victime déjà trouvée — incrémenter les confirmations pour CETTE victime
            VictimRecord record = victimsMap.get(victimPos);
            record.confirmations++;

            // Mettre à jour le bestPath avec le chemin de la victime courante
            bestPath = new ArrayList<>(record.path);
            bestPathSignature = record.signature;

            // Si le nouveau chemin est plus court, le garder comme meilleur chemin
            if (distance + 1e-9 < record.distance) {
                victimsMap.put(victimPos, new VictimRecord(normalizedPath, distance, candidateSignature));
                bestPath = new ArrayList<>(normalizedPath);
                bestPathSignature = candidateSignature;
                newBestPath = true;
                iterationsSinceImprovement = 0;
            }

            log.info("[CONFIRMATION] Victime {} confirmée {}/{} fois (distance: {:.1f})",
                    victimPos, record.confirmations, FULL_THRESHOLD, record.distance);
        } else {
            // Nouvelle victime !
            isNewVictim = true;
            victimsMap.put(victimPos, new VictimRecord(normalizedPath, distance, candidateSignature));
            bestPath = new ArrayList<>(normalizedPath);
            bestPathSignature = candidateSignature;
            iterationsSinceImprovement = 0;
            newBestPath = true;

            log.info("[NOUVELLE VICTIME] {} trouvée! Distance: {:.1f}", victimPos, distance);
        }

        // Le confirmationCount global suit la victime du bestPath courant
        if (bestPath != null && !bestPath.isEmpty()) {
            Position bpVictim = bestPath.get(bestPath.size() - 1);
            VictimRecord rec = victimsMap.get(bpVictim);
            confirmationCount = (rec != null) ? rec.confirmations : 1;
        } else {
            confirmationCount = 1;
        }

        return new PathUpdate(reinforcementFactorFor(confirmationCount), confirmationCount,
                newBestPath, bestPathSignature, isNewVictim);
    }

    /** Retourne le nombre total de victimes uniques trouvées */
    public synchronized int getUniqueVictimsFound() {
        return victimsMap.size();
    }

    /** Retourne tous les chemins vers les victimes uniques */
    public synchronized List<List<Position>> getAllVictimPaths() {
        List<List<Position>> paths = new ArrayList<>();
        for (VictimRecord record : victimsMap.values()) {
            paths.add(new ArrayList<>(record.path));
        }
        return paths;
    }

    public synchronized void tick() { iterationsSinceImprovement++; }

    public synchronized boolean isStagnating() {
        return bestPath != null && iterationsSinceImprovement >= stagnationThreshold;
    }

    public synchronized void resetStagnation() {
        iterationsSinceImprovement = 0;
        confirmationCount = Math.max(0, confirmationCount - 1);
    }

    public synchronized int getConfirmationCount() { return confirmationCount; }
    public synchronized List<Position> getBestPath() { return bestPath == null ? null : new ArrayList<>(bestPath); }
    public synchronized double getShortestPath() { return bestPath == null ? Double.MAX_VALUE : victimsMap.values().stream().mapToDouble(r -> r.distance).min().orElse(Double.MAX_VALUE); }
    public synchronized String getBestPathSignature() { return bestPathSignature; }

    public synchronized boolean matchesCurrentBest(String signature) {
        return signature != null && signature.equals(bestPathSignature);
    }

    /**
     * ✅ Validation tolérante : accepte un chemin candidat s'il est suffisamment
     * similaire (Jaccard >= seuil) au meilleur chemin courant, même si la signature
     * exacte diffère. Retourne false si aucun meilleur chemin n'existe encore.
     */
    public synchronized boolean isSimilarToCurrentBest(List<Position> candidate, double threshold) {
        if (candidate == null || bestPath == null) return false;
        return computeSimilarity(candidate, bestPath) >= threshold;
    }

    private double reinforcementFactorFor(int confirmations) {
        if (confirmations >= FULL_THRESHOLD) return 1.0;
        if (confirmations >= MEDIUM_THRESHOLD) return 0.6;
        return 0.3;
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

    /** ✅ Similarité de Jaccard entre deux chemins (0.0 à 1.0) */
    public synchronized double computeSimilarity(List<Position> first, List<Position> second) {
        if (first == null || second == null) return 0.0;
        Set<Position> firstSet = new HashSet<>(first);
        Set<Position> secondSet = new HashSet<>(second);
        int intersection = 0;
        for (Position p : firstSet) { if (secondSet.contains(p)) intersection++; }
        int refSize = Math.max(firstSet.size(), secondSet.size());
        return refSize == 0 ? 0.0 : (double) intersection / refSize;
    }

    /** ✅ Retourne le nombre d'itérations depuis la dernière amélioration */
    public synchronized int getIterationsSinceImprovement() {
        return iterationsSinceImprovement;
    }

    public synchronized void printStats() {
        log.info("=== STATISTIQUES MISSION ===");
        double shortest = getShortestPath();
        log.info("Meilleur chemin: {}", shortest == Double.MAX_VALUE ? "N/A" :
                String.format("%.2f", shortest));
        log.info("Victimes uniques trouvées: {}", victimsMap.size());
        log.info("Confirmations courantes: {}/{}", confirmationCount, FULL_THRESHOLD);
        log.info("Découvertes totales: {}", totalDiscoveries);
        log.info("Stagnation: {}/{}", iterationsSinceImprovement, stagnationThreshold);
    }
}
