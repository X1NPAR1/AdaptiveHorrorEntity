package com.adaptivehorror.scheduler;

import com.adaptivehorror.AdaptiveHorror;
import com.adaptivehorror.ai.BehaviorSampler;
import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.event.AssaultManager;
import com.adaptivehorror.event.InventoryDropManager;
import com.adaptivehorror.event.MobLockManager;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.spawn.StalkerManager;
import com.adaptivehorror.spawn.WatcherManager;
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

    /** Called once per server tick (before the per-player ticks). Drives server-global systems. */
    public static void tickServer(MinecraftServer server) {
        if (ConfigManager.get().enabled) {
            NullManager.tick(server);
            MobLockManager.tick(server);
            AssaultManager.tick(server);
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

        final PlayerHorrorState state =
                STATES.computeIfAbsent(player.getUUID(), PlayerHorrorState::new);

        // Deferred steps (totem ritual lightning/boss spawn, multi-beat events) must always drain,
        // even before null has joined or after it has been defeated - they are not gated by the haunting.
        drainScheduled(player, state);

        // Gate 2: the haunting itself only begins once "null" has joined.
        if (config.nullEntity.enabled && !NullManager.hasJoined()) {
            return;
        }

        BehaviorSampler.sample(player, state);

        final int day = DayProgression.dayOf(player.level());
        final double intensity = DayProgression.intensity(day, config);

        StalkerManager.tick(player, state, config, RNG);
        WatcherManager.tick(player, state, config, RNG);
        tickTravelSound(player, state, config, intensity);
        PeriodicAudioScheduler.tick(player, state, config, RNG);
        InventoryDropManager.tick(player, state, config, RNG);
        tickEventRoll(player, state, config, day, intensity);
        tickEnvEvent(player, state, config, day, intensity);
    }

    public static void removePlayer(ServerPlayer player) {
        final PlayerHorrorState state = STATES.remove(player.getUUID());
        if (state != null) {
            StalkerManager.despawn(player.serverLevel(), state);
            WatcherManager.despawnAll(player.serverLevel(), state);
        }
    }

    public static void clear() {
        STATES.clear();
    }

    /** Runs any deferred horror steps whose time has come. */
    private static void drainScheduled(ServerPlayer player, PlayerHorrorState state) {
        if (state.scheduled.isEmpty()) {
            return;
        }
        final long now = player.level().getGameTime();
        state.scheduled.removeIf(a -> {
            if (now < a.fireTick) {
                return false;
            }
            try {
                a.action.run();
            } catch (Exception e) {
                AdaptiveHorror.LOGGER.debug("Scheduled action failed", e);
            }
            return true;
        });
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
        final boolean underground = com.adaptivehorror.util.Locations.isUnderground(player);
        if (state.nextSchedulerTick == 0L) {
            state.nextSchedulerTick = scheduleNext(now, config, underground);
            return;
        }
        if (now < state.nextSchedulerTick) {
            return;
        }
        state.nextSchedulerTick = scheduleNext(now, config, underground);
        // Fixed cadence: a single 25% roll each interval. Intensity no longer gates this clock.
        if (RNG.nextDouble() < config.scheduler.eventChance) {
            EventRegistry.maybeRunOne(new EventContext(player, state, config, RNG, day, intensity, underground));
        }
    }

    /**
     * The guaranteed environmental beat (#13): on a fixed clock (every {@code envEventIntervalSeconds},
     * halved underground) roll {@code envEventChance} for a physical tamper - a block crumbling, a torch
     * snuffing out, a door swinging. Separate from the weighted roll so these reliably keep happening.
     */
    private static void tickEnvEvent(ServerPlayer player, PlayerHorrorState state,
                                     HorrorConfig config, int day, double intensity) {
        if (!config.features.worldManipulation) {
            return;
        }
        final long now = player.level().getGameTime();
        final boolean underground = com.adaptivehorror.util.Locations.isUnderground(player);
        if (state.nextEnvEventTick == 0L) {
            final int secs = underground ? config.scheduler.envEventIntervalSeconds / 2
                    : config.scheduler.envEventIntervalSeconds;
            state.nextEnvEventTick = now + (long) secs * 20L;
            return;
        }
        if (now < state.nextEnvEventTick) {
            return;
        }
        final int secs = underground ? config.scheduler.envEventIntervalSeconds / 2
                : config.scheduler.envEventIntervalSeconds;
        state.nextEnvEventTick = now + (long) secs * 20L;
        if (RNG.nextDouble() < config.scheduler.envEventChance) {
            EventRegistry.runById("world_manipulation",
                    new EventContext(player, state, config, RNG, day, intensity, underground));
        }
    }

    /** Fixed cadence: every {@code eventIntervalSeconds} (halved underground - caves try twice as often). */
    private static long scheduleNext(long now, HorrorConfig config, boolean underground) {
        final int secs = underground ? Math.max(10, config.scheduler.eventIntervalSeconds / 2)
                : config.scheduler.eventIntervalSeconds;
        return now + (long) secs * 20L;
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
