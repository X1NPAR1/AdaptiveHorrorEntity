package com.adaptivehorror.event;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.DayProgression;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * From day 6 on, null turns openly predatory. Two scheduled assaults run per player:
 * <ul>
 *   <li><b>Behind-lock</b> (every 10-15 min): a null blinks into existence right behind the player and
 *       the camera snaps onto it as it charges.</li>
 *   <li><b>Paralysis ritual</b> (every 10-20 min): the player is rooted and the view hard-locked to a
 *       null six blocks ahead through ten seconds of chat, then it charges and always jumpscares.</li>
 * </ul>
 */
public final class Day6AssaultManager {

    private Day6AssaultManager() {
    }

    public static void tick(ServerPlayer player, PlayerHorrorState state, HorrorConfig config, Random random) {
        final HorrorConfig.Aggression cfg = config.aggression;
        if (!config.features.day6Assault || DayProgression.dayOf(player.level()) < cfg.bloodMoonDay) {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final long now = level.getGameTime();

        // Behind-lock.
        if (state.nextBehindAimlockTick == 0L) {
            state.nextBehindAimlockTick = now + seconds(random, cfg.behindAimlockMinSeconds, cfg.behindAimlockMaxSeconds);
        } else if (now >= state.nextBehindAimlockTick) {
            state.nextBehindAimlockTick = now + seconds(random, cfg.behindAimlockMinSeconds, cfg.behindAimlockMaxSeconds);
            final Vec3 look = player.getLookAngle();
            final Vec3 behind = player.position().subtract(look.x * 3.0, 0.0, look.z * 3.0);
            NullAssault.chargeFrom(level, player, behind, 40, false, cfg.chargeKillChance);
        }

        // Paralysis ritual.
        if (state.nextParalysisTick == 0L) {
            state.nextParalysisTick = now + seconds(random, cfg.paralysisMinSeconds, cfg.paralysisMaxSeconds);
        } else if (now >= state.nextParalysisTick) {
            state.nextParalysisTick = now + seconds(random, cfg.paralysisMinSeconds, cfg.paralysisMaxSeconds);
            NullAssault.paralysisRitual(level, player);
        }
    }

    private static long seconds(Random random, int min, int max) {
        final int span = Math.max(1, max - min);
        return (long) (min + random.nextInt(span)) * 20L;
    }
}
