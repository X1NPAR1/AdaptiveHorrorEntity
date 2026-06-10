package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
        return ctx.underground ? 2.6 : 0.9; // far more block-breaking / torch-snuffing underground
    }

    @Override
    public void execute(EventContext ctx) {
        switch (ctx.random.nextInt(5)) {
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
            case 3 -> {
                if (!hauntDoorLoop(ctx)) { // the door that won't stop swinging
                    breakRandomBlock(ctx);
                }
            }
            default -> placeSkull(ctx);
        }
    }

    private static boolean toggleNearestDoor(EventContext ctx) {
        final BlockPos door = findNearestDoor(ctx);
        if (door == null) {
            return false;
        }
        toggleDoorAt(ctx.level, door);
        return true;
    }

    /**
     * The cinematic beat: a nearby door swings open and shut on its own, 2-4 times, each swing a beat
     * apart with its real creak. Built from per-player {@link ScheduledAction}s so it plays out over a
     * few seconds instead of all at once, and each step re-reads the door (so it no-ops if it's gone).
     */
    private static boolean hauntDoorLoop(EventContext ctx) {
        final BlockPos door = findNearestDoor(ctx);
        if (door == null) {
            return false;
        }
        final ServerLevel level = ctx.level;
        final int swings = 2 + ctx.random.nextInt(3); // 2-4 swings
        long delay = 0L;
        for (int i = 0; i < swings; i++) {
            delay += 8L + ctx.random.nextInt(9); // ~0.4-0.85s between swings
            final long fire = level.getGameTime() + delay;
            ctx.state.scheduled.add(new ScheduledAction(fire, () -> toggleDoorAt(level, door)));
        }
        return true;
    }

    private static BlockPos findNearestDoor(EventContext ctx) {
        final BlockPos origin = ctx.player.blockPosition();
        BlockPos nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RADIUS, -3, -RADIUS),
                origin.offset(RADIUS, 3, RADIUS))) {
            if (ctx.level.getBlockState(pos).getBlock() instanceof DoorBlock) {
                final double d = pos.distSqr(origin);
                if (d < nearestSq) {
                    nearestSq = d;
                    nearest = pos.immutable();
                }
            }
        }
        return nearest;
    }

    private static void toggleDoorAt(ServerLevel level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            return; // the door was removed between swings - just stop
        }
        final boolean willOpen = !state.getValue(DoorBlock.OPEN);
        level.setBlock(pos, state.setValue(DoorBlock.OPEN, willOpen), 10);
        level.playSound(null, pos, willOpen ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
                SoundSource.BLOCKS, 1.0F, 1.0F);
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
