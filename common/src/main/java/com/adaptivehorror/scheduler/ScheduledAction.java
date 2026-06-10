package com.adaptivehorror.scheduler;

/**
 * A single deferred bit of horror: run {@link #action} once the world clock reaches {@link #fireTick}.
 * Queued on the player's state and drained by {@link HorrorScheduler}, this is what lets events play
 * out over time - approaching footsteps, a whispered countdown, a fake player that joins then leaves -
 * instead of firing all at once. Per-player, so it is dropped automatically when the player leaves.
 */
public final class ScheduledAction {

    public final long fireTick;
    public final Runnable action;

    public ScheduledAction(long fireTick, Runnable action) {
        this.fireTick = fireTick;
        this.action = action;
    }
}
