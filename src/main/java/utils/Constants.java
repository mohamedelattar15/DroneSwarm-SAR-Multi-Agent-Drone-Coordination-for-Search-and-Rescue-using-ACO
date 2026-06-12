package utils;

/**
 * Constantes par défaut pour la simulation DroneSwarm-SAR.
 */
public class Constants {
    public static final int PERCEPTION_RADIUS = 4;
    public static final double ELITE_FACTOR = 0.5;
    public static final double EXTRA_EXPLORATION_RATE = 0.25;
    public static final int EXPLORATION_BOOST_TICKS = 25;
    public static final int DIVERSIFICATION_BOOST_TICKS = 40;
    public static final double VISITED_PATH_PENALTY = 0.35;
    public static final double BACKTRACK_PENALTY = 0.15;
    public static final double PATH_SIMILARITY_THRESHOLD = 0.80;
    public static final double DISTANCE_TOLERANCE_RATIO = 0.05;
    public static final int RECRUITMENT_COOLDOWN = 80;
    public static final long TICK_TIME = 150;

    private Constants() {}
}
