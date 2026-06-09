package com.adaptivehorror.neoforge.client;

import com.adaptivehorror.Constants;
import com.adaptivehorror.client.ClientHorrorManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * NeoForge game-bus client hooks: advance {@link ClientHorrorManager}, draw the HUD overlays, and
 * apply real world-camera shake via the camera-angles event (no mixin needed on NeoForge).
 */
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class NeoForgeClientGameEvents {

    private NeoForgeClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            ClientHorrorManager.get().tick();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientHorrorManager.get().renderOverlay(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        final ClientHorrorManager mgr = ClientHorrorManager.get();
        if (mgr.isShaking()) {
            event.setYaw(event.getYaw() + mgr.nextShakeYaw());
            event.setPitch(event.getPitch() + mgr.nextShakePitch());
            event.setRoll(event.getRoll() + mgr.nextShakeRoll());
        }
    }
}
