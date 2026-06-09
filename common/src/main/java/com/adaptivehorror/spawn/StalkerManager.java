package com.adaptivehorror.spawn;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Owns the lifecycle of the single stalker per player: spawning it at a believable position,
 * despawning it the instant the player closes in (rolling the proximity effects), and relocating it
 * once the player has travelled far enough to sell the "it followed me" illusion.
 *
 * <p>Spawn <em>placement</em> is where the adaptive AI becomes visible. The dominant behaviour signal
 * (from {@link com.adaptivehorror.ai.BehaviorTracker}) selects a placement mode:
 * <ul>
 *   <li><b>AFK</b> &rarr; appears directly behind the player, close.</li>
 *   <li><b>Mining</b> &rarr; appears at an open-sky position (a cave mouth above the digger).</li>
 *   <li><b>Camping</b> &rarr; appears outside the shelter, in the open.</li>
 *   <li><b>Sleeping</b> &rarr; small chance to appear right beside the bed.</li>
 *   <li>otherwise &rarr; the default peripheral arc.</li>
 * </ul>
 */
public final class StalkerManager {

    private static final double DOMINANCE_THRESHOLD = 40.0;

    /** Status effects eligible for the proximity scare (Holders in 1.21). Darkness now exists natively. */
    @SuppressWarnings("unchecked")
    private static final Holder<MobEffect>[] PROXIMITY_EFFECTS = new Holder[]{
            MobEffects.BLINDNESS,
            MobEffects.CONFUSION,     // "Nausea"
            MobEffects.DIG_SLOWDOWN,  // "Mining Fatigue"
            MobEffects.DARKNESS,
    };

    private StalkerManager() {
    }

    public static void tick(ServerPlayer player, PlayerHorrorState state, HorrorConfig config, Random random) {
        accountTravel(player, state);

        final ServerLevel level = player.serverLevel();
        final StalkerEntity active = resolveActive(level, state);

        if (active == null) {
            if (player.isSleeping() && level.getGameTime() % 20L == 0L
                    && random.nextDouble() < config.entity.sleepAppearChance) {
                trySpawnBesideBed(player, level, state, random);
                return;
            }
            trySpawn(player, level, state, config, random);
            return;
        }

        active.setNightForm(!level.isDay());

        if (handleProximity(player, active, state, config, random)) {
            return;
        }
        handleRelocate(player, active, state, config, random);
    }

    /** Force-spawns a stalker in the peripheral arc, replacing any existing one. For debug commands. */
    public static boolean forceSpawn(ServerPlayer player, PlayerHorrorState state,
                                     HorrorConfig config, Random random) {
        despawn(player.serverLevel(), state);
        final BlockPos pos = SpawnLocator.findSpawn(
                player, random, config.entity.spawnDistanceMin, config.entity.spawnDistanceMax);
        return pos != null && spawnAt(player, player.serverLevel(), state, pos);
    }

    public static void despawn(ServerLevel level, PlayerHorrorState state) {
        final StalkerEntity active = resolveActive(level, state);
        if (active != null) {
            active.discard();
        }
        state.activeStalkerId = null;
    }

    // --- placement -----------------------------------------------------------------------------

    private static void trySpawn(ServerPlayer player, ServerLevel level,
                                 PlayerHorrorState state, HorrorConfig config, Random random) {
        final BlockPos pos = choosePosition(player, state, config, random);
        if (pos != null) {
            spawnAt(player, level, state, pos);
        }
    }

    @Nullable
    private static BlockPos choosePosition(ServerPlayer player, PlayerHorrorState state,
                                           HorrorConfig config, Random random) {
        final int min = config.entity.spawnDistanceMin;
        final int max = config.entity.spawnDistanceMax;

        final long afkTicksThreshold = (long) config.entity.afkAppearSeconds * 20L;
        if (state.behavior.afkTicks >= afkTicksThreshold) {
            final BlockPos behind = SpawnLocator.findBehind(player, random, 6, 14);
            if (behind != null) {
                return behind;
            }
        }

        final double mining = state.behavior.miningScore;
        final double camping = state.behavior.campingScore;
        if (mining >= DOMINANCE_THRESHOLD && mining >= camping) {
            final BlockPos skylit = SpawnLocator.findSkylit(player, random, min, max);
            if (skylit != null) {
                return skylit;
            }
        } else if (camping >= DOMINANCE_THRESHOLD) {
            final BlockPos skylit = SpawnLocator.findSkylit(player, random, min, max);
            if (skylit != null) {
                return skylit;
            }
        }

        return SpawnLocator.findSpawn(player, random, min, max);
    }

