package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #20 - "I can see my breath". A puff of cold white fog blooms in the air right in
 * front of the player's face, as if the temperature just dropped - or as if something unseen just
 * exhaled where they are about to walk. Visible to nearby players too (real server particles).
 */
public final class BreathFogEvent implements HorrorEvent {

    @Override
    public String id() {
        return "breath_fog";
    }

    @Override
    public int minDay() {
        return 3;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.worldManipulation;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.3;
    }

    @Override
    public void execute(EventContext ctx) {
        final Vec3 look = ctx.player.getLookAngle();
        final Vec3 at = ctx.player.getEyePosition().add(look.x * 1.2, look.y * 1.2 - 0.2, look.z * 1.2);
        // A small cold cloud, drifting slightly.
        ctx.level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 18, 0.25, 0.2, 0.25, 0.01);
        ctx.level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.2, 0.15, 0.2, 0.0);
        HorrorNet.sendSound2D(ctx.player, "minecraft:ambient.cave", 0.9F, 0.6F);
    }
}
