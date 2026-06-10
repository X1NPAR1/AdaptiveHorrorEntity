package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * New paranoia beat #22 - "something just snapped at me". A guttural growl and the wet snap of jaws
 * right at the player's back, a lurch of slowness as if they flinched away, and the screen flushes red
 * - the body's certainty that it was just bitten. No damage, no creature. Late-game.
 */
public final class PhantomBiteEvent implements HorrorEvent {

    @Override
    public String id() {
        return "phantom_bite";
    }

    @Override
    public int minDay() {
        return 6;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return ctx.underground ? 1.4 : 1.0;
    }

    @Override
    public void execute(EventContext ctx) {
        HorrorNet.sendSound2D(ctx.player, "minecraft:entity.wolf.growl", 1.0F, 0.6F);
        HorrorNet.sendSound2D(ctx.player, "minecraft:entity.player.attack.sweep", 1.0F, 0.7F);
        HorrorNet.sendVignettePulse(ctx.player, 25);
        ctx.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 2, false, false, true));
    }
}
