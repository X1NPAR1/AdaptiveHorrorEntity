package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * null counts down in chat - {@code <null> 3 ... 2 ... 1 ...} a second apart - and then the jumpscare
 * lands. The dread of watching it tick down is the whole point. Rare and late-game.
 */
public final class CountdownEvent implements HorrorEvent {

    @Override
    public String id() {
        return "countdown";
    }

    @Override
    public int minDay() {
        return 5;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.jumpscares && config.features.chatMessages;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.35;
    }

    @Override
    public void execute(EventContext ctx) {
        final ServerPlayer player = ctx.player;
        final long now = ctx.level.getGameTime();
        ctx.state.scheduled.add(new ScheduledAction(now, () -> ChatMessageEvent.sendNullChat(player, Component.literal("3"))));
        ctx.state.scheduled.add(new ScheduledAction(now + 20L, () -> ChatMessageEvent.sendNullChat(player, Component.literal("2"))));
        ctx.state.scheduled.add(new ScheduledAction(now + 40L, () -> ChatMessageEvent.sendNullChat(player, Component.literal("1"))));
        ctx.state.scheduled.add(new ScheduledAction(now + 58L,
                () -> HorrorNet.sendJumpscare(player, com.adaptivehorror.util.Jumpscares.randomImage(ctx.random), 1 + ctx.random.nextInt(4), 16)));
    }
}
