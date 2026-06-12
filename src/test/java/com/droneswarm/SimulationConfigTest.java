package com.droneswarm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import utils.SimulationConfig;
import utils.SimulationRuntimeControl;

/**
 * Tests unitaires pour SimulationConfig et RuntimeControl.
 */
class SimulationConfigTest {

    @Test
    void testDefaultConfig() {
        SimulationConfig config = SimulationConfig.defaults();
        assertEquals(60, config.getGridWidth());
        assertEquals(60, config.getGridHeight());
        assertEquals(10, config.getDroneCount());
        assertEquals(5, config.getVictimCount());
        assertEquals(30, config.getObstacleCount());
    }

    @Test
    void testConfigClamping() {
        SimulationConfig config = SimulationConfig.defaults();
        config.setAlpha(10.0); // Devrait être clampé à 5.0
        assertTrue(config.getAlpha() <= 5.0);
        config.setAlpha(-1.0); // Devrait être clampé à 0.0
        assertTrue(config.getAlpha() >= 0.0);
    }

    @Test
    void testRuntimeControl() {
        SimulationConfig config = SimulationConfig.defaults();
        SimulationRuntimeControl.initialize(config);

        assertEquals(0.2, SimulationRuntimeControl.getExplorationRate(), 0.01);
        assertEquals(1.0, SimulationRuntimeControl.getAlpha(), 0.01);
        assertEquals(1.5, SimulationRuntimeControl.getBeta(), 0.01);

        SimulationRuntimeControl.setPaused(true);
        assertTrue(SimulationRuntimeControl.isPaused());

        SimulationRuntimeControl.setPaused(false);
        assertFalse(SimulationRuntimeControl.isPaused());
    }

    @Test
    void testDroneCount() {
        SimulationConfig config = SimulationConfig.defaults();
        config.setDroneCount(0); // Devrait être clampé à 1
        assertTrue(config.getDroneCount() >= 1);
        config.setDroneCount(50);
        assertEquals(50, config.getDroneCount());
    }
}
