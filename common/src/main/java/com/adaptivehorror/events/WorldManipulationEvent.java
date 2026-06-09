package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Quiet, "impossible" tampering with the player's surroundings. The implemented manifestation
 * <em>toggles</em> the nearest door (a closed door swings open, an open one shuts) and plays the
 * matching sound, so it is clearly perceptible. Kept to a small weight so it feels like a glitch in
 * reality rather than a mechanic.
 *
 * <p>Further manifestations from the design (rotating a chest, restoring a broken block, moving
 * animals) plug in here as additional cases without touching the scheduler.
 */
public final class WorldManipulationEvent implements HorrorEvent {

    private static final int RADIUS = 8;

    @Override
    public String id() {
        return "world_manipulation";
    }

    @Override
    public int minDay() {
        return 7;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.worldManipulation;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.4;
    }

    @Override
    public void execute(EventContext ctx) {
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
            return;
        }

        // Toggle the door silently - a door that opens or shuts on its own, without a sound, is far
        // more unsettling than an audible one. Flag 10 = send to clients + suppress neighbour updates.
        final boolean nowOpen = !nearestState.getValue(DoorBlock.OPEN);
        ctx.level.setBlock(nearest, nearestState.setValue(DoorBlock.OPEN, nowOpen), 10);
    }
}
