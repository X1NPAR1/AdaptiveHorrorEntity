package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * New paranoia beat #16 - "something is hunting me". The deep, sightless sounds of a thing tracking
 * prey: a sculk-like sonic warning rises, the world darkens, and an angry roar answers from nowhere -
 * the distinct, primal sense of being scented and pursued by something you cannot see. Late-game.
 */
public final class UnseenHunterEvent implements HorrorEvent {

    @Override
    public String id() {
        return "unseen_hunter";
    }

    @Override
    public int minDay() {
        return 7;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        if (ctx.level.isDay() && !ctx.underground) {
            return 0.0; // the hunt comes at night
        }
        return ctx.underground ? 1.6 : 1.0;
    }

    @Override
    public void execute(EventContext ctx) {
        final long start = ctx.level.getGameTime();
        ctx.player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
        HorrorNet.sendSound2D(ctx.player, "minecraft:event.mob_effect.darkness", 1.0F, 0.8F);
        ctx.state.scheduled.add(new ScheduledAction(start + 25L,
                () -> HorrorNet.sendSound2D(ctx.player, "minecraft:entity.warden.sonic_boom", 0.7F, 0.7F)));
        ctx.state.scheduled.add(new ScheduledAction(start + 55L, () -> {
            HorrorNet.sendSound2D(ctx.player, "minecraft:entity.warden.roar", 0.9F, 0.7F);
            HorrorNet.sendVignettePulse(ctx.player, 20);
        }));
    }
}
