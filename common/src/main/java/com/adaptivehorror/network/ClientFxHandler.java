package com.adaptivehorror.network;

import com.adaptivehorror.client.ClientHorrorManager;
import com.adaptivehorror.client.DisclaimerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side reader for the {@link HorrorNet#CHANNEL} effect stream. Loader networking glue calls
 * {@link #handle(FriendlyByteBuf)} <em>already on the render thread</em>, so this code may touch
 * {@link Minecraft} directly. It only decodes and forwards to {@link ClientHorrorManager}, keeping
 * presentation logic in one place.
 *
 * <p>This class lives behind a client-only call path and must never be loaded on a dedicated server.
 */
public final class ClientFxHandler {

    private ClientFxHandler() {
    }

    public static void handle(FriendlyByteBuf data) {
        final HorrorNet.FxType type = HorrorNet.FxType.values()[data.readByte()];
        final ClientHorrorManager mgr = ClientHorrorManager.get();

        switch (type) {
            case JUMPSCARE: {
                final int image = data.readVarInt();
                final int sound = data.readVarInt();
                final int duration = data.readVarInt();
                mgr.triggerJumpscare(image, sound, duration);
                break;
            }
            case SOUND_2D: {
                final String path = data.readUtf();
                final float vol = data.readFloat();
                final float pitch = data.readFloat();
                mgr.playSound2D(path, vol, pitch);
                break;
            }
            case SOUND_DIRECTIONAL: {
                final String path = data.readUtf();
                final Vec3 pos = new Vec3(data.readDouble(), data.readDouble(), data.readDouble());
                final float vol = data.readFloat();
                final float pitch = data.readFloat();
                mgr.playSoundAt(path, pos, vol, pitch);
                break;
            }
            case BLACKOUT:
                mgr.startBlackout(data.readVarInt());
                break;
            case VIGNETTE_PULSE:
                mgr.startVignettePulse(data.readVarInt());
                break;
            case GLITCH:
                mgr.startGlitch(data.readVarInt());
                break;
            case MUSIC_DISTORT:
                mgr.distortMusic(data.readVarInt());
                break;
            case SHADOW_GHOST:
                // Reserved: client-rendered single-frame ghost. Handled by ClientHorrorManager tick.
                mgr.spawnShadowGhost();
                break;
            case SHOW_DISCLAIMER:
                Minecraft.getInstance().setScreen(new DisclaimerScreen());
                break;
            case CAMERA_SHAKE: {
                final int duration = data.readVarInt();
                final float intensity = data.readFloat();
                mgr.startShake(duration, intensity);
                break;
            }
            case FORCE_LOOK: {
                final int duration = data.readVarInt();
                final float intensity = data.readFloat();
                mgr.startForcedLook(duration, intensity);
                break;
            }
            case AIM_LOCK:
                mgr.startAimLock(data.readVarInt());
                break;
        }
    }
}
