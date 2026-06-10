package com.adaptivehorror.event;

import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.FakePlayerEntity;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.scheduler.DayProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import com.adaptivehorror.spawn.SpawnLocator;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the world hostile. At night there is a small per-minute chance that every mob near a player
 * rounds on them for a minute (weakly) while the chat corrupts. From the configured aggression day
 * (default 10) this becomes permanent - mobs hound the player day and night and spawn far more
 * thickly. If a mob lands the killing blow during an assault, the player gets a jumpscare.
 */
public final class AssaultManager {

    private static final Random RNG = new Random();
    private static final EntityType<?>[] EXTRA_MOBS = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.ZOMBIE
    };

    private static long assaultEndTick = 0L;
    private static long lastProcessedTick = Long.MIN_VALUE;
    private static long lastNightRollTick = Long.MIN_VALUE;
    private static long lastSpawnCycleTick = Long.MIN_VALUE;
    private static final Set<UUID> deadHandled = new HashSet<>();

    private AssaultManager() {
    }

    public static void reset() {
        assaultEndTick = 0L;
        lastProcessedTick = Long.MIN_VALUE;
        lastNightRollTick = Long.MIN_VALUE;
        lastSpawnCycleTick = Long.MIN_VALUE;
        deadHandled.clear();
    }

    public static void forceStart(MinecraftServer server) {
        startAssault(server, ConfigManager.get());
    }

    public static void tick(MinecraftServer server) {
        final HorrorConfig config = ConfigManager.get();
        if (!config.enabled || !config.assault.enabled || !NullManager.hasJoined()) {
            return;
        }
        final long now = server.overworld().getGameTime();
        if (now == lastProcessedTick) {
            return;
        }
        lastProcessedTick = now;

        final int day = DayProgression.dayOf(server.overworld());
        final boolean permanent = day >= config.assault.aggressionDay;

        // Night roll (once a minute) while not already aggressive.
        if (!permanent && !server.overworld().isDay() && now >= assaultEndTick
                && now - lastNightRollTick >= 1200L) {
            lastNightRollTick = now;
            if (RNG.nextDouble() < config.assault.nightChancePerMinute) {
                startAssault(server, config);
            }
        }

        final boolean active = permanent || now < assaultEndTick;
        if (!active) {
            return;
        }

        applyAssault(server, config, now);
        checkDeaths(server);

        if (permanent && now - lastSpawnCycleTick >= 200L) {
            lastSpawnCycleTick = now;
            spawnExtra(server, config);
        }
    }

    // --- internals -----------------------------------------------------------------------------

    private static void startAssault(MinecraftServer server, HorrorConfig config) {
        assaultEndTick = server.overworld().getGameTime() + (long) config.assault.durationSeconds * 20L;
        deadHandled.clear();
        for (int i = 0; i < 15; i++) {
            broadcastCorrupted(server);
        }
    }

    private static void applyAssault(MinecraftServer server, HorrorConfig config, long now) {
        final double radius = config.assault.radiusChunks * 16.0;
        final boolean dealDamage = now % 20L == 0L; // weak: at most once per second per mob in range
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (!player.isAlive()) {
                    continue;
                }
                final AABB box = player.getBoundingBox().inflate(radius);
                for (Mob mob : level.getEntitiesOfClass(Mob.class, box, AssaultManager::isHostileTarget)) {
                    mob.setTarget(player);
                    if (mob instanceof PathfinderMob pathfinder) {
                        pathfinder.getNavigation().moveTo(player, 1.3);
                    }
                    if (dealDamage && mob.distanceToSqr(player) <= 2.4 * 2.4) {
                        player.hurt(mob.damageSources().mobAttack(mob), config.assault.contactDamage);
                    }
                }
            }
        }
    }

    private static void checkDeaths(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() && deadHandled.add(player.getUUID())) {
                HorrorNet.sendJumpscare(player, 1 + RNG.nextInt(8), 1 + RNG.nextInt(4), 16);
            } else if (player.isAlive()) {
                deadHandled.remove(player.getUUID());
            }
        }
    }

    private static void spawnExtra(MinecraftServer server, HorrorConfig config) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                for (int i = 0; i < config.assault.extraSpawnPerCycle; i++) {
                    final BlockPos pos = SpawnLocator.findSpawn(player, RNG, 16, 40);
                    if (pos == null) {
                        continue;
                    }
                    final EntityType<?> type = EXTRA_MOBS[RNG.nextInt(EXTRA_MOBS.length)];
                    if (type.create(level) instanceof Mob mob) {
                        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360F, 0F);
                        if (level.addFreshEntity(mob)) {
                            mob.setTarget(player);
                        }
                    }
                }
            }
        }
    }

    private static void broadcastCorrupted(MinecraftServer server) {
        final int len = 10 + RNG.nextInt(16);
        final StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('!' + RNG.nextInt(90)));
        }
        final ChatFormatting[] colors = {ChatFormatting.RED, ChatFormatting.DARK_RED, ChatFormatting.BLACK};
        final Component message = Component.literal(sb.toString())
                .withStyle(ChatFormatting.OBFUSCATED, colors[RNG.nextInt(colors.length)]);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    private static boolean isHostileTarget(Mob mob) {
        return mob.isAlive() && !(mob instanceof StalkerEntity) && !(mob instanceof FakePlayerEntity);
    }
}
