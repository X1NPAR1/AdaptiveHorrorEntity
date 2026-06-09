package com.adaptivehorror.ai;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Samples raw player state each tick into the decayed signals on {@link BehaviorTracker}. This is
 * the input half of the adaptive AI: it never makes decisions, it only observes. The scheduler and
 * {@link com.adaptivehorror.scheduler.EventRegistry} read the resulting signals to bias behaviour.
 */
public final class BehaviorSampler {

    /** Per-tick yaw swing (degrees) above which we count a deliberate "check behind". */
    private static final double LOOK_BEHIND_DEGREES = 90.0;

    /** Multiplicative decay applied each second so signals reflect recent behaviour. */
    private static final double DECAY_PER_SECOND = 0.97;

    private BehaviorSampler() {
    }

    public static void sample(ServerPlayer player, PlayerHorrorState state) {
        // Rapid turn detection.
        final float yaw = player.getYRot();
        if (!Float.isNaN(state.lastYaw)) {
            final double delta = Math.abs(Mth.wrapDegrees(yaw - state.lastYaw));
            if (delta >= LOOK_BEHIND_DEGREES) {
                state.behavior.addLookBehind(8.0);
            }
        }
        state.lastYaw = yaw;

        // Underground mining.
        if (player.getY() < 50 && !player.level().canSeeSky(player.blockPosition())) {
            state.behavior.addMining(1.0);
        }

        // Enclosed / camping: solid ceiling directly overhead and no sky access.
        final boolean enclosed = player.level()
                .getBlockState(player.blockPosition().above(2)).isSolid();
        if (enclosed && !player.level().canSeeSky(player.blockPosition())) {
            state.behavior.addCamping(1.0);
        }

        // AFK: negligible horizontal motion.
        final net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
        final double speedSqr = motion.x * motion.x + motion.z * motion.z;
        if (speedSqr < 1.0e-4) {
            state.behavior.afkTicks++;
        } else {
            state.behavior.afkTicks = 0;
        }

        // Decay once per second to keep signals "recent".
        if (player.level().getGameTime() % 20L == 0L) {
            state.behavior.decay(DECAY_PER_SECOND);
        }
    }
}
