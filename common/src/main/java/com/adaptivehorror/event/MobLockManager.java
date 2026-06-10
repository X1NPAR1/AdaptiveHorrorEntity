package com.adaptivehorror.event;

import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.FakePlayerEntity;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.npc.NullManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The "everything stares" set-piece. On its own timer (independent of the event roll), every mob
 * within a few chunks of any player freezes and turns to face the nearest player for 30 seconds,
 * the chat floods with corrupted glyphs in black/white/red tones, and {@code iseeyou} plays. When the
 * timer ends, every mob is restored to exactly the AI state it had before.
 *
 * <p>Server-global and multiplayer-aware: it sweeps every player in every dimension and addresses all
 * of them. Gated behind {@link NullManager#hasJoined()} like the rest of the haunting.
 */
public final class MobLockManager {

    private static final Random RNG = new Random();
    private static final char[] GLYPHS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#@%&$?!".toCharArray();
    private static final ChatFormatting[] COLORS = {
            ChatFormatting.BLACK, ChatFormatting.WHITE, ChatFormatting.RED, ChatFormatting.DARK_RED
    };

    private record Locked(Mob mob, boolean originalNoAi) {
    }

    private static final List<Locked> LOCKED = new ArrayList<>();
    private static long nextCheckTick = -1L;
    private static int lockTicksRemaining = 0;
    private static int durationTicks = 0;
    private static int messagesSent = 0;
    private static long lastProcessedTick = Long.MIN_VALUE;

    private MobLockManager() {
    }

    /** Restores any locked mobs and re-arms. Call on server stop. */
    public static void reset() {
        restoreAll();
        LOCKED.clear();
        lockTicksRemaining = 0;
        nextCheckTick = -1L;
        messagesSent = 0;
        lastProcessedTick = Long.MIN_VALUE;
    }

    public static void tick(MinecraftServer server) {
        final HorrorConfig config = ConfigManager.get();
        final long now = server.overworld().getGameTime();
        if (now == lastProcessedTick) {
            return;
        }
        lastProcessedTick = now;

        if (lockTicksRemaining > 0) {
            tickLock(server, config);
            return;
        }
        if (!config.enabled || !config.features.mobLock || !NullManager.hasJoined()) {
            nextCheckTick = -1L;
            return;
        }
        if (nextCheckTick < 0L) {
            nextCheckTick = now + (long) config.mobLock.intervalSeconds * 20L;
            return;
        }
        if (now >= nextCheckTick) {
            nextCheckTick = now + (long) config.mobLock.intervalSeconds * 20L;
            if (RNG.nextDouble() < config.mobLock.chance) {
                startLock(server, config);
            }
        }
    }

    /** Forces a lock immediately (debug command). */
    public static void forceStart(MinecraftServer server) {
        if (lockTicksRemaining <= 0) {
            startLock(server, ConfigManager.get());
        }
    }

    // --- internals -----------------------------------------------------------------------------

    private static void startLock(MinecraftServer server, HorrorConfig config) {
        LOCKED.clear();
        messagesSent = 0;
        durationTicks = Math.max(20, config.mobLock.durationSeconds * 20);
        lockTicksRemaining = durationTicks;

        final double radius = config.mobLock.radiusChunks * 16.0;
        final Set<Mob> found = new LinkedHashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                final AABB box = player.getBoundingBox().inflate(radius);
                found.addAll(level.getEntitiesOfClass(Mob.class, box, MobLockManager::isLockable));
            }
        }
        for (Mob mob : found) {
            LOCKED.add(new Locked(mob, mob.isNoAi()));
            mob.setNoAi(true);
        }

        // The whisper, to everyone.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HorrorNet.sendSound2D(player, "iseeyou", 1.0F, 1.0F);
        }
    }

    private static void tickLock(MinecraftServer server, HorrorConfig config) {
        lockTicksRemaining--;

        // Keep every locked mob frozen and staring at its nearest player.
        for (Locked locked : LOCKED) {
            final Mob mob = locked.mob();
            if (!mob.isAlive()) {
                continue;
            }
            mob.setNoAi(true);
            final ServerPlayer nearest = (ServerPlayer) mob.level().getNearestPlayer(mob, 128.0);
            if (nearest != null) {
                mob.lookAt(EntityAnchorArgument.Anchor.EYES, nearest.getEyePosition());
                mob.setYBodyRot(mob.getYRot());
            }
        }

        // Spread the corrupted messages evenly across the lock.
        final int elapsed = durationTicks - lockTicksRemaining;
        final int target = (int) ((long) config.mobLock.chatMessageCount * elapsed / durationTicks);
        while (messagesSent < target) {
            broadcastCorrupted(server);
            messagesSent++;
        }

        // A second whisper halfway through.
        if (lockTicksRemaining == durationTicks / 2) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                HorrorNet.sendSound2D(player, "iseeyou", 1.0F, 0.9F);
            }
        }

        if (lockTicksRemaining <= 0) {
            restoreAll();
            LOCKED.clear();
        }
    }

    private static void restoreAll() {
        for (Locked locked : LOCKED) {
            if (locked.mob().isAlive()) {
                locked.mob().setNoAi(locked.originalNoAi());
            }
        }
    }

    private static void broadcastCorrupted(MinecraftServer server) {
        final int len = 12 + RNG.nextInt(17);
        final StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(GLYPHS[RNG.nextInt(GLYPHS.length)]);
        }
        final ChatFormatting color = COLORS[RNG.nextInt(COLORS.length)];
        final Component message = Component.literal(sb.toString())
                .withStyle(ChatFormatting.OBFUSCATED, color);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    private static boolean isLockable(Mob mob) {
        return mob.isAlive() && !(mob instanceof StalkerEntity) && !(mob instanceof FakePlayerEntity);
    }
}
