package com.adaptivehorror.scheduler;

import com.adaptivehorror.AdaptiveHorror;
import net.minecraft.server.level.ServerPlayer;

/**
 * Seam between server-side <em>decisions</em> ("a travel sound should play for this player now") and
 * the client-side <em>presentation</em> of those decisions (playing audio, showing a full-screen
 * image, applying camera shaders). The networking layer installs a real implementation that sends
 * S2C packets; until then a logging stub keeps the decision logic fully exercisable and testable.
 *
 * <p>This indirection is what lets the scheduler/AI be unit-tested without a client, and keeps all
 * loader/networking specifics out of the core.
 */
public interface EffectDispatcher {

    void playTravelSound(ServerPlayer player);

    void travelJumpscare(ServerPlayer player);

    /** No-op-ish default: records the decision so behaviour is visible before networking lands. */
    EffectDispatcher LOGGING = new EffectDispatcher() {
        @Override
        public void playTravelSound(ServerPlayer player) {
            AdaptiveHorror.LOGGER.debug("[dispatch] travel sound -> {}", player.getGameProfile().getName());
        }

        @Override
        public void travelJumpscare(ServerPlayer player) {
            AdaptiveHorror.LOGGER.debug("[dispatch] travel jumpscare -> {}", player.getGameProfile().getName());
        }
    };

    /** The active dispatcher. Replaced by the networking layer during init. */
    Holder ACTIVE = new Holder();

    /** Tiny mutable holder so the reference can be swapped without a static-field race in callers. */
    final class Holder {
        private volatile EffectDispatcher delegate = LOGGING;

        public EffectDispatcher get() {
            return delegate;
        }

        public void set(EffectDispatcher dispatcher) {
            this.delegate = dispatcher;
        }
    }
}
