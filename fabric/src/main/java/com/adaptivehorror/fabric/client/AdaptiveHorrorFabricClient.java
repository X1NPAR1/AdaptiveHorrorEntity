package com.adaptivehorror.fabric.client;

import com.adaptivehorror.client.ClientHorrorManager;
import com.adaptivehorror.client.FakePlayerRenderer;
import com.adaptivehorror.client.ModModelLayers;
import com.adaptivehorror.client.NullBossModel;
import com.adaptivehorror.client.NullBossRenderer;
import com.adaptivehorror.client.StalkerModel;
import com.adaptivehorror.client.StalkerRenderer;
import com.adaptivehorror.platform.FabricNetworkHelper;
import com.adaptivehorror.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Fabric client entrypoint: registers the model layers, entity renderers, the S2C effect receiver,
 * and the per-frame tick/HUD hooks that drive {@link ClientHorrorManager}. (Camera shake is applied
 * by {@code CameraMixin}.)
 */
public final class AdaptiveHorrorFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.STALKER, StalkerModel::createBodyLayer);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.NULL_BOSS, NullBossModel::createBodyLayer);

        EntityRendererRegistry.register(ModEntities.STALKER, StalkerRenderer::new);
        EntityRendererRegistry.register(ModEntities.FAKE_PLAYER, FakePlayerRenderer::new);
        EntityRendererRegistry.register(ModEntities.NULL_BOSS, NullBossRenderer::new);

        FabricNetworkHelper.registerClientReceiver();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                ClientHorrorManager.get().tick();
            }
        });

        HudRenderCallback.EVENT.register((graphics, deltaTracker) ->
                ClientHorrorManager.get().renderOverlay(graphics));
    }
}
