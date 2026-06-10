package com.adaptivehorror.spawn;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.network.HorrorNet;
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
 * Owns the lifecycle of the single stalker per player. At spawn the stalker commits to a random
 * {@link StalkerBehavior}, and from then on this manager runs the matching routine:
 * <ul>
 *   <li><b>EFFECT</b> - vanishes when approached and applies a brief status effect.</li>
 *   <li><b>VANISH</b> - just vanishes when approached.</li>
 *   <li><b>RUSH</b> - sprints at the player and kills them at ~3 blocks (with a jumpscare).</li>
 *   <li><b>WATCH</b> - spawns close behind and stares; the instant the player looks at it, it does
 *       one of the above.</li>
 * </ul>
 * Spawn <em>placement</em> still reflects the adaptive AI for the non-aggressive archetypes.
 */
public final class StalkerManager {

    private static final double DOMINANCE_THRESHOLD = 40.0;
    private static final double RUSH_SPEED = 1.1;            // blocks/tick - a fast, unnatural sprint
    private static final double RUSH_KILL_DISTANCE = 3.0;
    private static final int RUSH_TIMEOUT_TICKS = 220;       // give up if the player escapes (~11s)
    private static final double WATCH_LOOK_DOT = 0.6;        // player looking within ~53 deg of it
    private static final int WATCH_TIMEOUT_TICKS = 1200;     // 60s of being ignored -> relocate
    private static final int KILL_JUMPSCARE_TICKS = 14;

