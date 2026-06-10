package com.adaptivehorror.scheduler;

import com.adaptivehorror.AdaptiveHorror;
import com.adaptivehorror.events.ChatMessageEvent;
import com.adaptivehorror.events.CountdownEvent;
import com.adaptivehorror.events.FakeJoinEvent;
import com.adaptivehorror.events.FakePlayerEvent;
import com.adaptivehorror.events.AimLockEvent;
import com.adaptivehorror.events.BloodScreenEvent;
import com.adaptivehorror.events.BreathFogEvent;
import com.adaptivehorror.events.CirclingFootstepsEvent;
import com.adaptivehorror.events.FootstepsAboveEvent;
import com.adaptivehorror.events.InventoryShuffleEvent;
import com.adaptivehorror.events.OminousItemEvent;
import com.adaptivehorror.events.PhantomArrowEvent;
import com.adaptivehorror.events.PhantomBiteEvent;
import com.adaptivehorror.events.ColdPresenceEvent;
import com.adaptivehorror.events.CreepingDarknessEvent;
import com.adaptivehorror.events.DistantScreamEvent;
import com.adaptivehorror.events.ForestFireEvent;
import com.adaptivehorror.events.NullBreathingEvent;
import com.adaptivehorror.events.PhantomExplosionEvent;
import com.adaptivehorror.events.SurroundWhispersEvent;
import com.adaptivehorror.events.TrappedFeelingEvent;
import com.adaptivehorror.events.UnseenHunterEvent;
import com.adaptivehorror.events.FootstepStalkEvent;
import com.adaptivehorror.events.ForcedLookEvent;
import com.adaptivehorror.events.GlobalEvent;
import com.adaptivehorror.events.HeartbeatEvent;
import com.adaptivehorror.events.LightsFlickerEvent;
import com.adaptivehorror.events.MobRenameEvent;
import com.adaptivehorror.events.NullPeekEvent;
import com.adaptivehorror.events.PhantomMiningEvent;
import com.adaptivehorror.events.SuddenSilenceEvent;
import com.adaptivehorror.events.JumpscareEvent;
import com.adaptivehorror.events.MusicDistortEvent;
import com.adaptivehorror.events.ScreenEffectEvent;
import com.adaptivehorror.events.ShadowGhostEvent;
import com.adaptivehorror.events.SignEvent;
import com.adaptivehorror.events.SoundIllusionEvent;
import com.adaptivehorror.events.WhisperEvent;
import com.adaptivehorror.events.WorldManipulationEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Central catalogue of {@link HorrorEvent}s and the weighted selector that drives them.
 *
 * <p>When the scheduler decides a player is due for "something", it asks the registry to pick and
 * run a single eligible event. Eligibility is gated by day (escalation), config toggles, and the
 * adaptive vigilance signal; selection is weighted-random among survivors and then scaled by the
 * current intensity. This is the seam that turns a flat list of events into an adaptive, escalating
 * experience without any one event knowing about the others.
 */
public final class EventRegistry {

    private static final List<HorrorEvent> EVENTS = new ArrayList<>();

