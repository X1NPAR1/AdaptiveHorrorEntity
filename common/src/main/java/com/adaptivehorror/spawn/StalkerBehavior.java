package com.adaptivehorror.spawn;

import java.util.Random;

/**
 * The archetype a freshly-spawned stalker commits to for this encounter. Picking one at spawn is what
 * makes the entity feel unpredictable rather than a single scripted routine.
 */
public enum StalkerBehavior {
    /** Vanishes when approached and applies a brief status effect (the classic encounter). */
    EFFECT,
    /** Simply vanishes when approached - no effect, pure doubt. */
    VANISH,
    /** Sprints at the player and, on reaching ~3 blocks, jumpscares and kills them. Rare, lethal. */
    RUSH,
    /** Spawns close behind and silently watches; the moment the player looks at it, it does one of
     *  the above (vanish / effect / rush). */
    WATCH;

    /** Weighted random pick. RUSH (lethal) is deliberately rare - the entity rarely kills. */
    public static StalkerBehavior random(Random random) {
        final int roll = random.nextInt(100);
        if (roll < 47) {
            return EFFECT;   // 47%
        } else if (roll < 72) {
            return VANISH;   // 25%
        } else if (roll < 97) {
            return WATCH;    // 25%
        } else {
            return RUSH;     // 3% - the only routine that always kills
        }
    }
}
