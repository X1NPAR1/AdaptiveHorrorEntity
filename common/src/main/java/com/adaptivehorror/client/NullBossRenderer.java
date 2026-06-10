package com.adaptivehorror.client;

import com.adaptivehorror.Constants;
import com.adaptivehorror.entity.NullBossEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the null boss: the black stalker silhouette, scaled up to a looming, oversized menace.
 */
public final class NullBossRenderer extends MobRenderer<NullBossEntity, NullBossModel> {

    private static final ResourceLocation BLACK = Constants.id("textures/entity/stalker_black.png");

    public NullBossRenderer(EntityRendererProvider.Context context) {
        super(context, new NullBossModel(context.bakeLayer(ModModelLayers.NULL_BOSS)), 0.7F);
        this.shadowRadius = 0.9F;
    }

    @Override
    protected void scale(NullBossEntity entity, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
        pose.scale(2.2F, 2.2F, 2.2F); // a towering presence
    }

    @Override
    public ResourceLocation getTextureLocation(NullBossEntity entity) {
        return BLACK;
    }
}
