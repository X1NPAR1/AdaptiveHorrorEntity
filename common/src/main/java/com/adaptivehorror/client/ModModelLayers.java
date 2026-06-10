package com.adaptivehorror.client;

import com.adaptivehorror.Constants;
import net.minecraft.client.model.geom.ModelLayerLocation;

/**
 * Model layer locations for the mod's custom entity models. Registered with the actual layer
 * definitions by each loader's client bootstrap (Fabric {@code EntityModelLayerRegistry} /
 * NeoForge {@code EntityRenderersEvent.RegisterLayerDefinitions}).
 */
public final class ModModelLayers {

    public static final ModelLayerLocation STALKER =
            new ModelLayerLocation(Constants.id("stalker"), "main");

    public static final ModelLayerLocation NULL_BOSS =
            new ModelLayerLocation(Constants.id("null_boss"), "main");

    private ModModelLayers() {
    }
}
