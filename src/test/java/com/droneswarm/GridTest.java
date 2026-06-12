package com.droneswarm;

import environment.Grid;
import environment.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import utils.SimulationConfig;

/**
 * Tests unitaires pour le module Grid.
 */
class GridTest {

    @Test
    void testGridInitialization() {
        SimulationConfig config = SimulationConfig.defaults();
        Grid grid = new Grid(config);
        assertEquals(60, grid.getWidth());
        assertEquals(60, grid.getHeight());
        assertNotNull(grid.getNestPosition());
        assertEquals(5, grid.getNestPosition().x);
        assertEquals(5, grid.getNestPosition().y);
    }

    @Test
    void testObstacleGeneration() {
        SimulationConfig config = SimulationConfig.defaults();
        config.setDroneCount(30); // 30 obstacles
        Grid grid = new Grid(config);
        int obstacleCount = 0;
        for (int i = 0; i < grid.getWidth(); i++) {
            for (int j = 0; j < grid.getHeight(); j++) {
                if (!grid.isPassable(new Position(i, j))) obstacleCount++;
            }
        }
        assertEquals(30, obstacleCount);
    }

    @Test
    void testVictimGeneration() {
        SimulationConfig config = SimulationConfig.defaults();
        Grid grid = new Grid(config);
        assertEquals(5, grid.getVictimPositions().size());
        // Les victimes ne doivent pas être sur le nid
        for (Position v : grid.getVictimPositions()) {
            assertNotEquals(grid.getNestPosition(), v);
            assertTrue(grid.isPassable(v));
        }
    }

    @Test
    void testPheromoneAddAndEvaporate() {
        SimulationConfig config = SimulationConfig.defaults();
        Grid grid = new Grid(config);
        Position pos = new Position(10, 10);

        grid.addPheromone(pos, 50.0);
        assertTrue(grid.getPheromone(pos) > 0);

        grid.evaporateDifferentiated(null, 0);
        assertTrue(grid.getPheromone(pos) < 50.0);
    }

    @Test
    void testNegativePheromone() {
        SimulationConfig config = SimulationConfig.defaults();
        Grid grid = new Grid(config);
        Position pos = new Position(15, 15);

        grid.addNegativePheromone(pos, 10.0);
        assertTrue(grid.getPheromone(pos) < 0);
    }

    @Test
    void testValidNeighbors() {
        SimulationConfig config = SimulationConfig.defaults();
        Grid grid = new Grid(config);
        Position center = new Position(30, 30);
        var neighbors = grid.getValidNeighbors(center);
        // 8 directions possibles
        assertTrue(neighbors.size() <= 8);
        assertTrue(neighbors.size() >= 1);
        // Tous les voisins doivent être valides
        for (Position n : neighbors) {
            assertTrue(grid.isPassable(n));
        }
    }

    @Test
    void testResetPheromones() {
        SimulationConfig config = SimulationConfig.defaults();
        Grid grid = new Grid(config);
        Position pos = new Position(20, 20);

        grid.addPheromone(pos, 80.0);
        assertTrue(grid.getPheromone(pos) > 0);

        grid.resetPheromones();
        assertEquals(0.0, grid.getPheromone(pos), 0.001);
    }

    @Test
    void testBestPath() {
        SimulationConfig config = SimulationConfig.defaults();
        Grid grid = new Grid(config);
        java.util.List<Position> path = new java.util.ArrayList<>();
        path.add(new Position(5, 5));
        path.add(new Position(6, 6));
        path.add(new Position(7, 7));

        grid.setBestPath(path);
        assertEquals(3, grid.getBestPath().size());
        assertEquals(new Position(7, 7), grid.getBestPath().get(2));
    }
}
