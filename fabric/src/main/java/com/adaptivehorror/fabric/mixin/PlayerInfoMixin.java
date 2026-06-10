package com.adaptivehorror.fabric.mixin;

import com.adaptivehorror.client.NullSkin;
import com.adaptivehorror.npc.NullManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the fake {@code null} player render with our black skin instead of a default one - this is
 * what gives the tab-list entry its black head. Matched by the fixed null UUID.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void adaptivehorror$nullSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (profile != null && NullManager.NULL_UUID.equals(profile.getId())) {
            cir.setReturnValue(NullSkin.get());
        }
    }
}
