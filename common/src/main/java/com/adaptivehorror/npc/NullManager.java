package com.adaptivehorror.npc;

import com.adaptivehorror.AdaptiveHorror;
import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.DayProgression;
import com.adaptivehorror.world.DisclaimerState;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
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
 * Drives the "null" presence as if it were a real player connecting and disconnecting.
 *
 * <p>Some minutes after the player accepts the disclaimer, {@code null} "joins" (yellow chat line +
 * tab entry), unlocking the haunting. From then on it behaves like a flaky player: it occasionally
 * "leaves the server" (the chat says so, the tab entry disappears, and the haunting goes quiet) and
 * then rejoins a few minutes later. This rhythm of presence and absence is far more unnerving than a
 * constant.
 */
public final class NullManager {

    // A fixed, NON-ZERO UUID. The all-zero UUID is treated specially by the vanilla client and its tab
    // entry was being silently dropped - which is why "null joined" showed in chat but never in the list.
    public static final UUID NULL_UUID = UUID.fromString("9b3a7e2d-1c4f-4a6b-8e1d-000000000001");

    private enum State { NOT_JOINED, PRESENT, AWAY }

    private static final Random RNG = new Random();

    /** The packet's {@code (EnumSet, List)} ctor is private; resolved reflectively by parameter types. */
    private static final Constructor<ClientboundPlayerInfoUpdatePacket> PACKET_CTOR = resolveCtor();

    private static volatile State state = State.NOT_JOINED;
    private static long timerTick = -1L;        // join / leave / rejoin deadline depending on state
    private static long lastProcessedTick = Long.MIN_VALUE;

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

    private NullManager() {
    }

    /** True only while null is actually present - the gate for the whole haunting. */
    public static boolean hasJoined() {
        return state == State.PRESENT;
    }

    public static void reset() {
        state = State.NOT_JOINED;
        timerTick = -1L;
        lastProcessedTick = Long.MIN_VALUE;
    }

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

        switch (state) {
            case NOT_JOINED -> {
                if (timerTick < 0L) {
                    if (anyPlayerAccepted(server)) {
                        timerTick = now + seconds(config.nullEntity.joinDelayMinSeconds,
                                config.nullEntity.joinDelayMaxSeconds);
                    }
                } else if (now >= timerTick) {
                    join(server, config, now);
                }
            }
            case PRESENT -> {
                if (now % 100L == 0L) {
                    sendToAll(server, buildAddPacket()); // re-assert the tab entry every 5s
                }
                if (now >= timerTick) {
                    // After the "stay forever" day, null never leaves again.
                    final int day = DayProgression.dayOf(server.overworld());
                    if (day >= config.nullEntity.stayForeverFromDay) {
                        timerTick = now + 20L * 600L; // far out; it simply never leaves
                    } else if (!server.overworld().isDay()) {
                        // Never leaves at night - wait and re-check until dawn.
                        timerTick = now + 20L * 20L;
                    } else {
                        leave(server, config, now); // it leaves in the morning
                    }
                }
            }
            case AWAY -> {
                if (now >= timerTick) {
                    join(server, config, now);
                }
            }
        }
    }

    /** Immediately joins (debug). */
    public static void forceJoin(MinecraftServer server) {
        if (state != State.PRESENT) {
            join(server, ConfigManager.get(), server.overworld().getGameTime());
        }
    }

    /** Sends the current presence to a player who just connected. */
    public static void syncTo(ServerPlayer player) {
        if (state == State.PRESENT) {
            final ClientboundPlayerInfoUpdatePacket packet = buildAddPacket();
            if (packet != null) {
                player.connection.send(packet);
            }
        }
    }

    // --- transitions ---------------------------------------------------------------------------

    private static void join(MinecraftServer server, HorrorConfig config, long now) {
        state = State.PRESENT;
        timerTick = now + seconds(config.nullEntity.presentMinSeconds, config.nullEntity.presentMaxSeconds);
        announce(server, "adaptivehorror.null.join");
        sendToAll(server, buildAddPacket());
    }

    private static void leave(MinecraftServer server, HorrorConfig config, long now) {
        state = State.AWAY;
        timerTick = now + seconds(config.nullEntity.awayMinSeconds, config.nullEntity.awayMaxSeconds);
        announce(server, "adaptivehorror.null.leave");
        final ClientboundPlayerInfoRemovePacket remove = new ClientboundPlayerInfoRemovePacket(List.of(NULL_UUID));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(remove);
        }
    }

    private static void announce(MinecraftServer server, String key) {
        final Component message = Component.translatable(key).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    private static void sendToAll(MinecraftServer server, ClientboundPlayerInfoUpdatePacket packet) {
        if (packet == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    private static boolean anyPlayerAccepted(MinecraftServer server) {
        final DisclaimerState disclaimer = DisclaimerState.get(server.overworld());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (disclaimer.hasAccepted(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    private static long seconds(int min, int max) {
        final int span = Math.max(1, max - min);
        return (long) (min + RNG.nextInt(span)) * 20L;
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
