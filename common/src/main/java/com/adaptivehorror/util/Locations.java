package com.adaptivehorror.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Small spatial predicates shared by the spawn logic and the event roll. A player counts as
 * "underground" when they cannot see the sky and sit well below the local surface (so a house or a
 * tree canopy doesn't qualify - only real caves/tunnels).
 */
public final class Locations {

    /** Blocks below the surface a player must be (and skyless) to count as underground. */
    private static final int DEPTH_THRESHOLD = 6;

    private Locations() {
    }

    public static boolean isUnderground(Level level, BlockPos pos) {
        if (level.canSeeSky(pos)) {
            return false;
        }
        final int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        return pos.getY() <= surface - DEPTH_THRESHOLD;
    }

    public static boolean isUnderground(ServerPlayer player) {
        return isUnderground(player.level(), player.blockPosition());
    }
}
