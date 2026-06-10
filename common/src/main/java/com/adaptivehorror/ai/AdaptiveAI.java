package com.adaptivehorror.ai;

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
