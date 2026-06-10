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

    /** Game-time tick at which a revealed/teleported stalker should vanish (0 = not revealing). */
    public long revealEndTick;

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

    /** Next tick at which the inventory-drop attempt rolls. 0 = unset. */
    public long nextInventoryDropTick;

    /** Previous head yaw, used to detect sharp "check behind" turns. */
    public float lastYaw = Float.NaN;

    /** Behaviour observations that feed the adaptive AI. */
    public final BehaviorTracker behavior = new BehaviorTracker();

    public PlayerHorrorState(UUID playerId) {
        this.playerId = playerId;
    }
}
