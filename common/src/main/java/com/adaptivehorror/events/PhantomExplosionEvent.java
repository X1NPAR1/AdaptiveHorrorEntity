package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #12 - "what was that?". A muffled explosion thuds somewhere out in the world, the
 * ground seems to shudder (a short camera shake) and the screen pulses - as if something just blew a
 * hole in reality nearby. Nothing is actually damaged.
 */
public final class PhantomExplosionEvent implements HorrorEvent {

    @Override
    public String id() {
        return "phantom_explosion";
    }

    @Override
    public int minDay() {
        return 5;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.0;
    }

    @Override
    public void execute(EventContext ctx) {
        final double angle = ctx.random.nextDouble() * Math.PI * 2.0;
        final double dist = 15.0 + ctx.random.nextDouble() * 20.0;
        final Vec3 pos = ctx.player.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
        HorrorNet.sendSoundAt(ctx.player, "minecraft:entity.generic.explode", pos, 1.0F, 0.5F);
        HorrorNet.sendCameraShake(ctx.player, 12, 1.4F);
        HorrorNet.sendVignettePulse(ctx.player, 14);
    }
}
