package domain.aco;

import domain.pheromone.PheromoneField;
import environment.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Optimiseur ACO (Ant Colony Optimization) — sélection probabiliste du prochain pas.
 * <p>
 * Implémente la règle de transition ACO enrichie :
 * <pre>
 *   P(i) ∝ (τ_i + 1)^α · (η_i)^β · persistance · pénalités · biais · répulsion
 * </pre>
 * où τ = phéromone, η = attraction vers la victime.
 * <p>
 * Aucune dépendance à JADE ni à Swing : entièrement testable.
 */
public class AntColonyOptimizer {

    /** Contexte d'un pas de décision (valeurs calculées par l'appelant). */
    public static class StepContext {
        public final Position current;
        public final Position lastDirection;
        public final Position previousPosition;
        public final Position preferredDirection;
        public final List<Position> otherDrones;
        public final List<Position> recentPath;
        public final List<Position> undiscoveredVictims;
        public final double alpha;
        public final double beta;
        public final double explorationRate;
        public final double visitedPenalty;
        public final double backtrackPenalty;
        public final double directionalBiasStrength;
        public final double repulsionWeight;
        public final int repulsionRadius;
        public final int recentWindow;

        public StepContext(Position current, Position lastDirection, Position previousPosition,
                           Position preferredDirection, List<Position> otherDrones,
                           List<Position> recentPath, List<Position> undiscoveredVictims,
                           double alpha, double beta, double explorationRate,
                           double visitedPenalty, double backtrackPenalty,
                           double directionalBiasStrength, double repulsionWeight,
                           int repulsionRadius, int recentWindow) {
            this.current = current;
            this.lastDirection = lastDirection;
            this.previousPosition = previousPosition;
            this.preferredDirection = preferredDirection;
            this.otherDrones = otherDrones;
            this.recentPath = recentPath;
            this.undiscoveredVictims = undiscoveredVictims;
            this.alpha = alpha;
            this.beta = beta;
            this.explorationRate = explorationRate;
            this.visitedPenalty = visitedPenalty;
            this.backtrackPenalty = backtrackPenalty;
            this.directionalBiasStrength = directionalBiasStrength;
            this.repulsionWeight = repulsionWeight;
            this.repulsionRadius = repulsionRadius;
            this.recentWindow = recentWindow;
        }
    }

    private final Random random;

    public AntColonyOptimizer() {
        this(new Random());
    }

    public AntColonyOptimizer(Random random) {
        this.random = random;
    }

    /**
     * Sélectionne le prochain pas parmi les candidats.
     *
     * @param candidates voisins praticables
     * @param field      champ de phéromones
     * @param ctx        contexte de décision
     * @return la position choisie, ou null si aucun candidat
     */
    public Position selectNext(List<Position> candidates, PheromoneField field, StepContext ctx) {
        if (candidates == null || candidates.isEmpty()) return null;

        List<Position> filtered = filterBacktrack(candidates, ctx);
        if (filtered.isEmpty()) filtered = candidates;

        // Exploration aléatoire (diversification)
        if (random.nextDouble() < ctx.explorationRate) {
            return filtered.get(random.nextInt(filtered.size()));
        }

        double[] weights = new double[filtered.size()];
        double total = 0.0;
        for (int i = 0; i < filtered.size(); i++) {
            weights[i] = weight(filtered.get(i), field, ctx);
            total += weights[i];
        }
        if (total <= 0) {
            return filtered.get(random.nextInt(filtered.size()));
        }

        double r = random.nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (r <= cumulative) {
                return filtered.get(i);
            }
        }
        return filtered.get(filtered.size() - 1);
    }

    /** Calcule le poids ACO d'un candidat. */
    private double weight(Position candidate, PheromoneField field, StepContext ctx) {
        double pheromone = Math.max(0.0, field.get(candidate));
        double attraction = victimAttraction(candidate, ctx.undiscoveredVictims);

        return Math.pow(pheromone + 1.0, ctx.alpha)
                * Math.pow(attraction, ctx.beta)
                * persistence(candidate, ctx)
                * (hasVisitedRecently(candidate, ctx) ? ctx.visitedPenalty : 1.0)
                * (candidate.equals(ctx.previousPosition) ? ctx.backtrackPenalty : 1.0)
                * directionalBias(candidate, ctx)
                * repulsion(candidate, ctx);
    }

    private static List<Position> filterBacktrack(List<Position> candidates, StepContext ctx) {
        if (ctx.previousPosition == null || candidates.size() <= 1) return new ArrayList<>(candidates);
        List<Position> filtered = new ArrayList<>();
        for (Position p : candidates) {
            if (!p.equals(ctx.previousPosition)) filtered.add(p);
        }
        return filtered;
    }

    /** Persistance de direction : favorise la continuité du vol. */
    private static double persistence(Position candidate, StepContext ctx) {
        if (ctx.lastDirection == null) return 1.0;
        int dx = candidate.x - ctx.current.x;
        int dy = candidate.y - ctx.current.y;
        return (dx == ctx.lastDirection.x && dy == ctx.lastDirection.y) ? 3.0 : 1.0;
    }

    /** Attraction vers la victime non découverte la plus proche. */
    private static double victimAttraction(Position candidate, List<Position> victims) {
        double max = 0.0;
        for (Position victim : victims) {
            if (candidate.equals(victim)) return 100.0;
            int dist = candidate.manhattanTo(victim);
            max = Math.max(max, 1.0 + (50.0 / (dist + 1)));
        }
        return max;
    }

    /** Pénalise les cases récemment parcourues (anti-boucle). */
    private static boolean hasVisitedRecently(Position candidate, StepContext ctx) {
        if (ctx.recentPath == null) return false;
        int start = Math.max(0, ctx.recentPath.size() - ctx.recentWindow);
        for (int i = start; i < ctx.recentPath.size(); i++) {
            if (ctx.recentPath.get(i).equals(candidate)) return true;
        }
        return false;
    }

    /** Biais vers la direction préférée du drone (couverture répartie). */
    private static double directionalBias(Position candidate, StepContext ctx) {
        if (ctx.preferredDirection == null) return 1.0;
        int dx = candidate.x - ctx.current.x;
        int dy = candidate.y - ctx.current.y;
        double dot = dx * ctx.preferredDirection.x + dy * ctx.preferredDirection.y;
        if (dot <= 0) return 0.5;
        double norm = Math.sqrt((double) dx * dx + (double) dy * dy)
                * Math.sqrt((double) ctx.preferredDirection.x * ctx.preferredDirection.x
                          + (double) ctx.preferredDirection.y * ctx.preferredDirection.y);
        if (norm == 0) return 1.0;
        return 1.0 + Math.max(0, dot / norm) * ctx.directionalBiasStrength;
    }

    /** Répulsion vis-à-vis des autres drones (évite la redondance). */
    private static double repulsion(Position candidate, StepContext ctx) {
        double sum = 0.0;
        for (Position other : ctx.otherDrones) {
            int dist = candidate.manhattanTo(other);
            if (dist > 0 && dist <= ctx.repulsionRadius) {
                sum += (ctx.repulsionRadius - dist + 1.0) / ctx.repulsionRadius;
            }
        }
        return 1.0 - (sum * ctx.repulsionWeight);
    }
}
