package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.FakePlayerEntity;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * New paranoia beat #7 - "it has a name now". A nearby ordinary mob is quietly renamed - to {@code
 * null}, or to the player's own name - with its tag made visible. The next time the player glances at
 * that cow or zombie, it is wearing a name that should not be there.
 */
public final class MobRenameEvent implements HorrorEvent {

    private static final double RADIUS = 16.0;

    @Override
    public String id() {
        return "mob_rename";
    }

    @Override
    public int minDay() {
        return 4;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.worldManipulation;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.0;
    }

    @Override
    public void execute(EventContext ctx) {
        final AABB box = ctx.player.getBoundingBox().inflate(RADIUS);
        final List<Mob> mobs = ctx.level.getEntitiesOfClass(Mob.class, box,
                m -> !(m instanceof StalkerEntity) && !(m instanceof FakePlayerEntity)
                        && m.getCustomName() == null && m.isAlive());
        if (mobs.isEmpty()) {
            return;
        }
        final Mob target = mobs.get(ctx.random.nextInt(mobs.size()));
        final String name = ctx.random.nextBoolean() ? "null" : ctx.player.getGameProfile().getName();
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
    }
}
