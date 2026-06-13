package com.adaptivehorror.event;

import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.scheduler.DayProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * The blood moon. From day {@code bloodMoonDay} on, while it is daytime there is a 3-5 minute fuse;
 * when it burns out the sky floods red, the world is locked to night (it never turns to day again for
 * the duration), and sleep becomes impossible - any bed a player lies in detonates. After the duration
 * the day returns, and a fresh fuse is set for the next one.
 */
public final class BloodMoonManager {

    private static final Random RNG = new Random();
    private static final long NIGHT_TIME = 18000L; // moon high overhead

    private static boolean active;
    private static long endTick;
    private static long nextTriggerTick;
    private static long lastProcessed = Long.MIN_VALUE;

    private BloodMoonManager() {
    }

    public static void reset() {
        active = false;
        endTick = 0L;
        nextTriggerTick = 0L;
        lastProcessed = Long.MIN_VALUE;
    }

    public static boolean isActive() {
        return active;
    }

    public static void tick(MinecraftServer server) {
        final HorrorConfig config = ConfigManager.get();
        final HorrorConfig.Aggression cfg = config.aggression;
        final ServerLevel overworld = server.overworld();
        final long now = overworld.getGameTime();
        if (now == lastProcessed) {
            return;
        }
        lastProcessed = now;

        if (!config.enabled || !config.features.bloodMoon || !NullManager.hasJoined()
                || DayProgression.dayOf(overworld) < cfg.bloodMoonDay) {
            if (active) {
                end(server);
            }
            return;
        }

        if (active) {
            overworld.setDayTime(NIGHT_TIME); // hold the world in red night
            if (now % 40L == 0L) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    HorrorNet.sendBloodMoon(p, 60);
                }
            }
            preventSleep(server);
            if (now >= endTick) {
                end(server);
            }
            return;
        }

        // Not active: arm and wait for the daytime fuse.
        if (nextTriggerTick == 0L) {
            nextTriggerTick = now + seconds(cfg.bloodMoonTriggerMinSeconds, cfg.bloodMoonTriggerMaxSeconds);
            return;
        }
        if (overworld.isDay() && now >= nextTriggerTick) {
            start(server, cfg, now);
        }
    }

    private static void start(MinecraftServer server, HorrorConfig.Aggression cfg, long now) {
        active = true;
        endTick = now + (long) cfg.bloodMoonDurationSeconds * 20L;
        server.overworld().setDayTime(NIGHT_TIME);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            HorrorNet.sendBloodMoon(p, 80);
            p.sendSystemMessage(Component.literal("Gökyüzü kana bulandı.").withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static void end(MinecraftServer server) {
        active = false;
        nextTriggerTick = server.overworld().getGameTime()
                + seconds(ConfigManager.get().aggression.bloodMoonTriggerMinSeconds,
                          ConfigManager.get().aggression.bloodMoonTriggerMaxSeconds);
        server.overworld().setDayTime(1000L); // dawn returns
    }

    /** During a blood moon no one sleeps - the bed erupts under them. */
    private static void preventSleep(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSleeping()) {
                continue;
            }
            final BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
            player.stopSleeping();
            if (player.level() instanceof ServerLevel level) {
                level.explode(null, bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5,
                        3.0F, Level.ExplosionInteraction.TNT);
            }
            player.sendSystemMessage(Component.literal("Uyuyamazsın.").withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static long seconds(int min, int max) {
        final int span = Math.max(1, max - min);
        return (long) (min + RNG.nextInt(span)) * 20L;
    }
}
