package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #2 - "something screamed, far away". A muffled, distant scream drifts in from a
 * random direction tens of blocks off. Too far to be a threat, close enough to know you are not alone.
 */
public final class DistantScreamEvent implements HorrorEvent {

    private static final String[] SCREAMS = {
            "minecraft:entity.ghast.scream",
            "minecraft:entity.player.hurt",
            "minecraft:entity.ravager.roar"
    };

    @Override
    public String id() {
        return "distant_scream";
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
        return 1.6;
    }

    @Override
    public void execute(EventContext ctx) {
        final double angle = ctx.random.nextDouble() * Math.PI * 2.0;
        final double dist = 25.0 + ctx.random.nextDouble() * 25.0;
        final Vec3 pos = ctx.player.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
        final String scream = SCREAMS[ctx.random.nextInt(SCREAMS.length)];
        HorrorNet.sendSoundAt(ctx.player, scream, pos, 1.0F, 0.6F + ctx.random.nextFloat() * 0.2F);
    }
}
