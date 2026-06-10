package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;

/**
 * New paranoia beat #11 - "am I bleeding?". The screen floods red from the edges with a wet hurt-sound,
 * as if the player has just been wounded - but no damage is dealt and there is nothing there. Pure
 * false alarm, and all the worse for it.
 */
public final class BloodScreenEvent implements HorrorEvent {

    @Override
    public String id() {
        return "blood_screen";
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
        return 1.2;
    }

    @Override
    public void execute(EventContext ctx) {
        HorrorNet.sendVignettePulse(ctx.player, 30 + ctx.random.nextInt(20));
        HorrorNet.sendSound2D(ctx.player, "minecraft:entity.player.hurt", 1.0F, 0.8F);
    }
}
