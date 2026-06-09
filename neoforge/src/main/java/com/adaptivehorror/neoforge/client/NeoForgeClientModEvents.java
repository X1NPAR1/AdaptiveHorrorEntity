package com.adaptivehorror.neoforge.client;

import com.adaptivehorror.Constants;
import com.adaptivehorror.client.FakePlayerRenderer;
import com.adaptivehorror.client.ModModelLayers;
import com.adaptivehorror.client.StalkerModel;
import com.adaptivehorror.client.StalkerRenderer;
import com.adaptivehorror.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** NeoForge mod-bus client setup: entity renderers and their baked model layers. */
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NeoForgeClientModEvents {

    private NeoForgeClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STALKER, StalkerRenderer::new);
        event.registerEntityRenderer(ModEntities.FAKE_PLAYER, FakePlayerRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.STALKER, StalkerModel::createBodyLayer);
    }
}
