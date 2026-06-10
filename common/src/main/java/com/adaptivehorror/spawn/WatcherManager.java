package com.adaptivehorror.spawn;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.scheduler.DayProgression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Random;
import java.util.UUID;

/**
 * "The many eyes." After the fifth night, a handful of extra nulls (3-8) stand 50-200 blocks off and
 * simply watch the player. They are almost always peaceful: if the player closes within the vanish
 * radius they wink out. A small fraction (default 5%) instead strike with a jumpscare when
 * approached - and even then only kill 20% of the time (shared {@link StalkerManager#jumpscareAttack}).
 *
 * <p>Separate from the single managed stalker: watchers are tracked by their own id list on the
 * player's state, so the two systems never tread on each other.
 */
public final class WatcherManager {

    private WatcherManager() {
    }

    public static void tick(ServerPlayer player, PlayerHorrorState state, HorrorConfig config, Random random) {
        final HorrorConfig.Watchers cfg = config.watchers;
        final ServerLevel level = player.serverLevel();

        if (!cfg.enabled || DayProgression.dayOf(player.level()) < cfg.startDay) {
            despawnAll(level, state);
            return;
        }

        final boolean underground = com.adaptivehorror.util.Locations.isUnderground(player);

        // Resolve, face the player, and handle the approach of each existing watcher.
        final Iterator<UUID> it = state.watcherIds.iterator();
        while (it.hasNext()) {
            final Entity entity = level.getEntity(it.next());
            if (!(entity instanceof StalkerEntity watcher) || !watcher.isAlive()) {
                it.remove();
                continue;
            }
            // Drop watchers that no longer match the player's level (e.g. surface ones once you go
            // deep underground), so they re-form in the right place rather than hanging in the sky.
            if (Math.abs(watcher.getY() - player.getY()) > 40.0) {
                watcher.discard();
                it.remove();
                continue;
            }
            watcher.setNightForm(underground || !level.isDay());
            facePlayer(watcher, player);

            if (player.distanceToSqr(watcher) <= (double) cfg.vanishRadius * cfg.vanishRadius) {
                if (random.nextDouble() < com.adaptivehorror.ai.AdaptiveAI.strikeChance(level, state, underground, false)) {
                    StalkerManager.jumpscareAttack(player, state, random); // 80% scare, kill scales with day
                }
                watcher.discard();
                it.remove();
            }
        }

        // Maintain the target headcount.
        if (state.watcherTargetCount == 0) {
            state.watcherTargetCount = cfg.minCount + random.nextInt(Math.max(1, cfg.maxCount - cfg.minCount + 1));
        }
        final long now = level.getGameTime();
        if (state.watcherIds.size() < state.watcherTargetCount && now >= state.nextWatcherSpawnTick) {
            state.nextWatcherSpawnTick = now + 20L; // at most one new watcher per second
            spawnWatcher(player, level, state, cfg, random);
        }
    }

    public static void despawnAll(ServerLevel level, PlayerHorrorState state) {
        if (state.watcherIds.isEmpty()) {
            state.watcherTargetCount = 0;
            return;
        }
        for (UUID id : state.watcherIds) {
            final Entity entity = level.getEntity(id);
            if (entity instanceof StalkerEntity watcher) {
                watcher.discard();
            }
        }
        state.watcherIds.clear();
        state.watcherTargetCount = 0;
    }

    private static void spawnWatcher(ServerPlayer player, ServerLevel level, PlayerHorrorState state,
                                     HorrorConfig.Watchers cfg, Random random) {
        // Underground, the watchers gather in the caves around you (at your level); on the surface,
        // they stand far off. Never on the distant surface while you're deep underground.
        final boolean underground = com.adaptivehorror.util.Locations.isUnderground(player);
        final BlockPos pos = underground
                ? SpawnLocator.findUnderground(player, random, 16, 64)
                : SpawnLocator.findSpawn(player, random, cfg.distanceMin, cfg.distanceMax);
        if (pos == null) {
            return; // no valid (loaded) spot this tick; try again later
        }
        final StalkerEntity watcher = ModEntities.STALKER.create(level);
        if (watcher == null) {
            return;
        }
        watcher.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        watcher.setNightForm(underground || !level.isDay());
        facePlayer(watcher, player);
        if (level.addFreshEntity(watcher)) {
            state.watcherIds.add(watcher.getUUID());
        }
    }

    private static void facePlayer(StalkerEntity watcher, ServerPlayer player) {
        final Vec3 s = watcher.position();
        final float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - s.x), player.getZ() - s.z));
        watcher.setYRot(yaw);
        watcher.setYHeadRot(yaw);
        watcher.setYBodyRot(yaw);
    }
}
