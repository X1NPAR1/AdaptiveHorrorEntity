package com.adaptivehorror.scheduler;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.HorrorConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/**
 * Immutable snapshot passed to every {@link HorrorEvent} when it runs. Bundling the player, world,
 * per-player state, config, RNG and the current progression values keeps event signatures uniform
 * and makes events trivially testable in isolation.
 */
public final class EventContext {

    public final ServerPlayer player;
    public final ServerLevel level;
    public final PlayerHorrorState state;
    public final HorrorConfig config;
    public final Random random;

    /** In-game day index (0-based) and the resulting intensity scalar (0..1+). */
    public final int day;
    public final double intensity;

    /** Whether the player is underground - caves get more frequent, more physical hauntings. */
    public final boolean underground;

    public EventContext(ServerPlayer player, PlayerHorrorState state, HorrorConfig config,
                        Random random, int day, double intensity, boolean underground) {
        this.player = player;
        this.level = player.serverLevel();
        this.state = state;
        this.config = config;
        this.random = random;
        this.day = day;
        this.intensity = intensity;
        this.underground = underground;
    }
}
