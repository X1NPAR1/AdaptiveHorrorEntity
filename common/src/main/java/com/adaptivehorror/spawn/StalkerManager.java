package com.adaptivehorror.spawn;

import com.adaptivehorror.ai.AdaptiveAI;
import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.util.Locations;
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
    /** Trigger radius for the <em>close</em> forms (behind/window/cave) - smaller than the far 25. */
    private static final double CLOSE_VANISH = 6.0;
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
            // A death is shared: every other player sees a jumpscare and, with sharedDeath, dies too.
            final net.minecraft.server.MinecraftServer server = player.getServer();
            final HorrorConfig cfg = ConfigManager.get();
            if (server != null) {
                final int img = 1 + random.nextInt(8);
                final int snd = 1 + random.nextInt(4);
                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                    if (other == player) {
                        continue;
                    }
                    if (cfg.features.sharedJumpscare) {
                        HorrorNet.sendJumpscare(other, img, snd, 16);
                    }
                    if (cfg.features.sharedDeath) {
                        other.hurt(other.damageSources().genericKill(), Float.MAX_VALUE);
                    } else {
                        HorrorNet.sendVignettePulse(other, 30); // at least feel it
                    }
                }
            }
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

        final boolean cave = state.stalkerBehavior == StalkerBehavior.CAVE;

        // The player's environment changed (surface <-> underground): re-place to match, so an old
        // surface null never lingers far above a player who has gone deep underground (or vice versa).
        if (Locations.isUnderground(player) != cave) {
            despawn(level, state);
            return;
        }

        active.setNightForm(cave || !level.isDay() || state.stalkerBlack); // black underground / forced
        state.stalkerAgeTicks++;
        facePlayer(active, player);

        // At dawn the close night forms move off to the distant day-watcher position, so a white null
        // never just stands a few blocks away.
        if (level.isDay() && (state.stalkerBehavior == StalkerBehavior.BEHIND
                || state.stalkerBehavior == StalkerBehavior.WINDOW)) {
            relocateFar(player, level, state, config, random);
            return;
        }

        // If the player wandered far away, the null relocates to a fresh spot near them. (Not for the
        // cave form, which stays at the player's level and just vanishes/respawns.)
        if (!cave && player.distanceToSqr(active) > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
            relocateFar(player, level, state, config, random);
            return;
        }

        switch (state.stalkerBehavior == null ? StalkerBehavior.FAR : state.stalkerBehavior) {
            case CAVE -> {
                if (state.stalkerRush) {
                    tickCaveRush(player, active, state, random); // tunnel ambush: it charges you
                } else if (nearby(player, active, CLOSE_VANISH) || staring(player, active, state)) {
                    // Close form: a small radius or a stare - never the big 25-block one, or it would
                    // vanish the instant it spawned in a tight cave.
                    triggerReaction(player, active, state, config, random);
                } else if (state.stalkerAgeTicks > NEAR_TIMEOUT) {
                    vanish(active, player, state, random); // re-appears in the cave after the gap
                }
            }
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
                if (staring(player, active, state) || nearby(player, active, CLOSE_VANISH)) {
                    triggerReaction(player, active, state, config, random);
                } else if (state.stalkerAgeTicks > NEAR_TIMEOUT) {
                    relocateFar(player, level, state, config, random);
                }
            }
            case WINDOW -> {
                if (staring(player, active, state) || nearby(player, active, CLOSE_VANISH)) {
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
        return pos != null && spawnAt(player, player.serverLevel(), state, pos, StalkerBehavior.FAR, false) ? pos : null;
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
        final int img = 1 + random.nextInt(8);
        final int snd = 1 + random.nextInt(4);
        HorrorNet.sendJumpscare(player, img, snd, 14);
        // Shared jumpscare: the rest of the server sees the same scare at the same instant.
        final net.minecraft.server.MinecraftServer server = player.getServer();
        if (ConfigManager.get().features.sharedJumpscare && server != null) {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other != player) {
                    HorrorNet.sendJumpscare(other, img, snd, 14);
                }
            }
        }
        final int day = com.adaptivehorror.scheduler.DayProgression.dayOf(player.level());
        if (random.nextDouble() < AdaptiveAI.killChance(day, ConfigManager.get().entity.jumpscareKillChance)) {
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

    private static boolean nearby(ServerPlayer player, StalkerEntity stalker, double radius) {
        return player.distanceToSqr(stalker) <= radius * radius;
    }

    /** Mostly vanish early on; strikes far more readily as the days pass (and at night/underground). */
    private static void triggerReaction(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                        HorrorConfig config, Random random) {
        final double chance = AdaptiveAI.strikeChance(player.serverLevel(), state,
                state.stalkerBehavior == StalkerBehavior.CAVE, state.stalkerBlack);
        state.encounters++;
        if (random.nextDouble() < chance) {
            aggressiveReaction(player, stalker, state, random);
        } else {
            // The passive white (day) watcher is unsettling on its own: 10% it jumpscares as it goes.
            final boolean white = state.stalkerBehavior == StalkerBehavior.FAR
                    && !state.stalkerBlack && player.level().isDay();
            if (white && random.nextDouble() < config.entity.whiteVanishJumpscareChance) {
                HorrorNet.sendJumpscare(player, 1 + random.nextInt(8), 1 + random.nextInt(4), 12);
            } else if (random.nextDouble() < config.entity.vanishWhisperChance) {
                HorrorNet.sendSound2D(player, "iseeyou", 0.8F, 1.0F);
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

    /** The tunnel ambush: it waits until you're in range, then charges and jumpscares on contact. */
    private static void tickCaveRush(ServerPlayer player, StalkerEntity stalker, PlayerHorrorState state,
                                     Random random) {
        final Vec3 from = stalker.position();
        final Vec3 to = player.position();
        final double dist = from.distanceTo(to);

        if (dist <= 2.0) {
            caveRushHit(player, state, random); // jumpscare; 5-10% it then kills
            stalker.discard();
            clear(state);
            return;
        }
        if (state.stalkerAgeTicks > 320) {     // you got away down the tunnel
            vanish(stalker, player, state, random);
            return;
        }
        if (dist > 18.0) {
            return;                            // bides its time until you come into the tunnel
        }
        final Vec3 next = from.add(to.subtract(from).normalize().scale(0.85)); // a quick, unnatural charge
        stalker.moveTo(next.x, next.y, next.z, stalker.getYRot(), 0.0F);
        facePlayer(stalker, player);
    }

    private static void caveRushHit(ServerPlayer player, PlayerHorrorState state, Random random) {
        HorrorNet.sendJumpscare(player, 1 + random.nextInt(8), 1 + random.nextInt(4), 14);
        if (random.nextDouble() < 0.08) {      // kills AFTER the jumpscare, ~5-10% of the time
            state.pendingKillTick = player.level().getGameTime() + KILL_DELAY_TICKS;
        }
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
                if (spawnAt(player, level, state, BlockPos.containing(front), StalkerBehavior.FRONT_SLEEP, false)) {
                    HorrorNet.sendSound2D(player, "iseeyou", 0.9F, 1.0F);
                    HorrorNet.sendVignettePulse(player, 25);
                }
            }
            return;
        }

        // Underground: a black null at the player's own level, inside the cave - never the far surface.
        // Half of them are tunnel-ambushers that charge instead of just watching.
        if (Locations.isUnderground(player)) {
            final BlockPos cave = SpawnLocator.findUnderground(player, random, 10, 30);
            if (cave != null && spawnAt(player, level, state, cave, StalkerBehavior.CAVE, false)) {
                state.stalkerRush = random.nextDouble() < 0.5;
            }
            return;
        }

        // Smart, behaviour-driven placement (window when hiding, behind when AFK, far when vigilant).
        final StalkerBehavior placement = AdaptiveAI.chooseSurfacePlacement(player, state, level, random);
        if (placement == StalkerBehavior.WINDOW) {
            final BlockPos window = SpawnLocator.findSkylit(player, random, 8, 18);
            if (window != null && spawnAt(player, level, state, window, StalkerBehavior.WINDOW, false)) {
                HorrorNet.sendVignettePulse(player, 20);
                return;
            }
        } else if (placement == StalkerBehavior.BEHIND) {
            final BlockPos behind = SpawnLocator.findBehind(player, random, BEHIND_MIN, BEHIND_MAX);
            if (behind != null && spawnAt(player, level, state, behind, StalkerBehavior.BEHIND, false)) {
                HorrorNet.sendVignettePulse(player, 18);
                return;
            }
        }
        spawnFar(player, level, state, config, random);
    }

    private static void spawnFar(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                 HorrorConfig config, Random random) {
        // Guaranteed at least the vanish radius from EVERY player - the far watcher never pops in close.
        final BlockPos far = SpawnLocator.findSpawnClear(player, random,
                config.entity.spawnDistanceMin, config.entity.spawnDistanceMax, config.entity.despawnTriggerRadius);
        if (far == null) {
            return;
        }
        // Day 1-2: never a daytime black null. From day 3 the chance grows each day up to the cap.
        final int day = com.adaptivehorror.scheduler.DayProgression.dayOf(level);
        final double blackChance = day < 3 ? 0.0
                : Math.min(config.entity.daytimeBlackChanceCap, config.entity.daytimeBlackChancePerDay * (day - 2));
        final boolean black = level.isDay() && random.nextDouble() < blackChance;
        spawnAt(player, level, state, far, StalkerBehavior.FAR, black);
    }

    private static boolean spawnAt(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                   BlockPos pos, StalkerBehavior placement, boolean forceBlack) {
        final StalkerEntity stalker = ModEntities.STALKER.create(level);
        if (stalker == null) {
            return false;
        }
        stalker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        stalker.setNightForm(placement == StalkerBehavior.CAVE || !level.isDay() || forceBlack);
        // Only the distant FAR watcher enforces the hard 25-block vanish at the entity level; the close
        // forms (cave/behind/window/sleep) are meant to be near and use the manager's smaller triggers.
        stalker.setVanishOnApproach(placement == StalkerBehavior.FAR,
                ConfigManager.get().entity.despawnTriggerRadius);
        facePlayer(stalker, player);
        if (!level.addFreshEntity(stalker)) {
            return false;
        }
        state.activeStalkerId = stalker.getUUID();
        state.stalkerBehavior = placement;
        state.stalkerAgeTicks = 0;
        state.stalkerLookTicks = 0;
        state.stalkerBlack = forceBlack;
        state.stalkerRush = false;
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
        state.stalkerRush = false;
        state.stalkerBlack = false;
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
