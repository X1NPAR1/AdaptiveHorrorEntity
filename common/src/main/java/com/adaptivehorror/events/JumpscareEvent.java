package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;

/**
 * The classic full-screen jumpscare: a random image (jumpscare1-8) plus a random horror sting,
 * shown briefly. Honours the global jumpscare cooldown stored on the player's state so it can never
 * fire back-to-back, however the dice fall.
 */
public final class JumpscareEvent implements HorrorEvent {

    private static final int IMAGE_COUNT = 8;
    private static final int DURATION_TICKS = 16; // ~0.8s

    @Override
    public String id() {
        return "jumpscare";
    }

    @Override
    public int minDay() {
        return 5;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.jumpscares;
    }

    @Override
    public double weight(EventContext ctx) {
        // Only a candidate when off cooldown - otherwise weight 0 so it's never selected.
        return offCooldown(ctx) ? 0.8 : 0.0;
    }

    @Override
    public void execute(EventContext ctx) {
        if (!offCooldown(ctx)) {
            return;
        }
        final int image = 1 + ctx.random.nextInt(IMAGE_COUNT);
        final int sound = 1 + ctx.random.nextInt(4);
        HorrorNet.sendJumpscare(ctx.player, image, sound, DURATION_TICKS);
        ctx.state.lastJumpscareTick = ctx.level.getGameTime();
    }

    private static boolean offCooldown(EventContext ctx) {
        final long cooldown = (long) ctx.config.scheduler.jumpscareCooldownSeconds * 20L;
        return ctx.level.getGameTime() - ctx.state.lastJumpscareTick >= cooldown;
    }
}
