package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Quiet, "impossible" tampering with the surroundings: toggling the nearest door (it swings open or
 * shut on its own) or breaking a random nearby block (it crumbles with no one there). Subtle and
 * rare, so it reads as reality glitching rather than a mechanic.
 */
public final class WorldManipulationEvent implements HorrorEvent {

    private static final int RADIUS = 8;

    @Override
    public String id() {
        return "world_manipulation";
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
        return 0.8;
    }

    @Override
    public void execute(EventContext ctx) {
        if (ctx.random.nextBoolean() && toggleNearestDoor(ctx)) {
            return;
        }
        breakRandomBlock(ctx);
    }

    private static boolean toggleNearestDoor(EventContext ctx) {
        final BlockPos origin = ctx.player.blockPosition();
        BlockPos nearest = null;
        double nearestSq = Double.MAX_VALUE;
        BlockState nearestState = null;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RADIUS, -3, -RADIUS),
                origin.offset(RADIUS, 3, RADIUS))) {
            final BlockState state = ctx.level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) {
                final double d = pos.distSqr(origin);
                if (d < nearestSq) {
                    nearestSq = d;
                    nearest = pos.immutable();
                    nearestState = state;
                }
            }
        }
        if (nearest == null) {
            return false;
        }
        final boolean nowOpen = !nearestState.getValue(DoorBlock.OPEN);
        ctx.level.setBlock(nearest, nearestState.setValue(DoorBlock.OPEN, nowOpen), 10);
        return true;
    }

    private static void breakRandomBlock(EventContext ctx) {
        final BlockPos origin = ctx.player.blockPosition();
        for (int attempt = 0; attempt < 20; attempt++) {
            final BlockPos pos = origin.offset(
                    ctx.random.nextInt(RADIUS * 2 + 1) - RADIUS,
                    ctx.random.nextInt(5) - 2,
                    ctx.random.nextInt(RADIUS * 2 + 1) - RADIUS);
            final BlockState state = ctx.level.getBlockState(pos);
            if (!isBreakable(state, ctx)) {
                continue;
            }
            // Crumbles with the break particles/sound, but drops nothing - it simply ceases to exist.
            ctx.level.destroyBlock(pos, false);
            return;
        }
    }

    private static boolean isBreakable(BlockState state, EventContext ctx) {
        // Skip air, unbreakable blocks, liquids and anything with a block entity (chests etc.).
        return !state.isAir()
                && state.getDestroySpeed(ctx.level, BlockPos.ZERO) >= 0.0F
                && state.getFluidState().isEmpty()
                && !state.hasBlockEntity();
    }
}
