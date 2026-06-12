package environment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import utils.SimulationConfig;
import utils.Statistics;

/**
 * Grille représentant la zone sinistrée.
 * Gère les phéromones, obstacles, positions des victimes et des drones.
 */
public class Grid {

    private final int width;
    private final int height;
    private final double[][] pheromones;
    private final boolean[][] obstacles;
    private final Position nestPosition;
    private final List<Position> victimPositions;
    private final Map<String, Position> dronePositions;
    private List<Position> bestPath = new ArrayList<>();
    private final Random random = new Random();

    public Grid(SimulationConfig config) {
        this.width = config.getGridWidth();
        this.height = config.getGridHeight();
        this.pheromones = new double[width][height];
        this.obstacles = new boolean[width][height];
        this.nestPosition = new Position(config.getNestX(), config.getNestY());
        this.victimPositions = new ArrayList<>();
        this.dronePositions = new ConcurrentHashMap<>();

        // Générer des obstacles aléatoires
        generateObstacles(config.getObstacleCount());

        // Placer les victimes aléatoirement
        generateVictims(config.getVictimCount());
    }

    private void generateObstacles(int count) {
        int placed = 0;
        while (placed < count) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            Position p = new Position(x, y);
            if (!p.equals(nestPosition) && !obstacles[x][y]) {
                obstacles[x][y] = true;
                // Phéromone négative sur les obstacles
                pheromones[x][y] = -5.0;
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

    public synchronized void resetPheromones() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                pheromones[i][j] = obstacles[i][j] ? -5.0 : 0.0;
            }
        }
    }

    public synchronized void addPheromone(Position pos, double amount) {
        if (isValid(pos) && !obstacles[pos.x][pos.y]) {
            pheromones[pos.x][pos.y] += amount;
            if (pheromones[pos.x][pos.y] > 100) pheromones[pos.x][pos.y] = 100;
        }
    }

    public synchronized void addNegativePheromone(Position pos, double amount) {
        if (isValid(pos) && !obstacles[pos.x][pos.y]) {
            pheromones[pos.x][pos.y] -= amount;
            if (pheromones[pos.x][pos.y] < -20) pheromones[pos.x][pos.y] = -20;
        }
    }

    private final Set<String> cachedPathSet = new HashSet<>();
    private int cachedConfirmationCount = -1;

    public synchronized void evaporateDifferentiated(List<Position> confirmedPath, int confirmationCount) {
        double baseRho = utils.SimulationRuntimeControl.getEvaporationRate();
        double rhoConfirmed = Math.min(baseRho * 0.25, 0.01);
        double rhoUnconfirmed = Math.min(baseRho * 2.0, 0.1);

        if (confirmedPath == null || confirmationCount < Statistics.MEDIUM_THRESHOLD) {
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    double val = pheromones[i][j];
                    if (val > 0.01) pheromones[i][j] = val * (1 - rhoUnconfirmed);
                    else if (val < -0.01) pheromones[i][j] = Math.min(0, val + 0.1); // Les négatives remontent vers 0
                }
            }
            return;
        }

        if (confirmationCount != cachedConfirmationCount) {
            cachedPathSet.clear();
            for (Position pos : confirmedPath) cachedPathSet.add(pos.x + "," + pos.y);
            cachedConfirmationCount = confirmationCount;
        }

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                double val = pheromones[i][j];
                if (val > 0.01) {
                    double rho = cachedPathSet.contains(i + "," + j) ? rhoConfirmed : rhoUnconfirmed;
                    pheromones[i][j] = val * (1 - rho);
                } else if (val < -0.01) {
                    pheromones[i][j] = Math.min(0, val + 0.1);
                }
            }
        }
    }

    public synchronized void applyEliteReinforcement(List<Position> bestPath, double factor) {
        if (bestPath == null || factor <= 0) return;
        double eliteAmount = 10.0 * 0.5 * factor;
        for (Position pos : bestPath) {
            addPheromone(pos, eliteAmount);
        }
    }

    public synchronized void partialReset(List<Position> elitePath) {
        Set<String> pathSet = new HashSet<>();
        if (elitePath != null) {
            for (Position pos : elitePath) pathSet.add(pos.x + "," + pos.y);
        }
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (!pathSet.contains(i + "," + j) && !obstacles[i][j]) {
                    pheromones[i][j] = 0.0;
                }
            }
        }
    }

    public synchronized double getPheromone(Position pos) {
        if (isValid(pos)) return pheromones[pos.x][pos.y];
        return 0.0;
    }

    public boolean isValid(Position pos) {
        return pos.x >= 0 && pos.x < width && pos.y >= 0 && pos.y < height;
    }

    public boolean isPassable(Position pos) {
        return isValid(pos) && !obstacles[pos.x][pos.y];
    }

    public boolean isObstacle(Position pos) {
        return isValid(pos) && obstacles[pos.x][pos.y];
    }

    public List<Position> getValidNeighbors(Position pos) {
        List<Position> neighbors = new ArrayList<>();
        int[][] directions = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] dir : directions) {
            Position newPos = new Position(pos.x + dir[0], pos.y + dir[1]);
            if (isValid(newPos) && !obstacles[newPos.x][newPos.y]) {
                neighbors.add(newPos);
            }
        }
        return neighbors;
    }

    public Position getNestPosition() { return nestPosition; }
    public List<Position> getVictimPositions() { return new ArrayList<>(victimPositions); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public synchronized double[][] getPheromoneMap() {
        double[][] snapshot = new double[width][height];
        for (int i = 0; i < width; i++) {
            System.arraycopy(pheromones[i], 0, snapshot[i], 0, height);
        }
        return snapshot;
    }

    public boolean[][] getObstacleMap() {
        return obstacles;
    }

    public void updateDronePosition(String droneId, Position pos) {
        dronePositions.put(droneId, new Position(pos.x, pos.y));
    }

    public Map<String, Position> getDronePositions() {
        return new HashMap<>(dronePositions);
    }

    public synchronized void setBestPath(List<Position> path) {
        this.bestPath = (path == null) ? new ArrayList<>() : new ArrayList<>(path);
    }

    public synchronized List<Position> getBestPath() {
        return new ArrayList<>(bestPath);
    }
}
