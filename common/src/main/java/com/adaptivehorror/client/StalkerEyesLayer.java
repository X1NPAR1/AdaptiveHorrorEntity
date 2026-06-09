package com.adaptivehorror.client;

import com.adaptivehorror.Constants;
import com.adaptivehorror.entity.StalkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;

/**
 * Full-bright (emissive) glowing eyes for the night form only. By day the stalker is silent, white
 * and eyeless; at night this layer paints faintly glowing eyes that ignore world lighting.
 */
public final class StalkerEyesLayer extends EyesLayer<StalkerEntity, StalkerModel> {

    private static final RenderType EYES =
            RenderType.eyes(Constants.id("textures/entity/stalker_eyes.png"));

    public StalkerEyesLayer(RenderLayerParent<StalkerEntity, StalkerModel> parent) {
        super(parent);
    }

    @Override
    public RenderType renderType() {
        return EYES;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, StalkerEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isNightForm()) {
            super.render(pose, buffer, packedLight, entity, limbSwing, limbSwingAmount,
                    partialTicks, ageInTicks, netHeadYaw, headPitch);
        }
    }
}
