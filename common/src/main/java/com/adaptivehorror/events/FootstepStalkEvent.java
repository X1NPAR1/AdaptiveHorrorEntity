package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Footsteps that approach from directly behind the player, getting closer over a few seconds and then
 * stopping dead. Each step is re-aimed at fire time, so they stay behind the player even as they spin
 * to look. The silence at the end is the worst part.
 */
public final class FootstepStalkEvent implements HorrorEvent {

    private static final int STEPS = 6;
    private static final int STEP_TICKS = 9;

    @Override
    public String id() {
        return "footsteps";
    }

    @Override
    public int minDay() {
        return 1;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.6;
    }

    @Override
    public void execute(EventContext ctx) {
        final ServerPlayer player = ctx.player;
        final long now = ctx.level.getGameTime();
        for (int i = 0; i < STEPS; i++) {
            final int step = i;
            ctx.state.scheduled.add(new ScheduledAction(now + (long) step * STEP_TICKS, () -> {
                final double dist = 9.0 - step * 1.25;           // 9 -> ~2.75 blocks
                final Vec3 look = player.getLookAngle();
                final Vec3 horiz = new Vec3(look.x, 0, look.z).normalize().scale(dist);
                final Vec3 behind = player.getEyePosition().subtract(horiz);
                HorrorNet.sendSoundAt(player, "minecraft:block.gravel.step", behind,
                        0.95F, 0.85F + step * 0.03F);
            }));
        }
        // ... then nothing but a chill.
        ctx.state.scheduled.add(new ScheduledAction(now + (long) STEPS * STEP_TICKS + 8L,
                () -> HorrorNet.sendVignettePulse(player, 22)));
    }
}
