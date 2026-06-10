package com.adaptivehorror.client;

import com.adaptivehorror.Constants;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * The skin used for the fake {@code null} player on the client: our shipped {@code stalker_black.png}.
 * Both loaders' {@code PlayerInfo} mixins return this for the null UUID, so the tab-list head (and any
 * rendering of "null") reads as the entity rather than a default skin - no hosted texture required.
 */
public final class NullSkin {

    private static final ResourceLocation TEXTURE = Constants.id("textures/entity/stalker_black.png");
    private static PlayerSkin cached;

    private NullSkin() {
    }

    public static PlayerSkin get() {
        if (cached == null) {
            cached = new PlayerSkin(TEXTURE, null, null, null, PlayerSkin.Model.WIDE, true);
        }
        return cached;
    }
}
