package com.adaptivehorror.network;

import com.adaptivehorror.Constants;
import com.adaptivehorror.platform.Services;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
        CAMERA_SHAKE
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
