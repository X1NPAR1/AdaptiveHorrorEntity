package com.adaptivehorror.scheduler;

/**
 * One discrete supernatural occurrence (a chat whisper, a sign, a sound illusion, a jumpscare...).
 *
 * <p>Events are pure strategy objects: the {@link EventRegistry} decides <em>whether</em> and
 * <em>which</em> to run based on the values below; the event only knows <em>how</em> to manifest. New
 * horror is added by writing one class and registering it - no scheduler edits, no scattered timers.
 */
public interface HorrorEvent {

    /** Stable id, used for logging and per-event cooldown bookkeeping. */
    String id();

    /** Earliest in-game day (0-based) on which this event may occur - the escalation curve. */
    int minDay();

    /** Whether the relevant feature toggle is enabled in config. */
    boolean isEnabled(com.adaptivehorror.config.HorrorConfig config);

    /**
     * Relative selection weight before intensity scaling. Higher = more likely to be chosen when
     * several events are eligible on the same roll. Stable, not a probability.
     */
    double weight(EventContext ctx);

    /** Manifest the event. Runs on the server thread. */
    void execute(EventContext ctx);
}