    static {
        register(new SoundIllusionEvent());     // day 1+
        register(new WhisperEvent());           // day 1+ (over-the-shoulder whisper)
        register(new FootstepStalkEvent());     // day 1+ (approaching footsteps)
        register(new MusicDistortEvent());      // day 2+
        register(new SignEvent());              // day 2+
        register(new ChatMessageEvent());       // day 1+ (incl. your own name)
        register(new JumpscareEvent());         // day 3+
        register(new ScreenEffectEvent());      // day 1+
        register(new ShadowGhostEvent());       // day 3+
        register(new FakeJoinEvent());          // day 4+ (a stranger "joins")
        register(new FakePlayerEvent());        // day 4+
        register(new WorldManipulationEvent()); // day 3+ (door / block / torch / skull)
        register(new CountdownEvent());         // day 5+ (3..2..1.. jumpscare)
        register(new ForcedLookEvent());        // day 4+ (forced look + shake)
        register(new AimLockEvent());           // day 6+ (aim-lock ritual)
        register(new GlobalEvent());            // day 10+ (rare)
        // --- new paranoia beats -----------------------------------------------------------------
        register(new HeartbeatEvent());         // day 2+ (rising heartbeat)
        register(new ColdPresenceEvent());      // day 2+ (a cold something passes through)
        register(new CirclingFootstepsEvent()); // day 2+ (footsteps circle you)
        register(new DistantScreamEvent());     // day 3+ (a scream, far off)
        register(new LightsFlickerEvent());     // day 3+ (the lights fail)
        register(new NullPeekEvent());          // day 3+ (a null peeks and is gone)
        register(new PhantomMiningEvent());     // day 1+ underground (digging toward you)
        register(new MobRenameEvent());         // day 4+ (a mob wears your name)
        register(new SuddenSilenceEvent());     // day 4+ (everything goes quiet, then a sting)
        register(new CreepingDarknessEvent());  // day 5+ (the dark closes in)
        register(new BloodScreenEvent());       // day 4+ (red screen, false wound)
        register(new SurroundWhispersEvent());  // day 4+ (whispers from every side)
        register(new NullBreathingEvent());     // day 3+ (breathing over your shoulder)
        register(new PhantomExplosionEvent());  // day 5+ (a blast in the distance)
        register(new TrappedFeelingEvent());    // day 6+ (you can't leave)
        register(new UnseenHunterEvent());      // day 7+ (something is hunting you)
        register(new FootstepsAboveEvent());    // day 2+ (walking on the ceiling)
        register(new PhantomArrowEvent());      // day 3+ (a shot from the dark)
        register(new BreathFogEvent());         // day 3+ (cold breath in front of you)
        register(new OminousItemEvent());       // day 4+ (a token left at your feet)
        register(new InventoryShuffleEvent());  // day 5+ (your items moved)
        register(new PhantomBiteEvent());       // day 6+ (something snapped at you)
        register(new ForestFireEvent());        // day 3+ (the woods catch fire)
    }

    private EventRegistry() {
    }

    public static void register(HorrorEvent event) {
        EVENTS.add(event);
    }

    /** All registered event ids (for command suggestions). */
    public static java.util.List<String> ids() {
        final java.util.List<String> ids = new ArrayList<>();
        for (HorrorEvent event : EVENTS) {
            ids.add(event.id());
        }
        return ids;
    }

    /** Forcibly runs the event with the given id, bypassing weighting. Returns false if unknown. */
    public static boolean runById(String id, EventContext ctx) {
        for (HorrorEvent event : EVENTS) {
            if (event.id().equals(id)) {
                event.execute(ctx);
                return true;
            }
        }
        return false;
    }

    /**
     * Rolls a single event for this context. Returns true if one fired.
     *
     * <p>The "vigilance" signal damps selection: a player who constantly checks behind them is, per
     * the design, rewarded with fewer manifestations.
     */
    public static boolean maybeRunOne(EventContext ctx) {
        final List<HorrorEvent> eligible = new ArrayList<>();
        double totalWeight = 0.0;
        for (HorrorEvent event : EVENTS) {
            if (ctx.day < event.minDay() || !event.isEnabled(ctx.config)) {
                continue;
            }
            final double w = event.weight(ctx);
            if (w > 0.0) {
                eligible.add(event);
                totalWeight += w;
            }
        }
        if (eligible.isEmpty()) {
            return false;
        }

        // Adaptive damping: high vigilance reduces the chance anything fires at all.
        final double vigilance = ctx.state.behavior.vigilance();
        final double fireChance = Math.min(1.0, ctx.intensity) * (1.0 - 0.5 * vigilance);
        if (ctx.random.nextDouble() > fireChance) {
            return false;
        }

        double roll = ctx.random.nextDouble() * totalWeight;
        for (HorrorEvent event : eligible) {
            roll -= event.weight(ctx);
            if (roll <= 0.0) {
                event.execute(ctx);
                if (ctx.config.debugMode) {
                    AdaptiveHorror.LOGGER.info("Fired event '{}' for {} (day {}, intensity {})",
                            event.id(), ctx.player.getGameProfile().getName(), ctx.day, ctx.intensity);
                }
                return true;
            }
        }
        return false;
    }
}
