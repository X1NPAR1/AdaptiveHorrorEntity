package com.adaptivehorror.client;

import com.adaptivehorror.entity.FakePlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the {@link FakePlayerEntity} using the local player's own skin, so the apparition looks
 * exactly like the person observing it. Falls back to the default skin if the local skin is
 * unavailable (e.g. early in the session).
 */
public final class FakePlayerRenderer extends MobRenderer<FakePlayerEntity, PlayerModel<FakePlayerEntity>> {

    public FakePlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(FakePlayerEntity entity) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.getSkin().texture();
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }
}
