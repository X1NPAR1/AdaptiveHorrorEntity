package com.adaptivehorror.event;

import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.world.DisclaimerState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides <em>which</em> player the shared, server-wide set-pieces focus on.
 *
 * <p>Per-player hauntings are already distributed (the scheduler ticks each player independently), but
 * the rare collective beats - the global blackout, a shared jumpscare - need to single out one
 * "primary victim". Picking the same player every time would be unfair and predictable, so selection is
 * a <b>weighted rotation</b>: a player's weight grows the longer it has been since they were last
 * singled out, so attention spreads across the server while still being random. Only players who have
 * accepted the disclaimer (and while "null" is present) are eligible.
 */
public final class TargetingManager {

    /** Baseline weight so a just-targeted player still has a small chance to be picked again. */
    private static final double BASE_WEIGHT = 200.0;
    /** Weight gained per tick since last targeted, capped so it cannot run away unbounded. */
    private static final double MAX_AGE_WEIGHT = 20L * 600; // ~10 minutes of accrual

    private static final Map<UUID, Long> LAST_TARGETED = new ConcurrentHashMap<>();

    private TargetingManager() {
    }

    public static void reset() {
        LAST_TARGETED.clear();
    }

    /** Players currently eligible to be haunted (accepted disclaimer, null present). */
    public static List<ServerPlayer> eligible(MinecraftServer server) {
        if (!NullManager.hasJoined()) {
            return List.of();
        }
        final DisclaimerState disclaimer = DisclaimerState.get(server.overworld());
        final List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (disclaimer.hasAccepted(player.getUUID())) {
                out.add(player);
            }
        }
        return out;
    }

    /**
     * Picks one eligible player by weighted rotation and records the choice. Returns {@code null} if
     * nobody is eligible.
     */
    @Nullable
    public static ServerPlayer pickPrimary(MinecraftServer server, Random random) {
        final List<ServerPlayer> pool = eligible(server);
        if (pool.isEmpty()) {
            return null;
        }
        final long now = server.overworld().getGameTime();
        double total = 0.0;
        final double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            final Long last = LAST_TARGETED.get(pool.get(i).getUUID());
            final double age = last == null ? MAX_AGE_WEIGHT : Math.min(MAX_AGE_WEIGHT, now - last);
            weights[i] = BASE_WEIGHT + age;
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights[i];
            if (roll <= 0.0) {
                final ServerPlayer chosen = pool.get(i);
                LAST_TARGETED.put(chosen.getUUID(), now);
                return chosen;
            }
        }
        final ServerPlayer fallback = pool.get(pool.size() - 1);
        LAST_TARGETED.put(fallback.getUUID(), now);
        return fallback;
    }
}
