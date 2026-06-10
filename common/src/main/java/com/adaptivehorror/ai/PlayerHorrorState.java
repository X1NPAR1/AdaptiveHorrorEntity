package com.adaptivehorror.ai;

import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Per-player mutable horror state, held server-side for the duration of a session.
 *
 * <p>This is the single source of truth the scheduler and managers read/write. Bundling it here -
 * rather than smearing fields across managers - keeps each manager stateless and testable, and makes
 * multiplayer trivial (one state per player, no shared globals).
 */
public final class PlayerHorrorState {

    public final UUID playerId;

    /** The currently-spawned stalker for this player, or null if none is present. */
    @Nullable
    public UUID activeStalkerId;

    /** The behaviour archetype the active stalker committed to this encounter. */
    @Nullable
    public com.adaptivehorror.spawn.StalkerBehavior stalkerBehavior;

    /** Ticks the active stalker has existed - used to time-out rushers/watchers. */
    public int stalkerAgeTicks;

    /** Consecutive ticks the player has been looking at the active stalker (the enderman rule). */
    public int stalkerLookTicks;

    /** Game-time tick before which no new stalker may spawn (the 15-60s gap after one vanishes). */
    public long nextStalkerSpawnTick;

    /** How many times the stalker has been triggered - the entity grows bolder with each one. */
    public int encounters;

    /** This cave null charges the player (a tunnel ambush) instead of just standing and watching. */
    public boolean stalkerRush;

    /** This null is the black, more-aggressive form even though it spawned in daylight. */
    public boolean stalkerBlack;

    /** Game-time tick at which a scheduled jumpscare-kill lands (0 = none). */
    public long pendingKillTick;

    /** Last sampled player position, for incremental horizontal-travel accounting. */
    @Nullable
    public Vec3 lastSampledPos;

    /** Horizontal blocks travelled since the stalker last relocated. */
    public double travelSinceRelocate;

    /** Horizontal blocks travelled since the last "120-block" travel sound. */
    public double travelSinceSound;

    /** Game-time tick of the last full-screen jumpscare (for the global cooldown). */
    public long lastJumpscareTick = Long.MIN_VALUE;

    /** Tick at which the scheduler may next roll an ambient event. */
    public long nextSchedulerTick;

    /** Independent timers (game-time ticks) for the two recurring ambient audio cues. 0 = unset. */
    public long nextScaryTick;
    public long nextISeeYouTick;

    /** Timer for the frequent rotating ambient one-shot (travel/scary/iseeyou). 0 = unset. */
    public long nextAmbientTick;

    /** Next tick at which the inventory-drop attempt rolls. 0 = unset. */
    public long nextInventoryDropTick;

    /** Live "watcher" nulls (the post-day-5 group that stands far off and stares). */
    public final java.util.List<UUID> watcherIds = new java.util.ArrayList<>();
    /** How many watchers to maintain this cycle (0 = unset). */
    public int watcherTargetCount;
    /** Rate limiter for spawning watchers. */
    public long nextWatcherSpawnTick;

    /** Previous head yaw, used to detect sharp "check behind" turns. */
    public float lastYaw = Float.NaN;

    /** Behaviour observations that feed the adaptive AI. */
    public final BehaviorTracker behavior = new BehaviorTracker();

    /** Deferred horror steps (approaching footsteps, countdowns, ...), drained by the scheduler. */
    public final java.util.List<com.adaptivehorror.scheduler.ScheduledAction> scheduled =
            new java.util.ArrayList<>();

    public PlayerHorrorState(UUID playerId) {
        this.playerId = playerId;
    }
}
