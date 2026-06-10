package com.adaptivehorror.spawn;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.Random;

/**
 * Owns the single managed stalker per player: where it spawns (which decides how it is triggered),
 * and what happens when the player triggers it.
 *
 * <p>By day it stands 75-175 blocks off and watches. By night it appears at a sheltered player's
 * window, directly in front of a sleeping player, or behind/far when out in the open. On trigger -
 * the player looking at the near forms, or approaching within 25 blocks of any form - it is
 * <b>95% just vanishes, 5% strikes</b> (teleport in, sting, brief hex, a jumpscare that only kills
 * 20% of the time). After it vanishes there is a <b>15-60 second gap</b> before the next one appears,
 * and if the player wanders 200+ blocks away it relocates to a fresh far spot. White by day, black by
 * night - all automatic.
 */
public final class StalkerManager {

    private static final int STARE_TRIGGER_TICKS = 40;     // 2s of eye contact
    private static final double STARE_LOOK_DOT = 0.975;    // within ~13 degrees
    private static final int KILL_DELAY_TICKS = 20;        // jumpscare, then (maybe) die 1s later
    private static final int NEAR_TIMEOUT = 900;           // ~45s ignored -> relocate the near forms
    private static final int FAR_TIMEOUT = 2400;           // ~120s ignored -> relocate the far form
    private static final int FRONT_SLEEP_TIMEOUT = 400;    // ~20s
    private static final int BEHIND_MIN = 6;
    private static final int BEHIND_MAX = 15;
    private static final int RESPAWN_MIN_SECONDS = 15;
    private static final int RESPAWN_MAX_SECONDS = 60;
    private static final double FOLLOW_DISTANCE = 200.0;    // wander this far -> it relocates near you

    private StalkerManager() {
    }

    public static void tick(ServerPlayer player, PlayerHorrorState state, HorrorConfig config, Random random) {
        final long now = player.level().getGameTime();

        if (state.pendingKillTick != 0L && now >= state.pendingKillTick) {
            state.pendingKillTick = 0L;
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }

        accountTravel(player, state);

        final ServerLevel level = player.serverLevel();
        final StalkerEntity active = resolveActive(level, state);
        if (active == null) {
            if (now >= state.nextStalkerSpawnTick) {          // respect the 15-60s gap
                trySpawn(player, level, state, config, random);
            }
            return;
        }

        active.setNightForm(!level.isDay());
        state.stalkerAgeTicks++;
        facePlayer(active, player);

        // If the player wandered far away, the null relocates to a fresh far spot near them.
        if (player.distanceToSqr(active) > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
            relocateFar(player, level, state, config, random);
            return;
        }

        switch (state.stalkerBehavior == null ? StalkerBehavior.FAR : state.stalkerBehavior) {
            case FRONT_SLEEP -> {
                if (!player.isSleeping() || state.stalkerAgeTicks > FRONT_SLEEP_TIMEOUT) {
                    vanish(active, player, state, random);
                }
            }
            case FAR -> {
                if (withinVanish(player, active, config)) {
                    triggerReaction(player, active, state, config, random);
                } else if (state.stalkerAgeTicks > FAR_TIMEOUT) {
                    relocateFar(player, level, state, config, random);
                }
            }
            case BEHIND -> {
                // Vanishes the moment you turn and look at it - or if you back right into it.
                if (staring(player, active, state) || player.distanceToSqr(active) <= 25.0) {
                    triggerReaction(player, active, state, config, random);
                } else if (state.stalkerAgeTicks > NEAR_TIMEOUT) {
                    relocateFar(player, level, state, config, random);
                }
            }
            case WINDOW -> {
                if (staring(player, active, state) || withinVanish(player, active, config)) {
                    triggerReaction(player, active, state, config, random);
                } else if (state.stalkerAgeTicks > NEAR_TIMEOUT || level.canSeeSky(player.blockPosition())) {
                    vanish(active, player, state, random);
                }
            }
        }
    }

    /** Debug spawn: a far watcher, close enough to see, with its coordinates reported by the command. */
    @Nullable
    public static BlockPos forceSpawn(ServerPlayer player, PlayerHorrorState state, Random random) {
        despawn(player.serverLevel(), state);
        state.nextStalkerSpawnTick = 0L;
        final BlockPos pos = SpawnLocator.findSpawn(player, random, 12, 28);
        return pos != null && spawnAt(player, player.serverLevel(), state, pos, StalkerBehavior.FAR) ? pos : null;
    }

    public static void despawn(ServerLevel level, PlayerHorrorState state) {
        final StalkerEntity active = resolveActive(level, state);
        if (active != null) {
            active.discard();
        }
        clear(state);
    }

    /**
     * Shared jumpscare strike: always shows the jumpscare, only kills {@code jumpscareKillChance}
     * (20%) of the time. Used by the stalker reaction and the watcher group.
     */
    public static void jumpscareAttack(ServerPlayer player, PlayerHorrorState state, Random random) {
        HorrorNet.sendJumpscare(player, 1 + random.nextInt(8), 1 + random.nextInt(4), 14);
        if (random.nextDouble() < ConfigManager.get().entity.jumpscareKillChance) {
            state.pendingKillTick = player.level().getGameTime() + KILL_DELAY_TICKS;
        }
    }

    // --- triggering ----------------------------------------------------------------------------

