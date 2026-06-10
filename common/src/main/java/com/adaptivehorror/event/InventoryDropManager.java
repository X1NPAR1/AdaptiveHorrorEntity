package com.adaptivehorror.event;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.DayProgression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.FluidState;

import java.util.Random;

/**
 * From a certain in-game day on, null periodically tries to wrench items out of the player's hands:
 * every {@code intervalSeconds}, with {@code chance}, it either flings the held stack or empties the
 * whole inventory onto the ground. Items are dropped (recoverable), not destroyed - the terror is in
 * watching your gear leap out of your control.
 */
public final class InventoryDropManager {

    private InventoryDropManager() {
    }

    public static void tick(ServerPlayer player, PlayerHorrorState state, HorrorConfig config, Random random) {
        final HorrorConfig.InventoryDrop cfg = config.inventoryDrop;
        if (!cfg.enabled || DayProgression.dayOf(player.level()) < cfg.minDay) {
            return;
        }
        final long now = player.level().getGameTime();
        if (state.nextInventoryDropTick == 0L) {
            state.nextInventoryDropTick = now + (long) cfg.intervalSeconds * 20L;
            return;
        }
        if (now < state.nextInventoryDropTick) {
            return;
        }
        state.nextInventoryDropTick = now + (long) cfg.intervalSeconds * 20L;

        // Context-sensitive chance: it strikes when a dropped inventory hurts most.
        double chance = cfg.chance;
        if (player.getY() < 0.0) {
            chance = Math.max(chance, cfg.chanceBelowZero);   // deep underground - 10%
        }
        if (nearLava(player, cfg.lavaSearchRadius)) {
            chance = Math.max(chance, cfg.chanceNearLava);    // next to lava - the cruelest moment
        }
        if (random.nextDouble() >= chance) {
            return;
        }
        if (random.nextBoolean()) {
            player.drop(true); // the entire held stack leaps out
        } else {
            player.getInventory().dropAll(); // everything spills onto the ground
        }
    }

    /** True if there is any lava within {@code radius} blocks of the player (cheap bounded scan). */
    private static boolean nearLava(ServerPlayer player, int radius) {
        final BlockPos origin = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            final FluidState fluid = player.level().getFluidState(pos);
            if (!fluid.isEmpty() && fluid.is(net.minecraft.tags.FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }
}
