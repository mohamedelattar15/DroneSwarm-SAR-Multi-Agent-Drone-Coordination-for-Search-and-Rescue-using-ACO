package environment;

import domain.pheromone.PheromoneField;
import domain.victim.VictimRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import utils.SimulationConfig;

/**
 * Grille représentant la zone sinistrée.
 * <p>
 * <b>Rôle :</b> topologie (dimensions, obstacles, voisinage) et positions des drones.
 * La gestion des phéromones est déléguée à {@link PheromoneField} et celle des
 * victimes à {@link VictimRegistry} (source unique de vérité).
 */
public class Grid {

    private final int width;
    private final int height;
    private final boolean[][] obstacles;
    private final Position nestPosition;
    private final List<Position> victimPositions;
    private final Map<String, Position> dronePositions;
    private final Random random = new Random();

    private final PheromoneField pheromones;
    private final VictimRegistry victims;

    public Grid(SimulationConfig config) {
        this.width = config.getGridWidth();
        this.height = config.getGridHeight();
        this.obstacles = new boolean[width][height];
        this.nestPosition = new Position(config.getNestX(), config.getNestY());
        this.victimPositions = new ArrayList<>();
        this.dronePositions = new ConcurrentHashMap<>();
        this.pheromones = new PheromoneField(width, height, config);
        this.victims = new VictimRegistry();

        generateObstacles(config.getObstacleCount());
        generateVictims(config.getVictimCount());
    }

    // -------------------------------------------------------------------------
    // Génération
    // -------------------------------------------------------------------------

    private void generateObstacles(int count) {
        int placed = 0;
        while (placed < count) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            Position p = new Position(x, y);
            if (!p.equals(nestPosition) && !obstacles[x][y]) {
                obstacles[x][y] = true;
                pheromones.set(p, -5.0);
                placed++;
            }
        }
    }

    private void generateVictims(int count) {
        for (int i = 0; i < count; i++) {
            int x, y;
            Position p;
            do {
                x = random.nextInt(width);
                y = random.nextInt(height);
                p = new Position(x, y);
            } while (p.equals(nestPosition) || obstacles[x][y] || victimPositions.contains(p));
            victimPositions.add(p);
        }
    }

    // -------------------------------------------------------------------------
    // Topologie
    // -------------------------------------------------------------------------

    public boolean isValid(Position pos) {
        return pos != null && pos.x >= 0 && pos.x < width && pos.y >= 0 && pos.y < height;
    }

    public boolean isPassable(Position pos) {
        return isValid(pos) && !obstacles[pos.x][pos.y];
    }

    public boolean isObstacle(Position pos) {
        return isValid(pos) && obstacles[pos.x][pos.y];
    }

    /** Voisins praticables (8 directions). */
    public List<Position> getValidNeighbors(Position pos) {
        List<Position> neighbors = new ArrayList<>();
        int[][] directions = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] dir : directions) {
            Position candidate = new Position(pos.x + dir[0], pos.y + dir[1]);
            if (isPassable(candidate)) {
                neighbors.add(candidate);
            }
        }
        return neighbors;
    }

    public Position getNestPosition() { return nestPosition; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    // -------------------------------------------------------------------------
    // Drones
    // -------------------------------------------------------------------------

    public void updateDronePosition(String droneId, Position pos) {
        dronePositions.put(droneId, new Position(pos.x, pos.y));
    }

    /** Snapshot défensif des positions de drones. */
    public Map<String, Position> getDronePositions() {
        return new HashMap<>(dronePositions);
    }

    // -------------------------------------------------------------------------
    // Délégation : phéromones
    // -------------------------------------------------------------------------

    public PheromoneField getPheromones() { return pheromones; }

    public double getPheromone(Position pos) { return pheromones.get(pos); }

    public void addPheromone(Position pos, double amount) { pheromones.deposit(pos, amount); }

    public void addNegativePheromone(Position pos, double amount) { pheromones.repel(pos, amount); }

    public void evaporateDifferentiated(List<Position> confirmedPath, int confirmationCount) {
        pheromones.evaporate(confirmedPath, confirmationCount);
    }

    public void applyEliteReinforcement(List<Position> path, double factor) {
        pheromones.reinforceElite(path, factor);
    }

    public void partialReset(List<Position> elitePath) {
        pheromones.resetExcept(elitePath);
    }

    public void resetPheromones() {
        pheromones.reset(obstacles);
    }

    public double[][] getPheromoneMap() { return pheromones.snapshot(); }

    // -------------------------------------------------------------------------
    // Délégation : victimes
    // -------------------------------------------------------------------------

    public VictimRegistry getVictims() { return victims; }

    public List<Position> getVictimPositions() { return new ArrayList<>(victimPositions); }

    public boolean isVictimAlreadyFound(Position victimPos) { return victims.isFound(victimPos); }

    public void addVictimPath(List<Position> path) { victims.record(path, 0.0); }

    public List<List<Position>> getVictimPaths() { return victims.getAllPaths(); }

    public int getVictimsFound() { return victims.getVictimCount(); }

    /** Meilleur chemin courant (dérivé du registre des victimes). */
    public List<Position> getBestPath() {
        List<Position> best = victims.getBestPath();
        return (best == null) ? new ArrayList<>() : best;
    }

    // -------------------------------------------------------------------------
    // Carte des obstacles
    // -------------------------------------------------------------------------

    public boolean[][] getObstacleMap() {
        boolean[][] copy = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            System.arraycopy(obstacles[x], 0, copy[x], 0, height);
        }
        return copy;
    }
}
