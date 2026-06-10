package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import com.adaptivehorror.spawn.SpawnLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * New paranoia beat #6 - "the peek". A null flickers into view at the edge of sight for barely a
 * second - just long enough to register out of the corner of your eye - and is gone before you can
 * turn. No strike, no sound but a faint vignette pulse: pure "did I just see that?".
 */
public final class NullPeekEvent implements HorrorEvent {

    @Override
    public String id() {
        return "null_peek";
    }

    @Override
    public int minDay() {
        return 3;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.shadowEntities;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.5;
    }

    @Override
    public void execute(EventContext ctx) {
        final ServerLevel level = ctx.level;
        final BlockPos spot = SpawnLocator.findSpawn(ctx.player, ctx.random, 10, 18);
        if (spot == null) {
            return;
        }
        final StalkerEntity peek = ModEntities.STALKER.create(level);
        if (peek == null) {
            return;
        }
        peek.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
        peek.setNightForm(!level.isDay() || com.adaptivehorror.util.Locations.isUnderground(ctx.player));
        faceTowards(peek, ctx.player.position());
        if (!level.addFreshEntity(peek)) {
            return;
        }
        HorrorNet.sendVignettePulse(ctx.player, 12);
        final UUID id = peek.getUUID();
        final int life = 12 + ctx.random.nextInt(13); // 0.6-1.2s
        ctx.state.scheduled.add(new ScheduledAction(level.getGameTime() + life, () -> {
            final Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }));
    }

    private static void faceTowards(StalkerEntity stalker, Vec3 target) {
        final Vec3 s = stalker.position();
        final float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - s.x), target.z - s.z));
        stalker.setYRot(yaw);
        stalker.setYHeadRot(yaw);
        stalker.setYBodyRot(yaw);
    }
}
