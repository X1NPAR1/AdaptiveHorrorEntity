package com.adaptivehorror.npc;

import com.adaptivehorror.AdaptiveHorror;
import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.world.DisclaimerState;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Constructor;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Drives the "null" presence: some minutes after the player accepts the disclaimer, a fake player
 * named {@code null} silently joins - a yellow chat line plus a tab-list entry - and only then does
 * the haunting begin. The stalking entity and every event are gated behind {@link #hasJoined()}.
 *
 * <p>This is intentionally a per-server singleton held in memory: each play session, null "joins"
 * once. The fake player is not a real {@link ServerPlayer}; its tab entry is injected via a
 * hand-built {@link ClientboundPlayerInfoUpdatePacket} (constructor opened by the access widener).
 */
public final class NullManager {

    /** Fixed identity for the apparition. The all-zero UUID reads, fittingly, as "null". */
    public static final UUID NULL_UUID = new UUID(0L, 0L);

    private static final Random RNG = new Random();

    /**
     * The packet's {@code (EnumSet, List)} constructor is private (only the decoder uses it). We
     * resolve it reflectively by parameter types - stable across mappings - to inject a fake entry
     * without an access widener (which complicates dev runs).
     */
    private static final Constructor<ClientboundPlayerInfoUpdatePacket> PACKET_CTOR = resolveCtor();

    @SuppressWarnings("unchecked")
    private static Constructor<ClientboundPlayerInfoUpdatePacket> resolveCtor() {
        try {
            final Constructor<?> c =
                    ClientboundPlayerInfoUpdatePacket.class.getDeclaredConstructor(EnumSet.class, List.class);
            c.setAccessible(true);
            return (Constructor<ClientboundPlayerInfoUpdatePacket>) c;
        } catch (Exception e) {
            AdaptiveHorror.LOGGER.error("Could not resolve player-info packet constructor; "
                    + "the null tab entry will be unavailable", e);
            return null;
        }
    }

    private static volatile boolean joined;
    private static long joinAtTick = -1L;
    private static long lastProcessedTick = Long.MIN_VALUE;

    private NullManager() {
    }

    public static boolean hasJoined() {
        return joined;
    }

    /** Resets state (call on server stop) so the next session re-arms the presence. */
    public static void reset() {
        joined = false;
        joinAtTick = -1L;
        lastProcessedTick = Long.MIN_VALUE;
    }

    /** Server-tick driver. Idempotent within a tick. Arms the timer once a player has accepted. */
    public static void tick(MinecraftServer server) {
        final HorrorConfig config = ConfigManager.get();
        if (!config.enabled || !config.nullEntity.enabled) {
            return;
        }
        final long now = server.overworld().getGameTime();
        if (now == lastProcessedTick) {
            return;
        }
        lastProcessedTick = now;

        if (joined) {
            // Re-assert the tab entry every 5s so clients that prune unknown entries keep showing it.
            if (now % 100L == 0L) {
                final ClientboundPlayerInfoUpdatePacket packet = buildAddPacket();
                if (packet != null) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.connection.send(packet);
                    }
                }
            }
            return;
        }

        if (joinAtTick < 0L) {
            if (anyPlayerAccepted(server)) {
                final int span = Math.max(1,
                        config.nullEntity.joinDelayMaxSeconds - config.nullEntity.joinDelayMinSeconds);
                final int seconds = config.nullEntity.joinDelayMinSeconds + RNG.nextInt(span);
                joinAtTick = now + (long) seconds * 20L;
            }
            return;
        }
        if (now >= joinAtTick) {
            doJoin(server);
        }
    }

    /** Immediately performs the join (debug command / forced trigger). */
    public static void forceJoin(MinecraftServer server) {
        if (!joined) {
            doJoin(server);
        }
    }

    /** Sends the existing tab entry to a player who joined after null (so they also see it). */
    public static void syncTo(ServerPlayer player) {
        if (joined) {
            final ClientboundPlayerInfoUpdatePacket packet = buildAddPacket();
            if (packet != null) {
                player.connection.send(packet);
            }
        }
    }

    // --- internals -----------------------------------------------------------------------------

    private static boolean anyPlayerAccepted(MinecraftServer server) {
        final DisclaimerState disclaimer = DisclaimerState.get(server.overworld());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (disclaimer.hasAccepted(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    private static void doJoin(MinecraftServer server) {
        joined = true;
        final Component message = Component.translatable("adaptivehorror.null.join")
                .withStyle(ChatFormatting.YELLOW);
        final ClientboundPlayerInfoUpdatePacket packet = buildAddPacket();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            if (packet != null) {
                player.connection.send(packet);
            }
        }
    }

    private static ClientboundPlayerInfoUpdatePacket buildAddPacket() {
        if (PACKET_CTOR == null) {
            return null;
        }
        final GameProfile profile = new GameProfile(NULL_UUID, ConfigManager.get().nullEntity.name);
        final String textureValue = ConfigManager.get().nullEntity.textureValue;
        if (textureValue != null && !textureValue.isEmpty()) {
            final String sig = ConfigManager.get().nullEntity.textureSignature;
            profile.getProperties().put("textures",
                    new Property("textures", textureValue, sig == null || sig.isEmpty() ? null : sig));
        }

        final ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                NULL_UUID, profile, true, 5, GameType.SURVIVAL,
                Component.literal(ConfigManager.get().nullEntity.name), null);

        try {
            return PACKET_CTOR.newInstance(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                    List.of(entry));
        } catch (Exception e) {
            AdaptiveHorror.LOGGER.error("Failed to build null tab-list packet", e);
            return null;
        }
    }
}
