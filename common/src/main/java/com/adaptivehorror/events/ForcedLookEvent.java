package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;

/**
 * "Something turns your head." For a couple of seconds the view is wrenched away to a succession of
 * disorienting directions while the camera shakes - the player fights a hand that isn't there. The
 * whole effect is timer-bounded on the client (see {@link com.adaptivehorror.client.ClientHorrorManager}),
 * so control always returns; it can never permanently break the camera. Intensity and duration scale
 * with the day.
 */
public final class ForcedLookEvent implements HorrorEvent {

    @Override
    public String id() {
        return "forced_look";
    }

    @Override
    public int minDay() {
        return 4;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.cameraEffects;
    }

    @Override
    public double weight(EventContext ctx) {
        return ctx.underground ? 1.2 : 0.7; // a touch more likely in the close dark
    }

    @Override
    public void execute(EventContext ctx) {
        // 1.5-3s, longer and harsher as the days mount (intensity ~0.2..1+).
        final int duration = 30 + ctx.random.nextInt(30) + (int) Math.min(20, ctx.intensity * 15);
        final float intensity = (float) Math.min(2.5, 0.8 + ctx.intensity);
        HorrorNet.sendForcedLook(ctx.player, duration, intensity);
        HorrorNet.sendSound2D(ctx.player, "iseeyou", 0.7F, 0.9F);
    }
}
