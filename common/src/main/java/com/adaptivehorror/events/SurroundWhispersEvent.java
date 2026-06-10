package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #15 - "they're all around me". Whispers rise from every side at once - four points
 * of the compass, all at the same instant - so there is no one direction to turn toward. The player is
 * surrounded by things that are not there.
 */
public final class SurroundWhispersEvent implements HorrorEvent {

    @Override
    public String id() {
        return "surround_whispers";
    }

    @Override
    public int minDay() {
        return 4;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return ctx.underground ? 2.2 : 1.4;
    }

    @Override
    public void execute(EventContext ctx) {
        final double base = ctx.random.nextDouble() * Math.PI * 2.0;
        final double dist = 4.0 + ctx.random.nextDouble() * 3.0;
        for (int i = 0; i < 4; i++) {
            final double angle = base + i * (Math.PI / 2.0);
            final Vec3 pos = ctx.player.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
            HorrorNet.sendSoundAt(ctx.player, "iseeyou", pos, 0.7F, 0.85F + ctx.random.nextFloat() * 0.3F);
        }
    }
}
