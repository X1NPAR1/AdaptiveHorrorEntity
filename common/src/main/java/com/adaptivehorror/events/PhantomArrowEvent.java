package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.world.phys.Vec3;

/**
 * New paranoia beat #19 - "something just flew past my head". The unmistakable shoot-and-whistle of a
 * projectile loosed from the dark, then the thud of it striking just past the player - but no arrow,
 * no shooter, no wound. Someone, somewhere, is taking aim.
 */
public final class PhantomArrowEvent implements HorrorEvent {

    @Override
    public String id() {
        return "phantom_arrow";
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
        return 1.4;
    }

    @Override
    public void execute(EventContext ctx) {
        final double angle = ctx.random.nextDouble() * Math.PI * 2.0;
        final Vec3 from = ctx.player.position().add(Math.cos(angle) * 8.0, 1.0, Math.sin(angle) * 8.0);
        final Vec3 past = ctx.player.position().add(-Math.cos(angle) * 2.0, 1.2, -Math.sin(angle) * 2.0);
        HorrorNet.sendSoundAt(ctx.player, "minecraft:entity.arrow.shoot", from, 1.0F, 1.0F);
        ctx.state.scheduled.add(new ScheduledAction(ctx.level.getGameTime() + 6L,
                () -> HorrorNet.sendSoundAt(ctx.player, "minecraft:entity.arrow.hit", past, 1.0F, 1.2F)));
    }
}
