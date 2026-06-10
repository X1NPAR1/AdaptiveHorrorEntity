package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.world.phys.Vec3;

/**
 * A whisper from directly behind the player - a positional {@code iseeyou} right over their shoulder
 * plus a vignette pulse, so they feel something lean in close. No entity, nothing to find when they
 * spin around: pure paranoia.
 */
public final class WhisperEvent implements HorrorEvent {

    @Override
    public String id() {
        return "whisper";
    }

    @Override
    public int minDay() {
        return 1;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 2.0;
    }

    @Override
    public void execute(EventContext ctx) {
        final Vec3 look = ctx.player.getLookAngle();
        final Vec3 horiz = new Vec3(look.x, 0, look.z).normalize().scale(1.5);
        final Vec3 behind = ctx.player.getEyePosition().subtract(horiz);
        HorrorNet.sendSoundAt(ctx.player, "iseeyou", behind, 0.9F, 1.0F);
        HorrorNet.sendVignettePulse(ctx.player, 25);
    }
}
