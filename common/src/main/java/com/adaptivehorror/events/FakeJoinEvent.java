package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A stranger "joins the game" - then leaves a handful of seconds later. No such player exists; it's
 * just the vanilla join/leave lines with an unsettling name, enough to make a lone player freeze and
 * wonder who is in their world.
 */
public final class FakeJoinEvent implements HorrorEvent {

    private static final String[] NAMES = {
            "null", "Herobrine", "entity_303", "him", "NULL", "??????", "010110", "no_name", "Steve"
    };

    @Override
    public String id() {
        return "fake_join";
    }

    @Override
    public int minDay() {
        return 4;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.chatMessages;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.6;
    }

    @Override
    public void execute(EventContext ctx) {
        final ServerPlayer player = ctx.player;
        final String name = NAMES[ctx.random.nextInt(NAMES.length)];
        player.sendSystemMessage(
                Component.translatable("multiplayer.player.joined", Component.literal(name))
                        .withStyle(ChatFormatting.YELLOW));
        final long leaveDelay = 60L + ctx.random.nextInt(140); // 3-10s later
        ctx.state.scheduled.add(new ScheduledAction(ctx.level.getGameTime() + leaveDelay,
                () -> player.sendSystemMessage(
                        Component.translatable("multiplayer.player.left", Component.literal(name))
                                .withStyle(ChatFormatting.YELLOW))));
    }
}
