package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A direct, localized whisper in chat ("I'm watching.", "Turn around.", ...). Messages use
 * translation keys so each player reads them in their own language. Occasionally the text is
 * rendered with the obfuscated style, reading as corrupted glyphs. Extremely rare by weight.
 */
public final class ChatMessageEvent implements HorrorEvent {

    private static final String[] KEYS = {
            "adaptivehorror.chat.watching",
            "adaptivehorror.chat.turn_around",
            "adaptivehorror.chat.not_alone",
            "adaptivehorror.chat.dont_sleep",
            "adaptivehorror.chat.too_late"
    };

    @Override
    public String id() {
        return "chat";
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
        return 0.5; // very rare
    }

    @Override
    public void execute(EventContext ctx) {
        MutableComponent message = Component.translatable(KEYS[ctx.random.nextInt(KEYS.length)]);
        message = message.withStyle(ChatFormatting.GRAY);
        if (ctx.random.nextFloat() < 0.25F) {
            message = message.withStyle(ChatFormatting.OBFUSCATED); // "corrupted letters"
        }
        ctx.player.sendSystemMessage(message);
    }
}
