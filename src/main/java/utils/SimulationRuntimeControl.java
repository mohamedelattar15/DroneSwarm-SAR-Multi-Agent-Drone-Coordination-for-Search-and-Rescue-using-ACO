package utils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contrôle runtime partagé : pause, vitesse, exploration, alpha, beta, évaporation.
 */
public final class SimulationRuntimeControl {

    private static final AtomicBoolean PAUSED = new AtomicBoolean(false);
    private static final AtomicInteger DRONE_STEP_INTERVAL_MS = new AtomicInteger(150);
    private static final AtomicInteger EXPLORATION_RATE_PER_MILLE = new AtomicInteger(200);
    private static final AtomicInteger ALPHA_PER_MILLE = new AtomicInteger((int) (1.0 * 100));
    private static final AtomicInteger BETA_PER_MILLE = new AtomicInteger((int) (1.5 * 100));
    private static final AtomicInteger EVAPORATION_PER_MILLE = new AtomicInteger((int) (0.02 * 1000));

    private SimulationRuntimeControl() {}

    public static void initialize(SimulationConfig config) {
        PAUSED.set(false);
        DRONE_STEP_INTERVAL_MS.set(Math.max(20, config.getDroneStepIntervalMs()));
        EXPLORATION_RATE_PER_MILLE.set((int) Math.round(config.getRandomExploration() * 1000.0));
        ALPHA_PER_MILLE.set((int) (config.getAlpha() * 100));
        BETA_PER_MILLE.set((int) (config.getBeta() * 100));
        EVAPORATION_PER_MILLE.set((int) (config.getRho() * 1000));
    }

    public static boolean isPaused() { return PAUSED.get(); }
    public static void setPaused(boolean paused) { PAUSED.set(paused); }

    public static int getDroneStepIntervalMs() { return DRONE_STEP_INTERVAL_MS.get(); }
    public static void setDroneStepIntervalMs(int ms) { DRONE_STEP_INTERVAL_MS.set(Math.max(20, ms)); }

    public static double getExplorationRate() { return EXPLORATION_RATE_PER_MILLE.get() / 1000.0; }
    public static void setExplorationRate(double rate) {
        EXPLORATION_RATE_PER_MILLE.set((int) Math.round(clamp(rate, 0.0, 1.0) * 1000.0));
    }

    public static double getAlpha() { return ALPHA_PER_MILLE.get() / 100.0; }
    public static void setAlpha(double val) { ALPHA_PER_MILLE.set((int) clamp(val * 100, 0, 500)); }

    public static double getBeta() { return BETA_PER_MILLE.get() / 100.0; }
    public static void setBeta(double val) { BETA_PER_MILLE.set((int) clamp(val * 100, 0, 500)); }

    public static double getEvaporationRate() { return EVAPORATION_PER_MILLE.get() / 1000.0; }
    public static void setEvaporationRate(double val) { EVAPORATION_PER_MILLE.set((int) clamp(val * 1000, 1, 500)); }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
