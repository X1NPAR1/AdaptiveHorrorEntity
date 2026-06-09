package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.platform.Services;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A single-frame flicker at the edge of perception - no entity, no collision, no sound, gone
 * instantly. Designed purely to make the player doubt what they saw. Implemented as a client cue
 * (a one-tick vignette/glitch) rather than a world entity, so it leaves zero trace to verify.
 */
public final class ShadowGhostEvent implements HorrorEvent {

    @Override
    public String id() {
        return "shadow_ghost";
    }

    @Override
    public int minDay() {
        return 3;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.shadowEntities;
    }

    @Override
    public double weight(EventContext ctx) {
        return 1.2;
    }

    @Override
    public void execute(EventContext ctx) {
        // Reuse the SHADOW_GHOST fx tag directly (no payload beyond the discriminator).
        final FriendlyByteBuf b = Services.NETWORK.createBuffer();
        b.writeByte(HorrorNet.FxType.SHADOW_GHOST.ordinal());
        Services.NETWORK.sendFx(ctx.player, b);
    }
}
