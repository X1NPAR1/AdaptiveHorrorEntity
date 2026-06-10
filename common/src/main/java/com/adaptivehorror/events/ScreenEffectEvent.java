package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Subtle camera/screen disturbances: a brief blackout, a vignette pulse, or a glitch flicker. Each
 * is short and easy to dismiss as imagination - exactly the intended "did the screen just...?" beat.
 */
public final class ScreenEffectEvent implements HorrorEvent {

    @Override
    public String id() {
        return "screen_effect";
    }

    @Override
    public int minDay() {
        return 1;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.cameraEffects;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.5;
    }

    @Override
    public void execute(EventContext ctx) {
        final ServerPlayer p = ctx.player;
        switch (ctx.random.nextInt(4)) {
            case 0:
                HorrorNet.sendBlackout(p, 3 + ctx.random.nextInt(4)); // very brief
                break;
            case 1:
                HorrorNet.sendVignettePulse(p, 20 + ctx.random.nextInt(20));
                break;
            case 2:
                HorrorNet.sendGlitch(p, 3 + ctx.random.nextInt(5));
                break;
            default:
                // Real world-camera shake: a clearly-felt tremor (degrees of jitter).
                HorrorNet.sendCameraShake(p, 20 + ctx.random.nextInt(20), 3.0F + ctx.random.nextFloat() * 3.0F);
                break;
        }
    }
}
