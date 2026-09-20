package domain.drone;

/**
 * Modèle d'état d'un drone — machine à états et gestion d'autonomie.
 * <p>
 * Extrait de {@code DroneAgent} pour isoler la logique métier de l'infrastructure JADE.
 * 100% testable, sans dépendance externe.
 */
public class DroneModel {

    /** États possibles d'un drone. */
    public enum State {
        /** En exploration active. */
        EXPLORING,
        /** Retour à la base après avoir trouvé une victime. */
        RETURNING,
        /** Retour à la base sans victime (batterie faible). */
        RETURNING_EMPTY,
        /** Au repos à la base. */
        IDLE
    }

    private State state = State.EXPLORING;
    private int stepsSinceLastFind;
    private int stepsSinceLastReturn;
    private int explorationBoostTicks;

    private final int maxSteps;

    public DroneModel(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    // -------------------------------------------------------------------------
    // Transitions
    // -------------------------------------------------------------------------

    /**
     * Met à jour l'état en fonction des timeouts.
     * À appeler au début de chaque tick.
     */
    public void tick() {
        stepsSinceLastFind++;
        stepsSinceLastReturn++;
        if (explorationBoostTicks > 0) explorationBoostTicks--;
    }

    /** Vrai si la batterie est faible et qu'il faut rentrer. */
    public boolean shouldReturnForBattery() {
        return state == State.EXPLORING && stepsSinceLastFind > maxSteps;
    }

    /** Vrai si le drone est perdu (timeout de retour dépassé). */
    public boolean isLost() {
        return (state == State.RETURNING || state == State.RETURNING_EMPTY)
                && stepsSinceLastReturn > maxSteps;
    }

    /** Vrai si le drone est en train de rentrer. */
    public boolean isReturning() {
        return state == State.RETURNING || state == State.RETURNING_EMPTY;
    }

    /** Vrai si le drone explore. */
    public boolean isExploring() {
        return state == State.EXPLORING;
    }

    // -------------------------------------------------------------------------
    // Événements
    // -------------------------------------------------------------------------

    /** Signal de batterie faible : passe en retour vide. */
    public void onBatteryLow() {
        state = State.RETURNING_EMPTY;
        stepsSinceLastFind = 0;
    }

    /** Signal de drone perdu : réinitialisation. */
    public void onLost() {
        state = State.IDLE;
        stepsSinceLastReturn = 0;
    }

    /** Signal d'arrivée à la base : réinitialisation complète. */
    public void onArrivedAtBase() {
        state = State.IDLE;
        stepsSinceLastFind = 0;
        stepsSinceLastReturn = 0;
    }

    /** Signal de départ depuis la base. */
    public void onDeparture() {
        if (state == State.IDLE) state = State.EXPLORING;
    }

    /** Signal de détection d'une victime pendant l'exploration. */
    public void onVictimFound() {
        stepsSinceLastFind = 0;
        if (state == State.EXPLORING) state = State.RETURNING;
    }

    /** Signal de diversification : booste l'exploration. */
    public void boostExploration(int ticks) {
        explorationBoostTicks = Math.max(explorationBoostTicks, ticks);
    }

    /** Signal d'arrêt de mission (toutes les victimes trouvées). */
    public void onMissionComplete() {
        state = State.IDLE;
    }

    // -------------------------------------------------------------------------
    // Accès
    // -------------------------------------------------------------------------

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public int getExplorationBoostTicks() { return explorationBoostTicks; }
    public int getStepsSinceLastFind() { return stepsSinceLastFind; }
    public int getStepsSinceLastReturn() { return stepsSinceLastReturn; }
}
