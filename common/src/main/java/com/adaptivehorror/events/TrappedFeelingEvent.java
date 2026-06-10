package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * New paranoia beat #14 - "you can't leave". The world briefly snaps shut around the player: a wave of
 * blindness and a leaden slowness, a heavy vignette, and a single line in chat - {@code Çıkamazsın.} -
 * the unmistakable feeling of being held in place. Lifts after a couple of seconds.
 */
public final class TrappedFeelingEvent implements HorrorEvent {

    @Override
    public String id() {
        return "trapped_feeling";
    }

    @Override
    public int minDay() {
        return 6;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.cameraEffects;
    }

    @Override
    public double weight(EventContext ctx) {
        return (ctx.level.isDay() && !ctx.underground) ? 0.0 : 0.9; // night / underground only
    }

    @Override
    public void execute(EventContext ctx) {
        ctx.player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false, true));
        ctx.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3, false, false, true));
        HorrorNet.sendVignettePulse(ctx.player, 50);
        if (ctx.config.features.chatMessages) {
            ChatMessageEvent.sendNullChat(ctx.player, Component.translatable("adaptivehorror.chat.cant_leave"));
        }
    }
}
