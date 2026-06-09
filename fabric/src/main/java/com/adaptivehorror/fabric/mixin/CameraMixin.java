package com.adaptivehorror.fabric.mixin;

import com.adaptivehorror.client.ClientHorrorManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric has no camera-setup event, so real world-camera shake is applied here by nudging the
 * camera's rotation at the tail of {@link Camera#setup}. The Forge build achieves the same effect via
 * {@code EntityViewRenderEvent.CameraSetup} - no mixin needed there.
 *
 * <p>The shake magnitude/decay lives in {@link ClientHorrorManager}; this mixin only reads it and
 * re-applies the rotation (which recomputes the camera's look vectors).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void adaptivehorror$applyShake(BlockGetter area, Entity entity, boolean detached,
                                           boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        final ClientHorrorManager mgr = ClientHorrorManager.get();
        if (mgr.isShaking()) {
            setRotation(this.yRot + mgr.nextShakeYaw(), this.xRot + mgr.nextShakePitch());
        }
    }
}
