package domain.pheromone;

import environment.Position;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import utils.SimulationConfig;

/**
 * Champ de phéromones virtuelles (stigmergie).
 * <p>
 * Responsabilité unique : stocker, évaporer et renforcer les phéromones.
 * Aucune dépendance à JADE ni à Swing : 100% testable.
 * <p>
 * Convention :
 * <ul>
 *   <li>valeur &gt; 0 : piste attractive (zone explorée avec succès)</li>
 *   <li>valeur &lt; 0 : zone répulsive (obstacle, danger, déjà visitée)</li>
 * </ul>
 */
public class PheromoneField {

    private static final double POSITIVE_MAX = 100.0;
    private static final double NEGATIVE_MIN = -20.0;
    private static final double EPSILON = 0.01;
    private static final double NEGATIVE_DECAY = 0.1;

    private final int width;
    private final int height;
    private final double[][] matrix;
    private final SimulationConfig config;

    // Cache pour l'évaporation différenciée (évite de reconstruire le set à chaque tick)
    private final Set<Long> confirmedCells = new HashSet<>();
    private int cachedConfirmationCount = -1;

    public PheromoneField(int width, int height, SimulationConfig config) {
        this.width = width;
        this.height = height;
        this.config = config;
        this.matrix = new double[width][height];
    }

    // -------------------------------------------------------------------------
    // Dépôt
    // -------------------------------------------------------------------------

    /** Ajoute une phéromone positive (bornée à {@value #POSITIVE_MAX}). */
    public synchronized void deposit(Position pos, double amount) {
        if (!isValid(pos)) return;
        matrix[pos.x][pos.y] = Math.min(POSITIVE_MAX, matrix[pos.x][pos.y] + amount);
    }

    /** Ajoute une phéromone négative (bornée à {@value #NEGATIVE_MIN}). */
    public synchronized void repel(Position pos, double amount) {
        if (!isValid(pos)) return;
        matrix[pos.x][pos.y] = Math.max(NEGATIVE_MIN, matrix[pos.x][pos.y] - amount);
    }

    /** Force une valeur (utilisé pour initialiser les obstacles). */
    public synchronized void set(Position pos, double value) {
        if (!isValid(pos)) return;
        matrix[pos.x][pos.y] = value;
    }

    public synchronized double get(Position pos) {
        return isValid(pos) ? matrix[pos.x][pos.y] : 0.0;
    }

    // -------------------------------------------------------------------------
    // Évaporation différenciée
    // -------------------------------------------------------------------------

    /**
     * Évaporation différenciée : les cellules d'un chemin confirmé s'évaporent
     * lentement (protection de la piste), le reste s'évapore vite (nettoyage du bruit).
     *
     * @param confirmedPath     chemin confirmé (peut être null)
     * @param confirmationCount nombre de confirmations collectives
     */
    public synchronized void evaporate(List<Position> confirmedPath, int confirmationCount) {
        double baseRho = utils.SimulationRuntimeControl.getEvaporationRate();
        double rhoConfirmed = Math.min(baseRho * config.getRhoConfirmedFactor(), 0.01);
        double rhoUnconfirmed = Math.min(baseRho * config.getRhoUnconfirmedFactor(), 0.1);

        boolean hasConfirmedPath = confirmedPath != null
                && confirmationCount >= utils.Statistics.MEDIUM_THRESHOLD;

        if (hasConfirmedPath && confirmationCount != cachedConfirmationCount) {
            rebuildConfirmedCache(confirmedPath, confirmationCount);
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double value = matrix[x][y];
                if (value > EPSILON) {
                    double rho = (hasConfirmedPath && confirmedCells.contains(key(x, y)))
                            ? rhoConfirmed : rhoUnconfirmed;
                    matrix[x][y] = value * (1 - rho);
                } else if (value < -EPSILON) {
                    // Les phéromones négatives remontent doucement vers 0
                    matrix[x][y] = Math.min(0, value + NEGATIVE_DECAY);
                }
            }
        }
    }

    private void rebuildConfirmedCache(List<Position> confirmedPath, int confirmationCount) {
        confirmedCells.clear();
        for (Position pos : confirmedPath) {
            confirmedCells.add(key(pos.x, pos.y));
        }
        cachedConfirmationCount = confirmationCount;
    }

    /** Renforcement élite : ajoute de la phéromone sur tout le meilleur chemin. */
    public synchronized void reinforceElite(List<Position> path, double factor) {
        if (path == null || factor <= 0) return;
        double amount = config.getEliteFactor() * 10.0 * factor;
        for (Position pos : path) {
            deposit(pos, amount);
        }
    }

    /** Remet à zéro toutes les cellules sauf le chemin élite (diversification). */
    public synchronized void resetExcept(List<Position> elitePath) {
        Set<Long> keep = new HashSet<>();
        if (elitePath != null) {
            for (Position pos : elitePath) keep.add(key(pos.x, pos.y));
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!keep.contains(key(x, y))) {
                    matrix[x][y] = 0.0;
                }
            }
        }
    }

    /** Réinitialise tout le champ (obstacles conservés à -5.0). */
    public synchronized void reset(boolean[][] obstacles) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                matrix[x][y] = (obstacles != null && obstacles[x][y]) ? -5.0 : 0.0;
            }
        }
        confirmedCells.clear();
        cachedConfirmationCount = -1;
    }

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    /** Snapshot défensif de la matrice (pour le rendu). */
    public synchronized double[][] snapshot() {
        double[][] copy = new double[width][height];
        for (int x = 0; x < width; x++) {
            System.arraycopy(matrix[x], 0, copy[x], 0, height);
        }
        return copy;
    }

    private boolean isValid(Position pos) {
        return pos != null && pos.x >= 0 && pos.x < width && pos.y >= 0 && pos.y < height;
    }

    /** Clé compacte pour le cache (grille &lt; 100000 de côté). */
    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