    /** Status effects eligible for the proximity scare (Darkness exists natively in 1.21). */
    @SuppressWarnings("unchecked")
    private static final Holder<MobEffect>[] PROXIMITY_EFFECTS = new Holder[]{
            MobEffects.BLINDNESS,
            MobEffects.CONFUSION,
            MobEffects.DIG_SLOWDOWN,
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
                trySpawnBesideBed(player, level, state);
                return;
            }
            trySpawn(player, level, state, config, random, StalkerBehavior.random(random));
            return;
        }

        active.setNightForm(!level.isDay());
        state.stalkerAgeTicks++;

        switch (state.stalkerBehavior == null ? StalkerBehavior.EFFECT : state.stalkerBehavior) {
            case RUSH -> tickRush(player, active, state, config, random);
            case WATCH -> tickWatch(player, active, state, config, random);
            case VANISH -> tickVanish(player, active, state, config, random);
            default -> tickEffect(player, active, state, config, random);
        }
    }

    /** Force-spawns a stalker for debug commands (random behaviour), close and visible. */
    @Nullable
    public static BlockPos forceSpawn(ServerPlayer player, PlayerHorrorState state, Random random) {
        despawn(player.serverLevel(), state);
        final StalkerBehavior behavior = StalkerBehavior.random(random);
        BlockPos pos = positionFor(behavior, player, state, ConfigManager.get(), random, true);
        if (pos == null) {
            pos = SpawnLocator.findSpawn(player, random, 10, 25);
        }
        if (pos != null && spawnAt(player, player.serverLevel(), state, pos)) {
            state.stalkerBehavior = behavior;
            state.stalkerAgeTicks = 0;
            return pos;
        }
        return null;
    }

    public static void despawn(ServerLevel level, PlayerHorrorState state) {
        final StalkerEntity active = resolveActive(level, state);
        if (active != null) {
            active.discard();
        }
        clear(state);
    }

    // --- spawning ------------------------------------------------------------------------------

    private static void trySpawn(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                 HorrorConfig config, Random random, StalkerBehavior behavior) {
        final BlockPos pos = positionFor(behavior, player, state, config, random, false);
        if (pos != null && spawnAt(player, level, state, pos)) {
            state.stalkerBehavior = behavior;
            state.stalkerAgeTicks = 0;
        }
    }

    @Nullable
    private static BlockPos positionFor(StalkerBehavior behavior, ServerPlayer player, PlayerHorrorState state,
                                        HorrorConfig config, Random random, boolean debugClose) {
        return switch (behavior) {
            case WATCH -> SpawnLocator.findBehind(player, random, 3, 7);          // right behind them
            case RUSH -> SpawnLocator.findSpawn(player, random,
                    debugClose ? 8 : 14, debugClose ? 20 : 34);                   // visible, then sprints
            default -> debugClose
                    ? SpawnLocator.findSpawn(player, random, 10, 25)
                    : choosePosition(player, state, config, random);
        };
    }

    /** Adaptive placement for the passive archetypes (AFK -> behind, mining/camping -> open sky). */
    @Nullable
    private static BlockPos choosePosition(ServerPlayer player, PlayerHorrorState state,
                                           HorrorConfig config, Random random) {
        final int min = config.entity.spawnDistanceMin;
        final int max = config.entity.spawnDistanceMax;

        if (state.behavior.afkTicks >= (long) config.entity.afkAppearSeconds * 20L) {
            final BlockPos behind = SpawnLocator.findBehind(player, random, 6, 14);
            if (behind != null) {
                return behind;
            }
        }
        final double mining = state.behavior.miningScore;
        final double camping = state.behavior.campingScore;
        if ((mining >= DOMINANCE_THRESHOLD && mining >= camping) || camping >= DOMINANCE_THRESHOLD) {
            final BlockPos skylit = SpawnLocator.findSkylit(player, random, min, max);
            if (skylit != null) {
                return skylit;
            }
        }
        return SpawnLocator.findSpawn(player, random, min, max);
    }

    private static boolean spawnAt(ServerPlayer player, ServerLevel level, PlayerHorrorState state, BlockPos pos) {
        final StalkerEntity stalker = ModEntities.STALKER.create(level);
        if (stalker == null) {
            return false;
        }
        final float yaw = facingYaw(player.position(), pos);
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

    // --- behaviours ----------------------------------------------------------------------------

    private static void tickEffect(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                   HorrorConfig config, Random random) {
        faceStalkerAtPlayer(stalker, player);
        final double radius = config.entity.despawnTriggerRadius;
        if (player.distanceToSqr(stalker) <= radius * radius) {
            stalker.discard();
            clear(state);
            if (random.nextDouble() < config.entity.proximityEffectChance) {
                applyProximityEffects(player, config, random);
            }
            return;
        }
        handleRelocate(player, state, config, random);
    }

    private static void tickVanish(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                   HorrorConfig config, Random random) {
        faceStalkerAtPlayer(stalker, player);
        if (player.distanceToSqr(stalker) <= config.entity.despawnTriggerRadius * (double) config.entity.despawnTriggerRadius) {
            stalker.discard();
            clear(state);
            return;
        }
        handleRelocate(player, state, config, random);
    }

    private static void tickRush(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                 HorrorConfig config, Random random) {
        final Vec3 from = stalker.position();
        final Vec3 to = player.position();
        final double dist = from.distanceTo(to);

        if (dist <= RUSH_KILL_DISTANCE) {
            killPlayer(player, random);
            stalker.discard();
            clear(state);
            return;
        }
        if (state.stalkerAgeTicks > RUSH_TIMEOUT_TICKS) {
            stalker.discard(); // the player outran it
            clear(state);
            return;
        }
        final Vec3 step = to.subtract(from).normalize().scale(RUSH_SPEED);
        final Vec3 next = from.add(step);
        final float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - next.x), to.z - next.z));
        stalker.moveTo(next.x, next.y, next.z, yaw, 0.0F);
        stalker.setYHeadRot(yaw);
    }

    private static void tickWatch(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                  HorrorConfig config, Random random) {
        faceStalkerAtPlayer(stalker, player);

        final Vec3 look = player.getLookAngle();
        final Vec3 toStalker = stalker.position().add(0, stalker.getBbHeight() * 0.5, 0)
                .subtract(player.getEyePosition()).normalize();
        final boolean playerLooking = look.dot(toStalker) >= WATCH_LOOK_DOT
                && player.distanceToSqr(stalker) <= 64 * 64;

        if (playerLooking) {
            // Caught looking - react with one of the other behaviours.
            final int roll = random.nextInt(100);
            if (roll < 40) {                 // vanish
                stalker.discard();
                clear(state);
            } else if (roll < 75) {          // brief effect, then vanish
                stalker.discard();
                clear(state);
                applyProximityEffects(player, config, random);
            } else {                         // turn aggressive
                state.stalkerBehavior = StalkerBehavior.RUSH;
                state.stalkerAgeTicks = 0;
            }
            return;
        }
        if (state.stalkerAgeTicks > WATCH_TIMEOUT_TICKS) {
            stalker.discard();
            clear(state);
        }
    }

    private static void killPlayer(ServerPlayer player, Random random) {
        HorrorNet.sendJumpscare(player, 1 + random.nextInt(8), 1 + random.nextInt(4), KILL_JUMPSCARE_TICKS);
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    // --- helpers -------------------------------------------------------------------------------

    private static void faceStalkerAtPlayer(StalkerEntity stalker, ServerPlayer player) {
        final Vec3 s = stalker.position();
        final float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - s.x), player.getZ() - s.z));
        stalker.setYRot(yaw);
        stalker.setYHeadRot(yaw);
        stalker.setYBodyRot(yaw);
    }

    private static void trySpawnBesideBed(ServerPlayer player, ServerLevel level, PlayerHorrorState state) {
        final Optional<BlockPos> bed = player.getSleepingPos();
        if (bed.isEmpty()) {
            return;
        }
        final BlockPos head = bed.get();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            final BlockPos side = head.relative(dir);
            if (level.getBlockState(side).isAir() && level.getBlockState(side.above()).isAir()
                    && level.getBlockState(side.below()).isSolid()) {
                if (spawnAt(player, level, state, side)) {
                    state.stalkerBehavior = StalkerBehavior.WATCH;
                    state.stalkerAgeTicks = 0;
                }
                return;
            }
        }
    }

    private static void clear(PlayerHorrorState state) {
        state.activeStalkerId = null;
        state.stalkerBehavior = null;
        state.stalkerAgeTicks = 0;
    }

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
        clear(state);
        return null;
    }

    private static void handleRelocate(ServerPlayer player, PlayerHorrorState state,
                                       HorrorConfig config, Random random) {
        if (state.travelSinceRelocate < config.entity.relocateTravelBlocks) {
            return;
        }
        despawn(player.serverLevel(), state);
        trySpawn(player, player.serverLevel(), state, config, random, StalkerBehavior.random(random));
    }

    private static void applyProximityEffects(ServerPlayer player, HorrorConfig config, Random random) {
        final List<Holder<MobEffect>> pool = new ArrayList<>();
        Collections.addAll(pool, PROXIMITY_EFFECTS);
        Collections.shuffle(pool, random);
        final int count = 1 + random.nextInt(3);
        final int minD = config.entity.effectDurationSecondsMin;
        final int maxD = config.entity.effectDurationSecondsMax;
        for (int i = 0; i < count && i < pool.size(); i++) {
            final int seconds = minD + random.nextInt(Math.max(1, maxD - minD + 1));
            player.addEffect(new MobEffectInstance(pool.get(i), seconds * 20, 0, false, false, true));
        }
    }

    private static float facingYaw(Vec3 target, BlockPos pos) {
        final double dx = target.x - (pos.getX() + 0.5);
        final double dz = target.z - (pos.getZ() + 0.5);
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
