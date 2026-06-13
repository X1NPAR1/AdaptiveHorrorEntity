package com.adaptivehorror.network;

import com.adaptivehorror.Constants;
import com.adaptivehorror.platform.Services;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's single S2C channel and its typed senders.
 *
 * <p>Rather than a class-per-packet sprawl, every client effect travels on one channel as a
 * discriminated payload: a leading {@link FxType} ordinal followed by type-specific fields. This
 * keeps the loader networking glue to a single registration and centralises the wire format so the
 * server senders and the {@link ClientFxHandler} reader can never drift apart.
 */
public final class HorrorNet {

    public static final ResourceLocation CHANNEL = Constants.id("fx");

    private HorrorNet() {
    }

    /** Effect discriminator. Ordinal is the wire tag - only ever append, never reorder. */
    public enum FxType {
        JUMPSCARE,
        SOUND_2D,
        SOUND_DIRECTIONAL,
        BLACKOUT,
        VIGNETTE_PULSE,
        GLITCH,
        MUSIC_DISTORT,
        SHADOW_GHOST,
        SHOW_DISCLAIMER,
        CAMERA_SHAKE,
        FORCE_LOOK,
        AIM_LOCK,
        BLOOD_MOON
    }

    private static FriendlyByteBuf buf(FxType type) {
        final FriendlyByteBuf b = Services.NETWORK.createBuffer();
        b.writeByte(type.ordinal());
        return b;
    }

    // --- typed senders -------------------------------------------------------------------------

    /** Fullscreen jumpscare: image index + sound index + on-screen duration in ticks. */
    public static void sendJumpscare(ServerPlayer player, int imageIndex, int soundIndex, int durationTicks) {
        final FriendlyByteBuf b = buf(FxType.JUMPSCARE);
        b.writeVarInt(imageIndex);
        b.writeVarInt(soundIndex);
        b.writeVarInt(durationTicks);
        Services.NETWORK.sendFx(player, b);
    }

    /** Non-positional sound by registered path, e.g. {@code "scary_ambient"}. */
    public static void sendSound2D(ServerPlayer player, String soundPath, float volume, float pitch) {
        final FriendlyByteBuf b = buf(FxType.SOUND_2D);
        b.writeUtf(soundPath);
        b.writeFloat(volume);
        b.writeFloat(pitch);
        Services.NETWORK.sendFx(player, b);
    }

    /** Positional sound so the player cannot place the source. */
    public static void sendSoundAt(ServerPlayer player, String soundPath, Vec3 pos, float volume, float pitch) {
        final FriendlyByteBuf b = buf(FxType.SOUND_DIRECTIONAL);
        b.writeUtf(soundPath);
        b.writeDouble(pos.x);
        b.writeDouble(pos.y);
        b.writeDouble(pos.z);
        b.writeFloat(volume);
        b.writeFloat(pitch);
        Services.NETWORK.sendFx(player, b);
    }

    public static void sendBlackout(ServerPlayer player, int durationTicks) {
        final FriendlyByteBuf b = buf(FxType.BLACKOUT);
        b.writeVarInt(durationTicks);
        Services.NETWORK.sendFx(player, b);
    }

    public static void sendVignettePulse(ServerPlayer player, int durationTicks) {
        final FriendlyByteBuf b = buf(FxType.VIGNETTE_PULSE);
        b.writeVarInt(durationTicks);
        Services.NETWORK.sendFx(player, b);
    }

    public static void sendGlitch(ServerPlayer player, int durationTicks) {
        final FriendlyByteBuf b = buf(FxType.GLITCH);
        b.writeVarInt(durationTicks);
        Services.NETWORK.sendFx(player, b);
    }

    public static void sendMusicDistort(ServerPlayer player, int durationTicks) {
        final FriendlyByteBuf b = buf(FxType.MUSIC_DISTORT);
        b.writeVarInt(durationTicks);
        Services.NETWORK.sendFx(player, b);
    }

