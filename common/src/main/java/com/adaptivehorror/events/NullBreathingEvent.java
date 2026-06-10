package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #13 - "something is breathing on my neck". A slow, heavy breathing rises directly
 * behind the player - positioned just over their shoulder so it pans correctly as they turn - growing
 * closer over a few breaths before falling silent. There is never anything to find.
 */
public final class NullBreathingEvent implements HorrorEvent {

    private static final String[] BREATHS = {
            "minecraft:entity.warden.nearby_close",
            "minecraft:entity.warden.nearby_closer",
            "minecraft:entity.warden.heartbeat"
    };

    @Override
    public String id() {
        return "null_breathing";
    }

    @Override
    public int minDay() {
        return 3;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 2.0;
    }

    @Override
    public void execute(EventContext ctx) {
        final long start = ctx.level.getGameTime();
        final int breaths = 4 + ctx.random.nextInt(3);
        for (int i = 0; i < breaths; i++) {
            final float vol = 0.7F + 0.06F * i;
            final long fire = start + (long) i * 22L; // ~1.1s per breath
            ctx.state.scheduled.add(new ScheduledAction(fire, () -> {
                // Just behind the player's current facing, over the shoulder.
                final Vec3 back = ctx.player.getLookAngle().scale(-1.2);
                final Vec3 pos = ctx.player.getEyePosition().add(back.x, 0.0, back.z);
                final String breath = BREATHS[ctx.random.nextInt(BREATHS.length)];
                HorrorNet.sendSoundAt(ctx.player, breath, pos, vol, 0.7F);
            }));
        }
    }
}
