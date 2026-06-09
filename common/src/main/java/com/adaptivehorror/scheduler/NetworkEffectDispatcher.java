package com.adaptivehorror.scheduler;

import com.adaptivehorror.network.HorrorNet;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/**
 * The production {@link EffectDispatcher}: turns scheduler decisions into real S2C effect packets.
 * Installed over the logging stub during init via {@link EffectDispatcher.Holder#set}.
 */
public final class NetworkEffectDispatcher implements EffectDispatcher {

    private static final int TRAVEL_SOUND_COUNT = 2; // travel1, travel2 ship with audio
    private static final int JUMPSCARE_DURATION_TICKS = 18; // ~0.9s, brief interruption

    private final Random random = new Random();

    @Override
    public void playTravelSound(ServerPlayer player) {
        final int idx = 1 + random.nextInt(TRAVEL_SOUND_COUNT);
        HorrorNet.sendSound2D(player, "travel" + idx, 0.8F, 1.0F);
    }

    @Override
    public void travelJumpscare(ServerPlayer player) {
        // Uses the dedicated travel jumpscare image (jumpscare120) with a random horror sting.
        final int sound = 1 + random.nextInt(4);
        HorrorNet.sendJumpscare(player, 120, sound, JUMPSCARE_DURATION_TICKS);
    }
}
