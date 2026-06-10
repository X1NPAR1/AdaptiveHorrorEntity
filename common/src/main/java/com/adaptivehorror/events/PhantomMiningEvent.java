package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #5 - "someone is digging toward you". Down in the caves, the unmistakable sound of
 * a pick biting stone starts somewhere out in the dark and gets closer, block by block - then stops
 * dead, right next to you. Nothing was ever mined. Underground only.
 */
public final class PhantomMiningEvent implements HorrorEvent {

    @Override
    public String id() {
        return "phantom_mining";
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
        return ctx.underground ? 4.0 : 0.0; // caves only
    }

    @Override
    public void execute(EventContext ctx) {
        final long start = ctx.level.getGameTime();
        final double angle = ctx.random.nextDouble() * Math.PI * 2.0;
        final int steps = 6;
        for (int i = 0; i < steps; i++) {
            final double dist = 12.0 - i * 1.8; // closes from ~12 blocks to ~1
            final Vec3 pos = ctx.player.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
            final long fire = start + (long) i * 14L;
            ctx.state.scheduled.add(new ScheduledAction(fire, () -> {
                HorrorNet.sendSoundAt(ctx.player, "minecraft:block.stone.break", pos, 0.9F, 0.8F);
                HorrorNet.sendSoundAt(ctx.player, "minecraft:block.stone.hit", pos, 0.7F, 0.7F);
            }));
        }
    }
}
