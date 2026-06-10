package com.adaptivehorror.scheduler;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * The two recurring ambient-audio cues run on their own randomised, independent timers (per the
 * design): {@code scarysounds} every 5-15 min at low volume, and {@code iseeyou} every 10-25 min
 * from a randomised direction so the player cannot localise the source. Kept separate from the main
 * event roll so their cadence is steady background dread rather than part of the escalating events.
 */
public final class PeriodicAudioScheduler {

    private PeriodicAudioScheduler() {
    }

    public static void tick(ServerPlayer player, PlayerHorrorState state, HorrorConfig config, Random random) {
        if (!config.features.audioEvents) {
            return;
        }
        final long now = player.level().getGameTime();

        if (state.nextScaryTick == 0L) {
            state.nextScaryTick = schedule(now, config.audio.scarySoundIntervalMinSeconds,
                    config.audio.scarySoundIntervalMaxSeconds, random);
        } else if (now >= state.nextScaryTick) {
            HorrorNet.sendSound2D(player, "scary_ambient", config.audio.ambientVolume, 1.0F);
            state.nextScaryTick = schedule(now, config.audio.scarySoundIntervalMinSeconds,
                    config.audio.scarySoundIntervalMaxSeconds, random);
        }

        if (state.nextISeeYouTick == 0L) {
            state.nextISeeYouTick = schedule(now, config.audio.iSeeYouIntervalMinSeconds,
                    config.audio.iSeeYouIntervalMaxSeconds, random);
        } else if (now >= state.nextISeeYouTick) {
            playDirectional(player, config, random);
            state.nextISeeYouTick = schedule(now, config.audio.iSeeYouIntervalMinSeconds,
                    config.audio.iSeeYouIntervalMaxSeconds, random);
        }

        // A more frequent rotating one-shot drawn from all the ambient cues, every 1-4 minutes, so
        // travel1/travel2/scary_ambient/iseeyou are all heard from time to time during play.
        if (state.nextAmbientTick == 0L) {
            state.nextAmbientTick = schedule(now, 60, 240, random);
        } else if (now >= state.nextAmbientTick) {
            playRotatingAmbient(player, config, random);
            state.nextAmbientTick = schedule(now, 60, 240, random);
        }
    }

    private static final String[] AMBIENT_POOL = {"travel1", "travel2", "scary_ambient", "iseeyou"};

    private static void playRotatingAmbient(ServerPlayer player, HorrorConfig config, Random random) {
        final String sound = AMBIENT_POOL[random.nextInt(AMBIENT_POOL.length)];
        if (random.nextBoolean()) {
            // From a random nearby direction so the source can't be placed.
            final double angle = random.nextDouble() * Math.PI * 2.0;
            final double dist = 5.0 + random.nextDouble() * 8.0;
            final Vec3 pos = player.position().add(Math.cos(angle) * dist, 0.5, Math.sin(angle) * dist);
            HorrorNet.sendSoundAt(player, sound, pos, Math.max(config.audio.ambientVolume, 0.6F), 1.0F);
        } else {
            HorrorNet.sendSound2D(player, sound, Math.max(config.audio.ambientVolume, 0.6F), 1.0F);
        }
    }

    private static void playDirectional(ServerPlayer player, HorrorConfig config, Random random) {
        // A point on a ring around the player, at a random bearing - source is unplaceable.
        final double angle = random.nextDouble() * Math.PI * 2.0;
        final double dist = 6.0 + random.nextDouble() * 6.0;
        final Vec3 pos = player.position().add(Math.cos(angle) * dist, 0.5, Math.sin(angle) * dist);
        HorrorNet.sendSoundAt(player, "iseeyou", pos, Math.max(config.audio.ambientVolume, 0.6F), 1.0F);
    }

    private static long schedule(long now, int minSeconds, int maxSeconds, Random random) {
        final int span = Math.max(1, maxSeconds - minSeconds);
        final int seconds = minSeconds + random.nextInt(span);
        return now + (long) seconds * 20L;
    }
}
