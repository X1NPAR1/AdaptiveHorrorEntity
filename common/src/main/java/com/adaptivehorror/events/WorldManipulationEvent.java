package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Quiet, "impossible" tampering with the player's surroundings. The implemented manifestation closes
 * an open door near the player - subtle, reversible, and deeply unsettling when noticed. Kept to a
 * tiny weight so it feels like a glitch in reality rather than a mechanic.
 *
 * <p>Additional manifestations from the design (rotating a chest, restoring a broken block, moving
 * animals) plug in here as further cases without touching the scheduler.
 */
public final class WorldManipulationEvent implements HorrorEvent {

    private static final int RADIUS = 6;

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
        return 0.3; // should feel impossible - very low
    }

    @Override
    public void execute(EventContext ctx) {
        final BlockPos origin = ctx.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RADIUS, -3, -RADIUS),
                origin.offset(RADIUS, 3, RADIUS))) {
            final BlockState state = ctx.level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) {
                // Closed silently - a silent door is far more unsettling than an audible one.
                ctx.level.setBlock(pos.immutable(), state.setValue(DoorBlock.OPEN, false), 10);
                return; // one quiet act is enough
            }
        }
    }
}
