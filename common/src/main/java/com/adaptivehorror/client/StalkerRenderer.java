package com.adaptivehorror.client;

import com.adaptivehorror.Constants;
import com.adaptivehorror.entity.StalkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the stalker. The texture swaps with the synced day/night form: pure white by day, pure
 * black by night, with a full-bright glowing-eyes layer added for the night form.
 */
public final class StalkerRenderer extends MobRenderer<StalkerEntity, StalkerModel> {

    private static final ResourceLocation WHITE = Constants.id("textures/entity/stalker_white.png");
    private static final ResourceLocation BLACK = Constants.id("textures/entity/stalker_black.png");

    public StalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new StalkerModel(context.bakeLayer(ModModelLayers.STALKER)), 0.0F);
        addLayer(new StalkerEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(StalkerEntity entity) {
        return entity.isNightForm() ? BLACK : WHITE;
    }
}
