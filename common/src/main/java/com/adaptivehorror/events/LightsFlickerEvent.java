package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;

/**
 * New paranoia beat #3 - "the lights are failing". The screen stutters into darkness in short, uneven
 * blips, like a bulb on its last legs, with a glitch tear on each drop. Brief and disorienting, never
 * long enough to be a real blackout.
 */
public final class LightsFlickerEvent implements HorrorEvent {

    @Override
    public String id() {
        return "lights_flicker";
    }

    @Override
    public int minDay() {
        return 3;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.cameraEffects;
    }

    @Override
    public double weight(EventContext ctx) {
        return ctx.underground ? 2.4 : 1.4;
    }

    @Override
    public void execute(EventContext ctx) {
        final long start = ctx.level.getGameTime();
        final int flickers = 4 + ctx.random.nextInt(5);
        long t = 0L;
        for (int i = 0; i < flickers; i++) {
            t += 2L + ctx.random.nextInt(6);
            final long fire = start + t;
            ctx.state.scheduled.add(new ScheduledAction(fire, () -> {
                HorrorNet.sendBlackout(ctx.player, 2 + ctx.random.nextInt(3));
                HorrorNet.sendGlitch(ctx.player, 2);
            }));
        }
    }
}
