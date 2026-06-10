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

    /** Blocks below the local surface a player must be (and skyless) to count as underground. */
    private static final int DEPTH_THRESHOLD = 6;
    /**
     * Once a player is this far below the surface they count as underground <em>even if</em> a thin
     * shaft or ravine lets them technically see sky - otherwise a player standing at Y=-7 under a 1-wide
     * opening was wrongly treated as "surface" and got a null spawned up at the surface (Y≈72). This is
     * the fix for the "null spawning far above an underground player" bug.
     */
    private static final int DEEP_THRESHOLD = 16;

    private Locations() {
    }

    public static boolean isUnderground(Level level, BlockPos pos) {
        final int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        final int depth = surface - pos.getY();
        if (depth < DEPTH_THRESHOLD) {
            return false;                 // at or near the local surface (a house, a hill) - not a cave
        }
        if (depth >= DEEP_THRESHOLD) {
            return true;                  // genuinely deep: underground regardless of any sky shaft
        }
        return !level.canSeeSky(pos);     // shallow: only underground if actually enclosed
    }

    public static boolean isUnderground(ServerPlayer player) {
        return isUnderground(player.level(), player.blockPosition());
    }
}
