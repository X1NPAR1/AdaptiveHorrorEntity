package com.adaptivehorror.scheduler;

import com.adaptivehorror.AdaptiveHorror;
import com.adaptivehorror.ai.BehaviorSampler;
import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.spawn.StalkerManager;
import com.adaptivehorror.world.DisclaimerState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The central server-side clock for all horror activity.
 *
 * <p>Each loader's "server player tick" hook calls {@link #tickPlayer(ServerPlayer)} once per tick
 * per online player - the only entry point. There is no per-entity ticking, no polling thread, and
 * no global scan; work is O(players). Per player, per tick it:
 * <ol>
 *   <li>enforces the disclaimer gate (nothing fires until the player has accepted),</li>
 *   <li>samples behaviour for the adaptive AI,</li>
 *   <li>advances the stalker lifecycle and the travel-distance event,</li>
 *   <li>runs the two periodic ambient-audio timers,</li>
 *   <li>rolls a single weighted, day-gated event on a randomised interval.</li>
 * </ol>
 */
public final class HorrorScheduler {

    private static final Map<UUID, PlayerHorrorState> STATES = new ConcurrentHashMap<>();
    private static final Random RNG = new Random();

    private HorrorScheduler() {
    }

    /** Called once per server tick (before the per-player ticks). Advances the "null" presence. */
    public static void tickServer(MinecraftServer server) {
        if (ConfigManager.get().enabled) {
            NullManager.tick(server);
        }
    }

    /** Called on player login: shows the disclaimer if needed, and syncs the null tab entry. */
    public static void onPlayerJoin(ServerPlayer player) {
        final DisclaimerState disclaimer = DisclaimerState.get(player.serverLevel());
        if (!disclaimer.hasAccepted(player.getUUID())) {
            HorrorNet.sendDisclaimer(player);
        }
        NullManager.syncTo(player);
    }

    public static void tickPlayer(ServerPlayer player) {
        final HorrorConfig config = ConfigManager.get();
        if (!config.enabled) {
            return;
        }

        // Gate 1: no horror until the player has accepted the disclaimer in this world.
        if (!DisclaimerState.get(player.serverLevel()).hasAccepted(player.getUUID())) {
            return;
        }
        // Gate 2: the haunting only begins once "null" has joined.
        if (config.nullEntity.enabled && !NullManager.hasJoined()) {
            return;
        }

        final PlayerHorrorState state =
                STATES.computeIfAbsent(player.getUUID(), PlayerHorrorState::new);

        BehaviorSampler.sample(player, state);

        final int day = DayProgression.dayOf(player.level());
        final double intensity = DayProgression.intensity(day, config);

        StalkerManager.tick(player, state, config, RNG);
        tickTravelSound(player, state, config, intensity);
        PeriodicAudioScheduler.tick(player, state, config, RNG);
        tickEventRoll(player, state, config, day, intensity);
    }

    public static void removePlayer(ServerPlayer player) {
        final PlayerHorrorState state = STATES.remove(player.getUUID());
        if (state != null) {
            StalkerManager.despawn(player.serverLevel(), state);
        }
    }

    public static void clear() {
        STATES.clear();
    }

    /** Resolves (creating if needed) the per-player state. Used by debug commands. */
    public static PlayerHorrorState getOrCreateState(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), PlayerHorrorState::new);
    }

    /** Shared server-thread RNG, exposed for command-driven one-off actions. */
    public static Random rng() {
        return RNG;
    }

    // --- event roll ----------------------------------------------------------------------------

    private static void tickEventRoll(ServerPlayer player, PlayerHorrorState state,
                                      HorrorConfig config, int day, double intensity) {
        final long now = player.level().getGameTime();
        if (state.nextSchedulerTick == 0L) {
            state.nextSchedulerTick = scheduleNext(now, config, intensity);
            return;
        }
        if (now < state.nextSchedulerTick) {
            return;
        }
        EventRegistry.maybeRunOne(new EventContext(player, state, config, RNG, day, intensity));
        state.nextSchedulerTick = scheduleNext(now, config, intensity);
    }

    /** Randomised interval, shortened as intensity rises (more frequent late-game). */
    private static long scheduleNext(long now, HorrorConfig config, double intensity) {
        final int min = config.scheduler.baseIntervalSecondsMin;
        final int max = config.scheduler.baseIntervalSecondsMax;
        final int span = Math.max(1, max - min);
        final double scale = 1.0 / Math.max(0.25, intensity);
        final long seconds = (long) ((min + RNG.nextInt(span)) * scale);
        return now + seconds * 20L;
    }

    // --- travel-distance event -----------------------------------------------------------------

    private static void tickTravelSound(ServerPlayer player, PlayerHorrorState state,
                                        HorrorConfig config, double intensity) {
        if (!config.features.audioEvents) {
            return;
        }
        if (state.travelSinceSound < config.scheduler.travelSoundBlocks) {
            return;
        }
        state.travelSinceSound = 0.0;

        final EffectDispatcher dispatcher = EffectDispatcher.ACTIVE.get();
        dispatcher.playTravelSound(player);

        final double jumpscareChance = config.scheduler.travelJumpscareChance * intensity;
        if (config.features.jumpscares && RNG.nextDouble() < jumpscareChance && offCooldown(player, state, config)) {
            state.lastJumpscareTick = player.level().getGameTime();
            dispatcher.travelJumpscare(player);
        }

        if (config.debugMode) {
            AdaptiveHorror.LOGGER.info("Travel event fired for {}", player.getGameProfile().getName());
        }
    }

    private static boolean offCooldown(ServerPlayer player, PlayerHorrorState state, HorrorConfig config) {
        final long cooldownTicks = (long) config.scheduler.jumpscareCooldownSeconds * 20L;
        return player.level().getGameTime() - state.lastJumpscareTick >= cooldownTicks;
    }
}
