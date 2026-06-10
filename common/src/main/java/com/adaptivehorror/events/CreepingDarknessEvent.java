package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * New paranoia beat #10 - "the dark closes in". The light is swallowed: a pulsing Darkness wraps the
 * player while a low drone rises and a heavy vignette presses in from the edges. Not a jumpscare - a
 * slow, smothering dread that lifts as suddenly as it came.
 */
public final class CreepingDarknessEvent implements HorrorEvent {

    @Override
    public String id() {
        return "creeping_darkness";
    }

    @Override
    public int minDay() {
        return 5;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.cameraEffects;
    }

    @Override
    public double weight(EventContext ctx) {
        return ctx.underground ? 2.0 : 1.3;
    }

    @Override
    public void execute(EventContext ctx) {
        final int ticks = 100 + ctx.random.nextInt(80); // 5-9s
        ctx.player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0, false, false, true));
        HorrorNet.sendVignettePulse(ctx.player, ticks);
        HorrorNet.sendSound2D(ctx.player, "scary_ambient", 0.8F, 0.7F);
    }
}
