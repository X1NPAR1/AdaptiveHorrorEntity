package com.adaptivehorror.neoforge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pulls the distance (terrain) fog in by ~20%, always, so the world is permanently a little hazy -
 * part of the mod's oppressive atmosphere. Sky fog is left untouched. Deliberately not gated by any
 * config; this is meant to be inescapable.
 */
@Mixin(FogRenderer.class)
public class FogMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void adaptivehorror$haze(Camera camera, FogRenderer.FogMode fogMode, float renderDistance,
                                            boolean thickFog, float partialTick, CallbackInfo ci) {
        if (fogMode == FogRenderer.FogMode.FOG_TERRAIN) {
            final float newEnd = RenderSystem.getShaderFogEnd() * 0.8F;
            RenderSystem.setShaderFogEnd(newEnd);
            RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), newEnd * 0.15F));
        }
    }
}
