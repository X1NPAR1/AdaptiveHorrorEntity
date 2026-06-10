package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #18 - "something is walking above me". Slow, deliberate footsteps cross the
 * ceiling directly overhead - on the roof, in the rafters, just out of reach - even when there is
 * nothing up there at all. Especially unnerving underground or indoors.
 */
public final class FootstepsAboveEvent implements HorrorEvent {

    private static final String[] STEPS = {
            "minecraft:block.wood.step",
            "minecraft:block.stone.step",
            "minecraft:block.gravel.step"
    };

    @Override
    public String id() {
        return "footsteps_above";
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
        return 1.8;
    }

    @Override
    public void execute(EventContext ctx) {
        final long start = ctx.level.getGameTime();
        final String step = STEPS[ctx.random.nextInt(STEPS.length)];
        final int count = 5 + ctx.random.nextInt(4);
        final double dirX = ctx.random.nextDouble() - 0.5;
        final double dirZ = ctx.random.nextDouble() - 0.5;
        for (int i = 0; i < count; i++) {
            final double off = (i - count / 2.0) * 0.8; // walking across, overhead
            final Vec3 pos = ctx.player.position().add(dirX * off, 3.0 + ctx.random.nextDouble(), dirZ * off);
            final long fire = start + (long) i * 11L;
            ctx.state.scheduled.add(new ScheduledAction(fire,
                    () -> HorrorNet.sendSoundAt(ctx.player, step, pos, 0.8F, 0.85F)));
        }
    }
}