    public static void sendDisclaimer(ServerPlayer player) {
        Services.NETWORK.sendFx(player, buf(FxType.SHOW_DISCLAIMER));
    }

    /** Real world-camera shake: duration in ticks + intensity (degrees of jitter, ~0.5-3). */
    public static void sendCameraShake(ServerPlayer player, int durationTicks, float intensity) {
        final FriendlyByteBuf b = buf(FxType.CAMERA_SHAKE);
        b.writeVarInt(durationTicks);
        b.writeFloat(intensity);
        Services.NETWORK.sendFx(player, b);
    }

    /**
     * Forced-look (#16): for {@code durationTicks} the client wrenches the view to a succession of
     * disorienting directions with shake. Strictly timer-bounded on the client, so control always
     * returns - it can never permanently break the camera.
     */
    public static void sendForcedLook(ServerPlayer player, int durationTicks, float intensity) {
        final FriendlyByteBuf b = buf(FxType.FORCE_LOOK);
        b.writeVarInt(durationTicks);
        b.writeFloat(intensity);
        Services.NETWORK.sendFx(player, b);
    }

    /**
     * Aim-lock (#17): for {@code durationTicks} the client drags the view to centre on the nearest
     * null. Also timer-bounded; the player can fight it but regains full control when it ends.
     */
    public static void sendAimLock(ServerPlayer player, int durationTicks, boolean hard) {
        final FriendlyByteBuf b = buf(FxType.AIM_LOCK);
        b.writeVarInt(durationTicks);
        b.writeBoolean(hard);
        Services.NETWORK.sendFx(player, b);
    }

    /** Blood moon: a sustained red wash over the player's screen for {@code durationTicks}. */
    public static void sendBloodMoon(ServerPlayer player, int durationTicks) {
        final FriendlyByteBuf b = buf(FxType.BLOOD_MOON);
        b.writeVarInt(durationTicks);
        Services.NETWORK.sendFx(player, b);
    }

    // --- broadcast senders (shared, multiplayer set-pieces) ------------------------------------

    /**
     * Server-wide variants used by the "major events affect everyone" beats. A single null appearing
     * is personal; a death, a global blackout, or a collective jumpscare is felt by the whole server.
     * Each loops the online players and reuses the per-player sender (buffers are consumed per send).
     */
    public static void broadcastJumpscare(MinecraftServer server, int imageIndex, int soundIndex, int durationTicks) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendJumpscare(player, imageIndex, soundIndex, durationTicks);
        }
    }

    public static void broadcastSound2D(MinecraftServer server, String soundPath, float volume, float pitch) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSound2D(player, soundPath, volume, pitch);
        }
    }

    public static void broadcastVignettePulse(MinecraftServer server, int durationTicks) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendVignettePulse(player, durationTicks);
        }
    }

    /** Like {@link #broadcastSound2D} but skips one player - "someone else was taken". */
    public static void broadcastSound2DExcept(MinecraftServer server, ServerPlayer except,
                                              String soundPath, float volume, float pitch) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != except) {
                sendSound2D(player, soundPath, volume, pitch);
            }
        }
    }

    public static void broadcastVignettePulseExcept(MinecraftServer server, ServerPlayer except, int durationTicks) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != except) {
                sendVignettePulse(player, durationTicks);
            }
        }
    }

    // --- C2S (client -> server control) --------------------------------------------------------

    /** Client-to-server opcodes. Ordinal is the wire tag - append only. */
    public enum C2SType {
        DISCLAIMER_ACCEPTED
    }

    /** Called on the client when the player accepts the disclaimer. */
    public static void sendDisclaimerAccepted() {
        final FriendlyByteBuf b = Services.NETWORK.createBuffer();
        b.writeByte(C2SType.DISCLAIMER_ACCEPTED.ordinal());
        Services.NETWORK.sendToServer(b);
    }
}
