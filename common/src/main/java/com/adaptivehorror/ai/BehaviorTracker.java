package com.adaptivehorror.ai;

/**
 * Rolling, decayed observations of how a player behaves. The adaptive AI reads these signals to bias
 * event selection (e.g. a player who constantly checks behind them gets fewer rear appearances; a
 * player who camps indoors gets window sightings).
 *
 * <p>Signals are stored as exponentially-decayed scores rather than raw counters so that recent
 * behaviour dominates and the entity "adapts" rather than accumulating forever. Decay is applied
 * lazily on read/update via {@link #decay(double)} driven by the scheduler.
 */
public final class BehaviorTracker {

    /** How sharply a player turns to look behind them (checking their six). */
    public double lookBehindScore;
    /** Time spent enclosed / indoors. */
    public double campingScore;
    /** Time spent mining underground. */
    public double miningScore;
    /** Block-placement activity (building). */
    public double buildingScore;
    /** Stationary-with-no-input streak (AFK), in ticks. */
    public int afkTicks;

    private static final double SIGNAL_CAP = 100.0;

    public void addLookBehind(double amount) {
        lookBehindScore = Math.min(SIGNAL_CAP, lookBehindScore + amount);
    }

    public void addCamping(double amount) {
        campingScore = Math.min(SIGNAL_CAP, campingScore + amount);
    }

    public void addMining(double amount) {
        miningScore = Math.min(SIGNAL_CAP, miningScore + amount);
    }

    public void addBuilding(double amount) {
        buildingScore = Math.min(SIGNAL_CAP, buildingScore + amount);
    }

    /** Applies multiplicative decay to all continuous signals. {@code factor} in (0,1). */
    public void decay(double factor) {
        lookBehindScore *= factor;
        campingScore *= factor;
        miningScore *= factor;
        buildingScore *= factor;
    }

    /** Normalised dominance of "checks behind often", 0-1, used to scale rear-appearance odds. */
    public double vigilance() {
        return lookBehindScore / SIGNAL_CAP;
    }
}
