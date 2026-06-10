package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #9 - "it's circling me". Slow, deliberate footsteps track a full circle around the
 * player, one step at a time, always just out of sight. The sound is positional, so the player can hear
 * exactly where it is - pacing around them - but there is never anything there.
 */
public final class CirclingFootstepsEvent implements HorrorEvent {

    private static final String[] STEPS = {
            "minecraft:block.gravel.step",
            "minecraft:block.wood.step",
            "minecraft:block.stone.step"
    };

    @Override
    public String id() {
        return "circling_footsteps";
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
        return 2.2;
    }

    @Override
    public void execute(EventContext ctx) {
        final long start = ctx.level.getGameTime();
        final String step = STEPS[ctx.random.nextInt(STEPS.length)];
        final double radius = 3.0 + ctx.random.nextDouble() * 2.0;
        final double startAngle = ctx.random.nextDouble() * Math.PI * 2.0;
        final boolean clockwise = ctx.random.nextBoolean();
        final int steps = 10 + ctx.random.nextInt(6); // a bit more than a full lap
        for (int i = 0; i < steps; i++) {
            final double angle = startAngle + (clockwise ? 1 : -1) * (Math.PI * 2.0 / 12.0) * i;
            final Vec3 pos = ctx.player.position().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            final long fire = start + (long) i * 9L; // ~0.45s per step, an unhurried pace
            ctx.state.scheduled.add(new ScheduledAction(fire,
                    () -> HorrorNet.sendSoundAt(ctx.player, step, pos, 0.7F, 0.9F)));
        }
    }
}
