package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;

/**
 * New paranoia beat #8 - "everything went quiet". The music and ambience cut out completely for a few
 * seconds - the unnatural, held-breath silence that says something is about to happen - and then a
 * single sharp sting breaks it.
 */
public final class SuddenSilenceEvent implements HorrorEvent {

    @Override
    public String id() {
        return "sudden_silence";
    }

    @Override
    public int minDay() {
        return 4;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.musicDistortion;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.2;
    }

    @Override
    public void execute(EventContext ctx) {
        final int silence = 60 + ctx.random.nextInt(40); // 3-5s of held breath
        HorrorNet.sendMusicDistort(ctx.player, silence);
        ctx.state.scheduled.add(new ScheduledAction(ctx.level.getGameTime() + silence, () -> {
            HorrorNet.sendSound2D(ctx.player, "minecraft:entity.lightning_bolt.thunder", 1.0F, 0.5F);
            HorrorNet.sendVignettePulse(ctx.player, 20);
        }));
    }
}
