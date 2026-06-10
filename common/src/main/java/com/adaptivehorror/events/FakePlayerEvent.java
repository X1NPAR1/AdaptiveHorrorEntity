package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.FakePlayerEntity;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.spawn.SpawnLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns a motionless copy of the player ~150 blocks out, facing away. It never moves and vanishes if
 * approached (see {@link FakePlayerEntity}). One of the most unsettling beats - "is that... me?".
 */
public final class FakePlayerEvent implements HorrorEvent {

    @Override
    public String id() {
        return "fake_player";
    }

    @Override
    public int minDay() {
        return 4;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.fakePlayers;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.5;
    }

    @Override
    public void execute(EventContext ctx) {
        // Reuse the peripheral spawn finder but at long range (~120-160 blocks).
        final BlockPos pos = SpawnLocator.findSpawn(ctx.player, ctx.random, 120, 160);
        if (pos == null) {
            return;
        }
        final FakePlayerEntity fake = ModEntities.FAKE_PLAYER.create(ctx.level);
        if (fake == null) {
            return;
        }
        // Face AWAY from the player: yaw toward the player + 180.
        final Vec3 p = ctx.player.position();
        final double dx = p.x - (pos.getX() + 0.5);
        final double dz = p.z - (pos.getZ() + 0.5);
        final float towardPlayer = (float) Math.toDegrees(Math.atan2(-dx, dz));
        final float away = towardPlayer + 180.0F;

        fake.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, away, 0.0F);
        fake.setYHeadRot(away);
        fake.yBodyRot = away;
        ctx.level.addFreshEntity(fake);
    }
}
