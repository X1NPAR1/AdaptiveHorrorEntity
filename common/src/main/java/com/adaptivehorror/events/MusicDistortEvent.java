package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;

/**
 * Silences/interrupts the game's music for a spell. The engine cannot pitch-shift or reverse vanilla
 * music tracks, so the client stops the current track for the duration - which reads as the music
 * cutting out unnaturally. Rare; never overused.
 */
public final class MusicDistortEvent implements HorrorEvent {

    @Override
    public String id() {
        return "music_distort";
    }

    @Override
    public int minDay() {
        return 2;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.musicDistortion;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.0;
    }

    @Override
    public void execute(EventContext ctx) {
        final int duration = (8 + ctx.random.nextInt(12)) * 20; // 8-20s
        HorrorNet.sendMusicDistort(ctx.player, duration);
    }
}
