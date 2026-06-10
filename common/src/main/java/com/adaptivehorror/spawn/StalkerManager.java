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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Owns the lifecycle and per-encounter behaviour of the single stalker per player.
 *
 * <p>On spawn the stalker commits to a random {@link StalkerBehavior}. Independently of that, an
 * "enderman rule" applies to every form: if the player stares straight at the stalker for two
 * seconds, it teleports right in front of them, plays a sting, inflicts a short slowness/blindness/
 * nausea, and vanishes (with a small chance to turn lethal). RUSH is the only routine that reliably
 * kills, and even then via a jumpscare-then-die beat - so deaths stay rare.
 */
public final class StalkerManager {

    private static final double DOMINANCE_THRESHOLD = 40.0;
    private static final double RUSH_SPEED = 1.2;
    private static final double RUSH_KILL_DISTANCE = 1.0;        // jumpscare at 1 block, then die
    private static final int RUSH_TIMEOUT_TICKS = 240;
    private static final int STARE_TRIGGER_TICKS = 40;          // 2 seconds of eye contact
    private static final double STARE_LOOK_DOT = 0.975;         // within ~13 degrees
    private static final int REVEAL_TICKS = 12;                  // how long the teleport-in lingers
    private static final int KILL_DELAY_TICKS = 20;             // jumpscare, then die 1s later
    private static final double STARE_KILL_CHANCE = 0.05;
    private static final int WATCH_TIMEOUT_TICKS = 1200;

    @SuppressWarnings("unchecked")
    private static final Holder<MobEffect>[] PROXIMITY_EFFECTS = new Holder[]{
            MobEffects.BLINDNESS, MobEffects.CONFUSION, MobEffects.DIG_SLOWDOWN, MobEffects.DARKNESS,
    };

    private StalkerManager() {
    }

    public static void tick(ServerPlayer player, PlayerHorrorState state, HorrorConfig config, Random random) {
        final long now = player.level().getGameTime();

        // A scheduled jumpscare-kill lands even after the entity itself is gone.
        if (state.pendingKillTick != 0L && now >= state.pendingKillTick) {
            state.pendingKillTick = 0L;
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }

        accountTravel(player, state);

        final ServerLevel level = player.serverLevel();
        final StalkerEntity active = resolveActive(level, state);

        if (active == null) {
            if (player.isSleeping() && now % 20L == 0L
                    && random.nextDouble() < config.entity.sleepAppearChance) {
                trySpawnBesideBed(player, level, state);
                return;
            }
            trySpawn(player, level, state, config, random, StalkerBehavior.random(random));
            return;
        }

        active.setNightForm(!level.isDay());
        state.stalkerAgeTicks++;
        faceStalkerAtPlayer(active, player);

        // Reveal (post-teleport) lingers briefly, then vanishes.
        if (state.revealEndTick != 0L) {
            if (now >= state.revealEndTick) {
                active.discard();
                clear(state);
            }
            return;
        }

        // The enderman rule, for every non-rushing form.
        if (state.stalkerBehavior != StalkerBehavior.RUSH && checkStare(player, active, state)) {
            stareReaction(player, active, state, random);
            return;
        }

        switch (state.stalkerBehavior == null ? StalkerBehavior.EFFECT : state.stalkerBehavior) {
            case RUSH -> tickRush(player, active, state, random);
            case WATCH -> tickWatch(player, active, state, config, random);
            case VANISH -> tickVanish(player, active, state, config);
            default -> tickEffect(player, active, state, config, random);
        }
    }

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
            state.stalkerLookTicks = 0;
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

    // --- the enderman rule ---------------------------------------------------------------------

    private static boolean checkStare(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 center = stalker.position().add(0, stalker.getBbHeight() * 0.5, 0);
        final double distSq = player.distanceToSqr(stalker);
        final boolean looking = distSq <= 64 * 64
                && player.getLookAngle().dot(center.subtract(eye).normalize()) >= STARE_LOOK_DOT
                && player.hasLineOfSight(stalker);
        state.stalkerLookTicks = looking ? state.stalkerLookTicks + 1 : 0;
        return state.stalkerLookTicks >= STARE_TRIGGER_TICKS;
    }

