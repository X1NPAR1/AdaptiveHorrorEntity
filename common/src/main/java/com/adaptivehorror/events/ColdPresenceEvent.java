package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * New paranoia beat #4 - "something cold just passed through you". A sudden chill: the player slows for
 * a moment, the world dims, and a breath that is not theirs sounds at their shoulder.
 */
public final class ColdPresenceEvent implements HorrorEvent {

    @Override
    public String id() {
        return "cold_presence";
    }

    @Override
    public int minDay() {
        return 2;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.8;
    }

    @Override
    public void execute(EventContext ctx) {
        ctx.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false, true));
        ctx.player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
        HorrorNet.sendSound2D(ctx.player, "minecraft:ambient.cave", 1.2F, 0.5F);
        HorrorNet.sendVignettePulse(ctx.player, 30);
    }
}
