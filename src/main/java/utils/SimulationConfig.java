package utils;

import java.io.Serializable;

/**
 * Configuration mutable de la simulation de sauvetage par drones.
 */
public class SimulationConfig implements Serializable {

    private int gridWidth = 60;
    private int gridHeight = 60;
    private int nestX = 30;
    private int nestY = 30;
    private int droneCount = 10;
    private int victimCount = 5;
    private double alpha = 1.0;
    private double beta = 1.5;
    private double rho = 0.02;
    private double rhoConfirmed = 0.005;
    private double rhoUnconfirmed = 0.04;
    private double rhoConfirmedFactor = 0.25;
    private double rhoUnconfirmedFactor = 2.0;
    private double eliteFactor = 0.5;
    private double randomExploration = 0.20;
    private double extraExplorationRate = 0.25;
    private int explorationBoostTicks = 25;
    private int diversificationBoostTicks = 40;
    private double visitedPathPenalty = 0.35;
    private double backtrackPenalty = 0.15;
    private double pathSimilarityThreshold = 0.80;
    private double distanceToleranceRatio = 0.05;
    private int recruitmentCooldown = 80;
    private int perceptionRadius = 4;
    private int stagnationThreshold = 300;
    private int mediumThreshold = 2;
    private int fullThreshold = 3;
    private long tickTime = 150;
    private int droneStepIntervalMs = 150;
    private int idleDelayMs = 500;
    private int maxDroneSteps = 3000;
    private int obstacleCount = 30;
    private double negativePheromoneWeight = -2.0;

    public static SimulationConfig defaults() { return new SimulationConfig(); }

    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }
    public int getNestX() { return nestX; }
    public int getNestY() { return nestY; }
    public int getDroneCount() { return droneCount; }
    public int getVictimCount() { return victimCount; }
    public double getAlpha() { return alpha; }
    public double getBeta() { return beta; }
    public double getRho() { return rho; }
    public double getRhoConfirmed() { return rhoConfirmed; }
    public double getRhoUnconfirmed() { return rhoUnconfirmed; }
    public double getRhoConfirmedFactor() { return rhoConfirmedFactor; }
    public double getRhoUnconfirmedFactor() { return rhoUnconfirmedFactor; }
    public double getEliteFactor() { return eliteFactor; }
    public double getRandomExploration() { return randomExploration; }
    public double getExtraExplorationRate() { return extraExplorationRate; }
    public int getExplorationBoostTicks() { return explorationBoostTicks; }
    public int getDiversificationBoostTicks() { return diversificationBoostTicks; }
    public double getVisitedPathPenalty() { return visitedPathPenalty; }
    public double getBacktrackPenalty() { return backtrackPenalty; }
    public double getPathSimilarityThreshold() { return pathSimilarityThreshold; }
    public double getDistanceToleranceRatio() { return distanceToleranceRatio; }
    public int getRecruitmentCooldown() { return recruitmentCooldown; }
    public int getPerceptionRadius() { return perceptionRadius; }
    public int getStagnationThreshold() { return stagnationThreshold; }
    public int getMediumThreshold() { return mediumThreshold; }
    public int getFullThreshold() { return fullThreshold; }
    public long getTickTime() { return tickTime; }
    public int getDroneStepIntervalMs() { return droneStepIntervalMs; }
    public int getIdleDelayMs() { return idleDelayMs; }
    public int getMaxDroneSteps() { return maxDroneSteps; }
    public int getObstacleCount() { return obstacleCount; }
    public double getNegativePheromoneWeight() { return negativePheromoneWeight; }

    public void setAlpha(double v) { this.alpha = clamp(v, 0.0, 5.0); }
    public void setBeta(double v) { this.beta = clamp(v, 0.0, 10.0); }
    public void setRandomExploration(double v) { this.randomExploration = clamp(v, 0.0, 1.0); }
    public void setRho(double v) { this.rho = clamp(v, 0.001, 0.5); }
    public void setDroneStepIntervalMs(int v) { this.droneStepIntervalMs = Math.max(20, v); }
    public void setGridWidth(int v) { this.gridWidth = Math.max(10, v); }
    public void setGridHeight(int v) { this.gridHeight = Math.max(10, v); }
    public void setVictimCount(int v) { this.victimCount = Math.max(1, v); }
    public void setObstacleCount(int v) { this.obstacleCount = Math.max(0, v); }
    public void setPerceptionRadius(int v) { this.perceptionRadius = Math.max(1, v); }
    public void setStagnationThreshold(int v) { this.stagnationThreshold = Math.max(50, v); }
    public void setMaxDroneSteps(int v) { this.maxDroneSteps = Math.max(100, v); }
    public void setDroneCount(int v) { this.droneCount = Math.max(1, v); }
    public void setTickTime(long v) { this.tickTime = Math.max(20, v); }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public String toString() {
        return String.format("Config[grid=%dx%d, drones=%d, victims=%d, alpha=%.1f, beta=%.1f, rho=%.3f]",
                gridWidth, gridHeight, droneCount, victimCount, alpha, beta, rho);
    }
}