    private static boolean staring(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 center = stalker.position().add(0, stalker.getBbHeight() * 0.5, 0);
        final boolean looking = player.distanceToSqr(stalker) <= 200 * 200
                && player.getLookAngle().dot(center.subtract(eye).normalize()) >= STARE_LOOK_DOT
                && player.hasLineOfSight(stalker);
        state.stalkerLookTicks = looking ? state.stalkerLookTicks + 1 : 0;
        return state.stalkerLookTicks >= STARE_TRIGGER_TICKS;
    }

    private static boolean withinVanish(ServerPlayer player, StalkerEntity stalker, HorrorConfig config) {
        final double r = config.entity.despawnTriggerRadius;
        return player.distanceToSqr(stalker) <= r * r;
    }

    /** Mostly vanish; strike with the day/night attack chance (night nulls are far more aggressive). */
    private static void triggerReaction(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                        HorrorConfig config, Random random) {
        final double chance = player.level().isDay()
                ? config.entity.stalkerAttackChance : config.entity.stalkerAttackChanceNight;
        if (random.nextDouble() < chance) {
            aggressiveReaction(player, stalker, state, random);
        } else {
            if (random.nextDouble() < config.entity.vanishWhisperChance) {
                HorrorNet.sendSound2D(player, "iseeyou", 0.8F, 1.0F); // only 10% of the time
            }
            vanish(stalker, player, state, random);
        }
    }

    private static void aggressiveReaction(ServerPlayer player, StalkerEntity stalker,
                                           PlayerHorrorState state, Random random) {
        final Vec3 front = frontOf(player, 2.5);
        stalker.moveTo(front.x, front.y, front.z, player.getYRot() + 180.0F, 0.0F);
        facePlayer(stalker, player);
        HorrorNet.sendSoundAt(player, "travel1", front, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
        jumpscareAttack(player, state, random);
        vanish(stalker, player, state, random);
    }

    /** Removes the stalker and arms the 15-60s gap before the next one may appear. */
    private static void vanish(StalkerEntity stalker, ServerPlayer player, PlayerHorrorState state, Random random) {
        stalker.discard();
        clear(state);
        final int seconds = RESPAWN_MIN_SECONDS + random.nextInt(RESPAWN_MAX_SECONDS - RESPAWN_MIN_SECONDS + 1);
        state.nextStalkerSpawnTick = player.level().getGameTime() + (long) seconds * 20L;
    }

    // --- spawning ------------------------------------------------------------------------------

    private static void trySpawn(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                 HorrorConfig config, Random random) {
        // Sleeping: only the rare apparition standing right in front of the bed.
        if (player.isSleeping()) {
            if (level.getGameTime() % 20L == 0L && random.nextDouble() < config.entity.sleepAppearChance) {
                final Vec3 front = frontOf(player, 2.5);
                if (spawnAt(player, level, state, BlockPos.containing(front), StalkerBehavior.FRONT_SLEEP)) {
                    HorrorNet.sendSound2D(player, "iseeyou", 0.9F, 1.0F);
                    HorrorNet.sendVignettePulse(player, 25);
                }
            }
            return;
        }

        final boolean night = !level.isDay();
        final boolean sheltered = !level.canSeeSky(player.blockPosition());

        // Night + sheltered: at the window, just outside. A silent vignette sells "being watched".
        if (night && sheltered) {
            final BlockPos window = SpawnLocator.findSkylit(player, random, 5, 16);
            if (window != null && spawnAt(player, level, state, window, StalkerBehavior.WINDOW)) {
                HorrorNet.sendVignettePulse(player, 20);
                return;
            }
        }

        // By night out in the open, it may loom directly behind; by day it is always the far watcher.
        if (night && !sheltered && random.nextBoolean()) {
            final BlockPos behind = SpawnLocator.findBehind(player, random, BEHIND_MIN, BEHIND_MAX);
            if (behind != null && spawnAt(player, level, state, behind, StalkerBehavior.BEHIND)) {
                HorrorNet.sendVignettePulse(player, 18);
                return;
            }
        }
        spawnFar(player, level, state, config, random);
    }

    private static void spawnFar(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                 HorrorConfig config, Random random) {
        final BlockPos far = SpawnLocator.findSpawn(player, random,
                config.entity.spawnDistanceMin, config.entity.spawnDistanceMax);
        if (far != null) {
            spawnAt(player, level, state, far, StalkerBehavior.FAR);
        }
    }

    private static boolean spawnAt(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                   BlockPos pos, StalkerBehavior placement) {
        final StalkerEntity stalker = ModEntities.STALKER.create(level);
        if (stalker == null) {
            return false;
        }
        stalker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        stalker.setNightForm(!level.isDay());
        facePlayer(stalker, player);
        if (!level.addFreshEntity(stalker)) {
            return false;
        }
        state.activeStalkerId = stalker.getUUID();
        state.stalkerBehavior = placement;
        state.stalkerAgeTicks = 0;
        state.stalkerLookTicks = 0;
        state.travelSinceRelocate = 0.0;
        return true;
    }

    /** Immediately moves the null to a fresh far spot (no gap) - the "it followed me" relocate. */
    private static void relocateFar(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                    HorrorConfig config, Random random) {
        final StalkerEntity active = resolveActive(level, state);
        if (active != null) {
            active.discard();
        }
        clear(state);
        state.nextStalkerSpawnTick = 0L;
        spawnFar(player, level, state, config, random);
    }

    // --- helpers -------------------------------------------------------------------------------

    private static Vec3 frontOf(ServerPlayer player, double distance) {
        final Vec3 look = player.getLookAngle();
        final Vec3 horiz = new Vec3(look.x, 0, look.z).normalize().scale(distance);
        return player.position().add(horiz);
    }

    private static void facePlayer(StalkerEntity stalker, ServerPlayer player) {
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
}
