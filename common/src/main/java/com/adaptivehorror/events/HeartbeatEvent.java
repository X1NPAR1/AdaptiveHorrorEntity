package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;

/**
 * New paranoia beat #1 - "your own heart". A slow, heavy heartbeat rises out of nowhere for a few
 * thuds and fades. There is nothing to see; the player just suddenly hears their pulse, the body's own
 * fear response played back at them.
 */
public final class HeartbeatEvent implements HorrorEvent {

    @Override
    public String id() {
        return "heartbeat";
    }

    @Override
    public int minDay() {
        return 2;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return ctx.underground ? 3.0 : 2.0;
    }

    @Override
    public void execute(EventContext ctx) {
        final long start = ctx.level.getGameTime();
        final int beats = 5 + ctx.random.nextInt(4);
        for (int i = 0; i < beats; i++) {
            final long fire = start + (long) i * 16L; // ~0.8s apart, a resting pulse
            final float vol = 0.6F + 0.04F * i;       // creeps louder
            ctx.state.scheduled.add(new ScheduledAction(fire,
                    () -> HorrorNet.sendSound2D(ctx.player, "minecraft:entity.warden.heartbeat", vol, 0.9F)));
        }
    }
}
