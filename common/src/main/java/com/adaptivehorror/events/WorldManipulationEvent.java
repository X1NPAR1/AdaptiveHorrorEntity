package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Quiet, "impossible" tampering with the surroundings. One of: a door swinging open/shut on its own,
 * a block crumbling with no one there, a torch snuffing out (the dark creeps in), or a skull quietly
 * appearing on the ground nearby. Subtle and rare, so it reads as reality glitching.
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
        return 0.9;
    }

    @Override
    public void execute(EventContext ctx) {
        switch (ctx.random.nextInt(4)) {
            case 0 -> {
                if (!toggleNearestDoor(ctx)) {
                    breakRandomBlock(ctx);
                }
            }
            case 1 -> breakRandomBlock(ctx);
            case 2 -> {
                if (!snuffNearestTorch(ctx)) {
                    breakRandomBlock(ctx);
                }
            }
            default -> placeSkull(ctx);
        }
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
        ctx.level.setBlock(nearest, nearestState.setValue(DoorBlock.OPEN, !nearestState.getValue(DoorBlock.OPEN)), 10);
        return true;
    }

    private static boolean snuffNearestTorch(EventContext ctx) {
        final BlockPos origin = ctx.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RADIUS, -3, -RADIUS),
                origin.offset(RADIUS, 3, RADIUS))) {
            if (ctx.level.getBlockState(pos).getBlock() instanceof TorchBlock) {
                ctx.level.destroyBlock(pos.immutable(), false); // the light goes out
                return true;
            }
        }
        return false;
    }

    private static void placeSkull(EventContext ctx) {
        final BlockPos origin = ctx.player.blockPosition();
        for (int attempt = 0; attempt < 16; attempt++) {
            final BlockPos pos = origin.offset(
                    ctx.random.nextInt(7) - 3, ctx.random.nextInt(3) - 1, ctx.random.nextInt(7) - 3);
            if (ctx.level.getBlockState(pos).isAir() && ctx.level.getBlockState(pos.below()).isSolid()) {
                ctx.level.setBlock(pos.immutable(), Blocks.SKELETON_SKULL.defaultBlockState()
                        .setValue(SkullBlock.ROTATION, ctx.random.nextInt(16)), 3);
                return;
            }
        }
    }

    private static void breakRandomBlock(EventContext ctx) {
        final BlockPos origin = ctx.player.blockPosition();
        for (int attempt = 0; attempt < 20; attempt++) {
            final BlockPos pos = origin.offset(
                    ctx.random.nextInt(RADIUS * 2 + 1) - RADIUS,
                    ctx.random.nextInt(5) - 2,
                    ctx.random.nextInt(RADIUS * 2 + 1) - RADIUS);
            final BlockState state = ctx.level.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(ctx.level, BlockPos.ZERO) >= 0.0F
                    && state.getFluidState().isEmpty() && !state.hasBlockEntity()) {
                ctx.level.destroyBlock(pos.immutable(), false);
                return;
            }
        }
    }
}