    private static void stareReaction(ServerPlayer player, StalkerEntity stalker,
                                      PlayerHorrorState state, Random random) {
        // Teleport directly in front of the player's face.
        final Vec3 look = player.getLookAngle();
        final Vec3 horiz = new Vec3(look.x, 0, look.z).normalize().scale(2.5);
        final Vec3 front = player.position().add(horiz);
        stalker.moveTo(front.x, player.getY(), front.z, player.getYRot() + 180.0F, 0.0F);
        faceStalkerAtPlayer(stalker, player);

        HorrorNet.sendSoundAt(player, "travel1", front, 1.0F, 1.0F); // 120blocksound1
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));

        state.stalkerLookTicks = 0;
        state.revealEndTick = player.level().getGameTime() + REVEAL_TICKS;

        if (random.nextDouble() < STARE_KILL_CHANCE) {
            jumpscareAttack(player, state, random);
        }
    }

    // --- behaviours ----------------------------------------------------------------------------

    private static void tickEffect(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                   HorrorConfig config, Random random) {
        final double radius = config.entity.despawnTriggerRadius;
        if (player.distanceToSqr(stalker) <= radius * radius) {
            // A proper "event": a visual lightning crack, a sting, and a hex.
            strikeLightning(player.serverLevel(), player.position());
            HorrorNet.sendSound2D(player, "jumpscare" + (1 + random.nextInt(4)), 0.7F, 0.9F);
            stalker.discard();
            clear(state);
            applyProximityEffects(player, config, random);
            return;
        }
        handleRelocate(player, state, config, random);
    }

    private static void tickVanish(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                   HorrorConfig config) {
        final double r = config.entity.despawnTriggerRadius;
        if (player.distanceToSqr(stalker) <= r * r) {
            stalker.discard();
            clear(state);
            return;
        }
        handleRelocate(player, state, config, new Random());
    }

    private static void tickRush(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state, Random random) {
        final Vec3 from = stalker.position();
        final Vec3 to = player.position();
        if (from.distanceTo(to) <= RUSH_KILL_DISTANCE) {
            // Jumpscare and vanish now; death follows a second later only 20% of the time.
            jumpscareAttack(player, state, random);
            stalker.discard();
            clear(state);
            return;
        }
        if (state.stalkerAgeTicks > RUSH_TIMEOUT_TICKS) {
            stalker.discard();
            clear(state);
            return;
        }
        final Vec3 next = from.add(to.subtract(from).normalize().scale(RUSH_SPEED));
        stalker.moveTo(next.x, next.y, next.z, stalker.getYRot(), 0.0F);
    }

    private static void tickWatch(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                  HorrorConfig config, Random random) {
        if (player.distanceToSqr(stalker) <= config.entity.despawnTriggerRadius * (double) config.entity.despawnTriggerRadius) {
            stalker.discard();
            clear(state);
            return;
        }
        if (state.stalkerAgeTicks > WATCH_TIMEOUT_TICKS) {
            stalker.discard();
            clear(state);
        }
    }

    /**
     * A jumpscare strike. Always shows the jumpscare; only sometimes (config jumpscareKillChance,
     * default 20%) does it schedule the actual death a second later - 80% of the time null just
     * scares and vanishes. Shared by the rush, the stare reaction and the watchers.
     */
    public static void jumpscareAttack(ServerPlayer player, PlayerHorrorState state, Random random) {
        HorrorNet.sendJumpscare(player, 1 + random.nextInt(8), 1 + random.nextInt(4), 14);
        if (random.nextDouble() < ConfigManager.get().entity.jumpscareKillChance) {
            state.pendingKillTick = player.level().getGameTime() + KILL_DELAY_TICKS;
        }
    }

    private static void strikeLightning(ServerLevel level, Vec3 near) {
        final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(near.x, near.y, near.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    // --- spawning ------------------------------------------------------------------------------

    private static void trySpawn(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                 HorrorConfig config, Random random, StalkerBehavior behavior) {
        final BlockPos pos = positionFor(behavior, player, state, config, random, false);
        if (pos != null && spawnAt(player, level, state, pos)) {
            state.stalkerBehavior = behavior;
            state.stalkerAgeTicks = 0;
            state.stalkerLookTicks = 0;
        }
    }

    @Nullable
    private static BlockPos positionFor(StalkerBehavior behavior, ServerPlayer player, PlayerHorrorState state,
                                        HorrorConfig config, Random random, boolean debugClose) {
        return switch (behavior) {
            case WATCH -> SpawnLocator.findBehind(player, random, 3, 7);
            case RUSH -> SpawnLocator.findSpawn(player, random, debugClose ? 8 : 14, debugClose ? 20 : 34);
            default -> debugClose
                    ? SpawnLocator.findSpawn(player, random, 10, 25)
                    : choosePosition(player, state, config, random);
        };
    }

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
                    state.stalkerLookTicks = 0;
                }
                return;
            }
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private static void faceStalkerAtPlayer(StalkerEntity stalker, ServerPlayer player) {
        final Vec3 s = stalker.position();
        final float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - s.x), player.getZ() - s.z));
        stalker.setYRot(yaw);
        stalker.setYHeadRot(yaw);
        stalker.setYBodyRot(yaw);
    }

    private static void clear(PlayerHorrorState state) {
        state.activeStalkerId = null;
        state.stalkerBehavior = null;
        state.stalkerAgeTicks = 0;
        state.stalkerLookTicks = 0;
        state.revealEndTick = 0L;
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
