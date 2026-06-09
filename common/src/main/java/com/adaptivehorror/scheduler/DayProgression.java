package com.adaptivehorror.scheduler;

import com.adaptivehorror.config.HorrorConfig;
import net.minecraft.world.level.Level;

/**
 * Translates the in-game day count into the escalation curve described by the design (rare sightings
 * on day 1, ramping to maximum intensity by day 10+). Pure functions - no state.
 */
public final class DayProgression {

    private DayProgression() {
    }

    /** 0-based in-game day index for the player's current world. */
    public static int dayOf(Level level) {
        return (int) (level.getDayTime() / 24000L);
    }

    /**
     * Intensity scalar in roughly [0.2, multiplier]. Ramps linearly from a low floor on day 0 to the
     * configured multiplier at {@code maxIntensityDay}, then holds. With progression disabled the
     * multiplier is returned flat.
     */
    public static double intensity(int day, HorrorConfig config) {
        if (!config.intensity.dayProgressionEnabled) {
            return config.intensity.multiplier;
        }
        final int cap = Math.max(1, config.intensity.maxIntensityDay);
        final double ramp = Math.min(1.0, day / (double) cap);
        final double floored = 0.2 + 0.8 * ramp;
        return floored * config.intensity.multiplier;
    }
}
