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

    /** Forty additional whispers, kept inline (the mod's chat is Turkish by design). */
    private static final String[] LINES = {
            "neredesin", "seni buldum", "kapını çaldım", "duydun mu beni", "yaklaşıyorum",
            "arkana bakma", "ışıkları açık bırak", "uyuduğunda geleceğim", "seni izliyorum hâlâ",
            "bu gece olmaz", "yalnız mısın", "kapıyı kilitle", "perdeyi kapatma",
            "seni göremiyorum ama buradayım", "nefes alma", "sessiz ol", "onlar geldi",
            "biz çoğuz", "geri dön", "evine git", "burada kal", "çok geç oldu",
            "beni unutma", "hatırlıyor musun", "adını biliyorum", "yüzünü gördüm",
            "tam arkandayım", "pencereye bakma", "tavana bak", "zeminin altındayım",
            "yatağının yanındayım", "elini ver", "benimle gel", "acıktım",
            "daha yakına gel", "oyun bitti", "sıra sende", "son kez gülümse",
            "kaçamazsın", "seni hep izledim"
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
        final float roll = ctx.random.nextFloat();
        final Component message;
        if (roll < 0.12F) {
            message = Component.literal(ctx.player.getGameProfile().getName());        // it knows your name
        } else if (roll < 0.45F) {
            message = Component.translatable(KEYS[ctx.random.nextInt(KEYS.length)]);   // localised classics
        } else {
            message = Component.literal(LINES[ctx.random.nextInt(LINES.length)]);      // the wider whisper pool
        }
        sendNullChat(ctx.player, message);
    }

    /** Broadcasts a {@code <null> message} line, in white - the shared null-chat style. */
    public static void sendNullChat(ServerPlayer player, Component message) {
        player.sendSystemMessage(Component.literal("<null> ").append(message).withStyle(ChatFormatting.WHITE));
    }
}
