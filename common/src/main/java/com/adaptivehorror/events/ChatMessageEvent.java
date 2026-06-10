package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A whisper in chat that reads exactly like another player talking: {@code <null> ...} in plain
 * white. Localised content, plus a rare line that simply says the player's own name - nothing is more
 * unsettling than the thing in the dark knowing who you are. Extremely rare by weight.
 */
public final class ChatMessageEvent implements HorrorEvent {

    private static final String[] KEYS = {
            "adaptivehorror.chat.watching",
            "adaptivehorror.chat.turn_around",
            "adaptivehorror.chat.not_alone",
            "adaptivehorror.chat.dont_sleep",
            "adaptivehorror.chat.too_late",
            "adaptivehorror.chat.behind_you",
            "adaptivehorror.chat.i_see_you",
            "adaptivehorror.chat.why"
    };

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public int minDay() {
        return 1;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.chatMessages;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.8;
    }

    @Override
    public void execute(EventContext ctx) {
        final Component message = ctx.random.nextFloat() < 0.25F
                ? Component.literal(ctx.player.getGameProfile().getName())   // it knows your name
                : Component.translatable(KEYS[ctx.random.nextInt(KEYS.length)]);
        sendNullChat(ctx.player, message);
    }

    /** Broadcasts a {@code <null> message} line, in white - the shared null-chat style. */
    public static void sendNullChat(ServerPlayer player, Component message) {
        player.sendSystemMessage(Component.literal("<null> ").append(message).withStyle(ChatFormatting.WHITE));
    }
}
