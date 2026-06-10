package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.world.item.ItemStack;

/**
 * New paranoia beat #17 - "did my items just move?". Two of the player's hotbar slots quietly trade
 * places. Nothing is taken, nothing is added - but the next time they reach for a tool it is not where
 * they left it, and they will never be quite sure they didn't do it themselves.
 */
public final class InventoryShuffleEvent implements HorrorEvent {

    @Override
    public String id() {
        return "inventory_shuffle";
    }

    @Override
    public int minDay() {
        return 5;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.worldManipulation;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.8;
    }

    @Override
    public void execute(EventContext ctx) {
        final var inv = ctx.player.getInventory();
        int a = ctx.random.nextInt(9);
        int b = ctx.random.nextInt(9);
        if (a == b) {
            b = (b + 1) % 9;
        }
        final ItemStack sa = inv.getItem(a);
        final ItemStack sb = inv.getItem(b);
        if (sa.isEmpty() && sb.isEmpty()) {
            return; // nothing to move - don't waste the beat
        }
        inv.setItem(a, sb);
        inv.setItem(b, sa);
    }
}
