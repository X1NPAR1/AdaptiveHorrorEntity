package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;

/**
 * The rare, memorable "something is deeply wrong" beat. Implemented as a sustained blackout with a
 * lingering whisper from nowhere - the player is left in the dark with the sense of being watched.
 * Probability is kept below 1% via a tiny weight and the day-10 gate.
 */
public final class GlobalEvent implements HorrorEvent {

    @Override
    public String id() {
        return "global";
    }

    @Override
    public int minDay() {
        return 10;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.globalEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.1; // extremely rare
    }

    @Override
    public void execute(EventContext ctx) {
        final int blackout = 30 + ctx.random.nextInt(30); // 1.5-3s of darkness
        HorrorNet.sendBlackout(ctx.player, blackout);
        HorrorNet.sendMusicDistort(ctx.player, blackout);
        // A whisper inside the dark.
        HorrorNet.sendSound2D(ctx.player, "iseeyou", 0.9F, 0.85F);
    }
}
