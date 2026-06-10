package com.adaptivehorror.ai;

import com.adaptivehorror.scheduler.DayProgression;
import com.adaptivehorror.spawn.StalkerBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/**
 * The "brain" behind the stalker - it doesn't pick spots at random, it <em>reads the player</em> and
 * grows bolder over time.
 *
 * <ul>
 *   <li>A player hiding indoors at night gets watched from the <b>window</b>.</li>
 *   <li>A player standing still (AFK) gets it looming <b>right behind</b> them.</li>
 *   <li>A player who keeps spinning to check their back is kept at a <b>distance</b> - it stays out of
 *       the obvious spots.</li>
 *   <li>By day it always keeps its distance.</li>
 * </ul>
 *
 * <p>And with every encounter it survives, the entity's <b>pressure</b> rises: it strikes a little
 * more readily each time, so a confident player is slowly worn down.
 */
public final class AdaptiveAI {

    /** Seconds of stillness after which the AFK behaviour kicks in. */
    private static final long AFK_TICKS = 20L * 90L; // 90s

    private AdaptiveAI() {
    }

    /** Multiplier (1.0 → ~1.8) applied to the strike chance, rising with encounters survived. */
    public static double pressure(PlayerHorrorState state) {
        return 1.0 + Math.min(0.8, state.encounters * 0.03);
    }

    /**
     * Base strike chance for the in-game day - it escalates steeply: ~0.20 the first day, 0.40 the
     * second, 0.80 the third, 1.25 the fourth, then +0.25 each day after. By day 10+ it is effectively
     * always striking (values &gt;1 also feed the kill chance).
     */
    public static double attackBase(int day) {
        if (day <= 0) {
            return 0.20;
        }
        if (day == 1) {
            return 0.40;
        }
        if (day == 2) {
            return 0.80;
        }
        return 1.25 + (day - 3) * 0.25;
    }

    /**
     * The full strike chance: the day curve, made deadlier underground/at night and by accumulated
     * pressure. Clamped to [0,1] for the roll.
     */
    public static double strikeChance(ServerLevel level, PlayerHorrorState state, boolean cave, boolean black) {
        final int day = DayProgression.dayOf(level);
        double chance = attackBase(day);
        if (cave) {
            chance += 0.20;                       // caves are the deadliest
        } else if (!level.isDay() || black) {
            chance += 0.12;                       // night, or the rare daytime black form, is deadlier
        }
        return Math.min(1.0, chance * pressure(state));
    }

    /** Chance a strike actually kills, also rising with the day (very lethal past day 10). */
    public static double killChance(int day, double configBase) {
        return Math.min(0.85, configBase + Math.max(0, day) * 0.05);
    }

    /** Picks where the next surface stalker appears, reading the player's recent behaviour. */
    public static StalkerBehavior chooseSurfacePlacement(ServerPlayer player, PlayerHorrorState state,
                                                         ServerLevel level, Random random) {
        final boolean night = !level.isDay();
        final boolean sheltered = !level.canSeeSky(player.blockPosition());
        final BehaviorTracker b = state.behavior;

        // Hiding inside at night -> it watches the windows.
        if (night && sheltered) {
            return StalkerBehavior.WINDOW;
        }
        // By day it is always the distant watcher.
        if (!night) {
            return StalkerBehavior.FAR;
        }
        // Night, out in the open: adapt to the player.
        if (b.afkTicks >= AFK_TICKS) {
            return StalkerBehavior.BEHIND;          // not moving -> loom behind
        }
        if (b.vigilance() > 0.5) {
            return StalkerBehavior.FAR;             // keeps checking -> stay out of reach
        }
        return random.nextBoolean() ? StalkerBehavior.BEHIND : StalkerBehavior.FAR;
    }
}
