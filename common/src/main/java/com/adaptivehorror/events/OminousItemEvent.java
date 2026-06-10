package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * New paranoia beat #21 - "who left this here?". A grim little token - a skull, a bone, a withered
 * vine - is sitting on the ground at the player's feet, as if placed there for them. It lingers a few
 * seconds, just long enough to be seen and doubted, then is gone.
 */
public final class OminousItemEvent implements HorrorEvent {

    private static final net.minecraft.world.item.Item[] TOKENS = {
            Items.WITHER_SKELETON_SKULL,
            Items.SKELETON_SKULL,
            Items.BONE,
            Items.WITHER_ROSE,
            Items.ROTTEN_FLESH
    };

    @Override
    public String id() {
        return "ominous_item";
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
        return 0.9;
    }

    @Override
    public void execute(EventContext ctx) {
        final ItemStack stack = new ItemStack(TOKENS[ctx.random.nextInt(TOKENS.length)]);
        final ItemEntity item = new ItemEntity(ctx.level,
                ctx.player.getX(), ctx.player.getY() + 0.2, ctx.player.getZ(), stack);
        item.setDeltaMovement(0, 0, 0);
        item.setPickUpDelay(Integer.MAX_VALUE); // it cannot be picked up - only seen
        item.setUnlimitedLifetime();
        if (!ctx.level.addFreshEntity(item)) {
            return;
        }
        final UUID id = item.getUUID();
        ctx.state.scheduled.add(new ScheduledAction(ctx.level.getGameTime() + 120L, () -> {
            final var e = ctx.level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }));
    }
}
