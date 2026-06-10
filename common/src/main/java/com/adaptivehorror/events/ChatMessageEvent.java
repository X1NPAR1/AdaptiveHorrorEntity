package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * A whisper in chat that reads exactly like another player talking: {@code <null> ...} in plain
 * white. Localised content; extremely rare by weight, so when it happens it lands.
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
        sendNullChat(ctx, Component.translatable(KEYS[ctx.random.nextInt(KEYS.length)]));
    }

    /** Broadcasts a {@code <null> message} line, in white, to the player - the shared null-chat style. */
    public static void sendNullChat(EventContext ctx, Component message) {
        ctx.player.sendSystemMessage(
                Component.literal("<null> ").append(message).withStyle(ChatFormatting.WHITE));
    }
}
