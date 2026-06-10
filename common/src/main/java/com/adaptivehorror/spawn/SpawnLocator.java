package com.adaptivehorror.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.Random;

/**
 * Pure geometry for choosing a believable spawn position for the stalker. No side effects, never
 * spawns anything - it only returns a candidate (or {@code null}), which makes it trivially testable
 * and keeps lifecycle logic in {@link StalkerManager}.
 *
 * <p>Several placement <em>modes</em> back the adaptive AI: the default peripheral arc, a "behind the
 * player" mode (used when the player is AFK), and a "sky-lit" variant that only accepts open-sky
 * positions (used to manifest outside a camping player's shelter or at a cave mouth for a miner).
 */
public final class SpawnLocator {

    private static final double PERIPHERAL_MIN_ANGLE = 45.0;
    private static final double PERIPHERAL_MAX_ANGLE = 170.0;
    private static final double BEHIND_SPREAD = 25.0; // +/- degrees around dead-behind
    private static final int MAX_ATTEMPTS = 24;

    private SpawnLocator() {
    }

    /** Default: somewhere in the player's peripheral arc, sky not required. */
    @Nullable
    public static BlockPos findSpawn(ServerPlayer player, Random random, int minDistance, int maxDistance) {
        return findInArc(player, random, minDistance, maxDistance,
                PERIPHERAL_MIN_ANGLE, PERIPHERAL_MAX_ANGLE, false);
    }

    /** Peripheral, but only open-sky positions (outside a shelter / at a cave mouth). */
    @Nullable
    public static BlockPos findSkylit(ServerPlayer player, Random random, int minDistance, int maxDistance) {
        return findInArc(player, random, minDistance, maxDistance,
                PERIPHERAL_MIN_ANGLE, PERIPHERAL_MAX_ANGLE, true);
    }

    /** Directly behind the player (used when AFK) - close and unsettling. */
    @Nullable
    public static BlockPos findBehind(ServerPlayer player, Random random, int minDistance, int maxDistance) {
        return findInArc(player, random, minDistance, maxDistance,
                180.0 - BEHIND_SPREAD, 180.0 + BEHIND_SPREAD, false);
    }

    /**
     * Core search: sample angles in {@code [minAngle, maxAngle]} (degrees, measured from the player's
     * look vector, on a random side), pick a distance in range, resolve the surface, and validate a
     * clear standing spot. {@code requireSky} additionally restricts to positions that can see sky.
     */
    @Nullable
    private static BlockPos findInArc(ServerPlayer player, Random random, int minDistance, int maxDistance,
                                      double minAngle, double maxAngle, boolean requireSky) {
        final Level level = player.level();
        final Vec3 origin = player.position();
        final float yawRad = (float) Math.toRadians(player.getYRot());

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final double spread = minAngle + random.nextDouble() * (maxAngle - minAngle);
            final double side = random.nextBoolean() ? 1.0 : -1.0;
            final double angle = yawRad + Math.toRadians(spread * side);

            final double distance = minDistance + random.nextDouble() * Math.max(1, maxDistance - minDistance);
            final double dx = -Math.sin(angle) * distance;
            final double dz = Math.cos(angle) * distance;

            final int x = (int) Math.floor(origin.x + dx);
            final int z = (int) Math.floor(origin.z + dz);

            final BlockPos candidate = surfaceStandingPos(level, x, z);
            if (candidate == null || !isValidStanding(level, candidate)) {
                continue;
            }
            if (requireSky && !level.canSeeSky(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    @Nullable
    private static BlockPos surfaceStandingPos(Level level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return null; // never force-load chunks to place the entity
        }
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static boolean isValidStanding(Level level, BlockPos pos) {
        final boolean feetClear = level.getBlockState(pos).isAir();
        final boolean headClear = level.getBlockState(pos.above()).isAir();
        // Solid, non-fluid floor: never on or in water (no spawning inside/under water).
        final boolean groundSolid = level.getBlockState(pos.below()).isSolid()
                && level.getBlockState(pos.below()).getFluidState().isEmpty();
        return feetClear && headClear && groundSolid;
    }

    /**
     * A valid standing spot inside a cave near the player's own level (not the distant surface): on
     * solid non-fluid ground, with head/foot room, enclosed (cannot see sky), within {@code ±6} blocks
     * of the player's Y. Returns null if none found this call - retry later.
     */
    @Nullable
    public static BlockPos findUnderground(ServerPlayer player, Random random, int minDistance, int maxDistance) {
        final Level level = player.level();
        final BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < MAX_ATTEMPTS * 2; attempt++) {
            final double angle = random.nextDouble() * Math.PI * 2.0;
            final double dist = minDistance + random.nextDouble() * Math.max(1, maxDistance - minDistance);
            final int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            final int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            final int y = origin.getY() + random.nextInt(13) - 6; // roughly the player's level
            final BlockPos pos = new BlockPos(x, y, z);
            if (level.hasChunkAt(pos) && isValidStanding(level, pos) && !level.canSeeSky(pos)) {
                return pos;
            }
        }
        return null;
    }
}