    private static void trySpawnBesideBed(ServerPlayer player, ServerLevel level,
                                          PlayerHorrorState state, Random random) {
        final Optional<BlockPos> bed = player.getSleepingPos();
        if (bed.isEmpty()) {
            return;
        }
        final BlockPos head = bed.get();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            final BlockPos side = head.relative(dir);
            if (level.getBlockState(side).isAir()
                    && level.getBlockState(side.above()).isAir()
                    && level.getBlockState(side.below()).isSolid()) {
                spawnAt(player, level, state, side);
                return;
            }
        }
    }

    private static boolean spawnAt(ServerPlayer player, ServerLevel level, PlayerHorrorState state, BlockPos pos) {
        final StalkerEntity stalker = ModEntities.STALKER.create(level);
        if (stalker == null) {
            return false;
        }
        final float yaw = facingYaw(pos, player.position());
        stalker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        stalker.setYHeadRot(yaw);
        stalker.setNightForm(!level.isDay());

        if (!level.addFreshEntity(stalker)) {
            return false;
        }
        state.activeStalkerId = stalker.getUUID();
        state.travelSinceRelocate = 0.0;
        return true;
    }

    // --- lifecycle helpers ---------------------------------------------------------------------

    private static void accountTravel(ServerPlayer player, PlayerHorrorState state) {
        final Vec3 now = player.position();
        if (state.lastSampledPos != null) {
            final double dx = now.x - state.lastSampledPos.x;
            final double dz = now.z - state.lastSampledPos.z;
            final double horizontal = Math.sqrt(dx * dx + dz * dz);
            state.travelSinceRelocate += horizontal;
            state.travelSinceSound += horizontal;
        }
        state.lastSampledPos = now;
    }

    @Nullable
    private static StalkerEntity resolveActive(ServerLevel level, PlayerHorrorState state) {
        if (state.activeStalkerId == null) {
            return null;
        }
        final Entity entity = level.getEntity(state.activeStalkerId);
        if (entity instanceof StalkerEntity stalker && entity.isAlive()) {
            return stalker;
        }
        state.activeStalkerId = null;
        return null;
    }

    private static boolean handleProximity(ServerPlayer player, StalkerEntity stalker,
                                           PlayerHorrorState state, HorrorConfig config, Random random) {
        final double radius = config.entity.despawnTriggerRadius;
        if (player.distanceToSqr(stalker) > radius * radius) {
            return false;
        }
        stalker.discard();
        state.activeStalkerId = null;

        if (random.nextDouble() < config.entity.proximityEffectChance) {
            applyProximityEffects(player, config, random);
        }
        return true;
    }

    private static void handleRelocate(ServerPlayer player, StalkerEntity stalker,
                                       PlayerHorrorState state, HorrorConfig config, Random random) {
        if (state.travelSinceRelocate < config.entity.relocateTravelBlocks) {
            return;
        }
        stalker.discard();
        state.activeStalkerId = null;
        state.travelSinceRelocate = 0.0;
        trySpawn(player, player.serverLevel(), state, config, random);
    }

    private static void applyProximityEffects(ServerPlayer player, HorrorConfig config, Random random) {
        final List<Holder<MobEffect>> pool = new ArrayList<>();
        Collections.addAll(pool, PROXIMITY_EFFECTS);
        Collections.shuffle(pool, random);

        final int count = 1 + random.nextInt(3); // 1..3 effects
        final int minD = config.entity.effectDurationSecondsMin;
        final int maxD = config.entity.effectDurationSecondsMax;

        for (int i = 0; i < count && i < pool.size(); i++) {
            final int seconds = minD + random.nextInt(Math.max(1, maxD - minD + 1));
            player.addEffect(new MobEffectInstance(pool.get(i), seconds * 20, 0, false, false, true));
        }
    }

    private static float facingYaw(BlockPos from, Vec3 target) {
        final double dx = target.x - (from.getX() + 0.5);
        final double dz = target.z - (from.getZ() + 0.5);
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
