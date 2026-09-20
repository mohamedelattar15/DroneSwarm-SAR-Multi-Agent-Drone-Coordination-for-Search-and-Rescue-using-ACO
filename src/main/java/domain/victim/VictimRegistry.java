package domain.victim;

import environment.Position;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registre des victimes — <b>source unique de vérité</b>.
 * <p>
 * Remplace les trois compteurs auparavant dispersés dans {@code Grid},
 * {@code Statistics} et {@code SimulationFrame}, cause de désynchronisations.
 * <p>
 * Chaque victime est identifiée par sa position (clé) et associée à un
 * {@link VictimRecord} contenant son meilleur chemin et son nombre de confirmations.
 */
public class VictimRegistry {

    /** Seuil de confirmations pour une validation "moyenne". */
    public static final int MEDIUM_THRESHOLD = 2;
    /** Seuil de confirmations pour une validation "complète". */
    public static final int FULL_THRESHOLD = 3;

    /** Enregistrement d'une victime : meilleur chemin connu + confirmations. */
    public static class VictimRecord {
        private List<Position> bestPath;
        private double bestDistance;
        private String signature;
        private int confirmations;

        VictimRecord(List<Position> path, double distance, String signature) {
            this.bestPath = new ArrayList<>(path);
            this.bestDistance = distance;
            this.signature = signature;
            this.confirmations = 1;
        }

        public List<Position> getBestPath() { return new ArrayList<>(bestPath); }
        public double getBestDistance() { return bestDistance; }
        public String getSignature() { return signature; }
        public int getConfirmations() { return confirmations; }
    }

    /** Résultat d'un enregistrement de détection. */
    public static class DetectionResult {
        private final double reinforcementFactor;
        private final int confirmationCount;
        private final boolean newVictim;
        private final boolean improvedPath;
        private final String signature;

        DetectionResult(double factor, int confirmations, boolean newVictim,
                        boolean improvedPath, String signature) {
            this.reinforcementFactor = factor;
            this.confirmationCount = confirmations;
            this.newVictim = newVictim;
            this.improvedPath = improvedPath;
            this.signature = signature;
        }

        public double getReinforcementFactor() { return reinforcementFactor; }
        public int getConfirmationCount() { return confirmationCount; }
        public boolean isNewVictim() { return newVictim; }
        public boolean isImprovedPath() { return improvedPath; }
        public String getSignature() { return signature; }
    }

    /** Victimes connues, ordonnées par découverte. */
    private final Map<Position, VictimRecord> victims = new LinkedHashMap<>();
    /** Victime actuellement "élite" (celle du meilleur chemin courant). */
    private Position currentTarget;

    /**
     * Enregistre une détection de victime.
     *
     * @param path     chemin parcouru (dernier élément = position de la victime)
     * @param distance longueur du chemin
     * @return le résultat (facteur de renfort, confirmations, nouveauté)
     */
    public synchronized DetectionResult record(List<Position> path, double distance) {
        List<Position> normalized = normalize(path);
        if (normalized.isEmpty()) {
            return new DetectionResult(0.3, 1, false, false, "");
        }

        Position victimPos = normalized.get(normalized.size() - 1);
        String signature = buildSignature(normalized);
        boolean isNew = false;
        boolean improved = false;

        VictimRecord record = victims.get(victimPos);
        if (record == null) {
            record = new VictimRecord(normalized, distance, signature);
            victims.put(victimPos, record);
            isNew = true;
            improved = true;
        } else {
            record.confirmations++;
            if (distance + 1e-9 < record.bestDistance) {
                record.bestPath = new ArrayList<>(normalized);
                record.bestDistance = distance;
                record.signature = signature;
                improved = true;
            }
        }
        currentTarget = victimPos;

        return new DetectionResult(reinforcementFactorFor(record.confirmations),
                record.confirmations, isNew, improved, signature);
    }

    /** Nombre de victimes distinctes détectées. */
    public synchronized int getVictimCount() {
        return victims.size();
    }

    /** Vrai si la victime à cette position a déjà été détectée. */
    public synchronized boolean isFound(Position victimPos) {
        return victimPos != null && victims.containsKey(victimPos);
    }

    /** Le meilleur chemin de la victime ciblée (ou null). */
    public synchronized List<Position> getBestPath() {
        VictimRecord record = (currentTarget == null) ? null : victims.get(currentTarget);
        return (record == null) ? null : record.getBestPath();
    }

    /** Signature du meilleur chemin courant (ou null). */
    public synchronized String getBestPathSignature() {
        VictimRecord record = (currentTarget == null) ? null : victims.get(currentTarget);
        return (record == null) ? null : record.getSignature();
    }

    /** Nombre de confirmations de la victime ciblée. */
    public synchronized int getConfirmationCount() {
        VictimRecord record = (currentTarget == null) ? null : victims.get(currentTarget);
        return (record == null) ? 0 : record.getConfirmations();
    }

    /** Distance du meilleur chemin parmi toutes les victimes. */
    public synchronized double getShortestDistance() {
        return victims.values().stream()
                .mapToDouble(r -> r.bestDistance)
                .min().orElse(Double.MAX_VALUE);
    }

    /** Tous les meilleurs chemins (un par victime). */
    public synchronized List<List<Position>> getAllPaths() {
        List<List<Position>> paths = new ArrayList<>();
        for (VictimRecord record : victims.values()) {
            paths.add(record.getBestPath());
        }
        return paths;
    }

    /** Vrai si le chemin candidat correspond exactement au meilleur courant. */
    public synchronized boolean matchesCurrentBest(String signature) {
        return signature != null && signature.equals(getBestPathSignature());
    }

    /** Similarité de Jaccard entre un chemin candidat et le meilleur courant. */
    public synchronized double similarityToCurrentBest(List<Position> candidate) {
        List<Position> best = getBestPath();
        if (candidate == null || best == null) return 0.0;
        return jaccard(candidate, best);
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private static double jaccard(List<Position> a, List<Position> b) {
        java.util.Set<Position> setA = new java.util.HashSet<>(a);
        java.util.Set<Position> setB = new java.util.HashSet<>(b);
        int intersection = 0;
        for (Position p : setA) {
            if (setB.contains(p)) intersection++;
        }
        int union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static List<Position> normalize(List<Position> path) {
        List<Position> result = new ArrayList<>();
        Position previous = null;
        for (Position p : path) {
            if (!p.equals(previous)) {
                result.add(new Position(p.x, p.y));
                previous = p;
            }
        }
        return result;
    }

    private static String buildSignature(List<Position> path) {
        StringBuilder sb = new StringBuilder();
        for (Position p : path) {
            if (sb.length() > 0) sb.append("->");
            sb.append(p.x).append(',').append(p.y);
        }
        return sb.toString();
    }

    private static double reinforcementFactorFor(int confirmations) {
        if (confirmations >= FULL_THRESHOLD) return 1.0;
        if (confirmations >= MEDIUM_THRESHOLD) return 0.6;
        return 0.3;
    }
}
