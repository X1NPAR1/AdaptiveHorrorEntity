package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import com.adaptivehorror.spawn.SpawnLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The aim-lock ritual (#17). A black null manifests nearby and the player's view is wrenched onto it
 * and held there - they can fight it, but it drags back. While they are made to stare, the chat fills
 * with the thing's whispers and a ring of silent lightning strikes the ground around them, one bolt at
 * a time, closing the circle. When the last bolt lands, a jumpscare - and the null is gone.
 *
 * <p>Multiplayer-correct: the apparition and its lightning are real server entities (synced to every
 * nearby client automatically); the lock and jumpscare target the focused player while everyone else
 * hears the whisper. The lock is client-side and strictly timer-bounded, so it can never strand the
 * camera. Built entirely from per-player {@link ScheduledAction}s so it unfolds over several seconds.
 */
public final class AimLockEvent implements HorrorEvent {

    private static final int LIGHTNING_COUNT = 8;     // bolts in the ring
    private static final double RING_RADIUS = 3.5;

    @Override
    public String id() {
        return "aim_lock";
    }

    @Override
    public int minDay() {
        return 6;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.cameraEffects;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.4; // rare set-piece
    }

    @Override
    public void execute(EventContext ctx) {
        final ServerLevel level = ctx.level;

        // A visible apparition to lock onto - close enough to fill the view, not on top of the player.
        final BlockPos spot = SpawnLocator.findSpawn(ctx.player, ctx.random, 14, 24);
        if (spot == null) {
            return; // no clear, loaded spot this time - skip rather than force it
        }
        final StalkerEntity apparition = ModEntities.STALKER.create(level);
        if (apparition == null) {
            return;
        }
        apparition.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
        apparition.setNightForm(true);
        faceTowards(apparition, ctx.player.position());
        if (!level.addFreshEntity(apparition)) {
            return;
        }
        final UUID apparitionId = apparition.getUUID();

        final int lockTicks = 80 + ctx.random.nextInt(41); // 4-6s
        HorrorNet.sendAimLock(ctx.player, lockTicks);

        // Everyone else feels it land, faintly.
        final MinecraftServer server = ctx.player.getServer();
        if (server != null) {
            HorrorNet.broadcastSound2DExcept(server, ctx.player, "iseeyou", 0.4F, 0.8F);
        }

        final long start = level.getGameTime();
        final Vec3 centre = ctx.player.position();

        // Whispers spread across the lock.
        for (int i = 0; i < 5; i++) {
            final long fire = start + 10L + (long) (lockTicks - 10) * i / 5L;
            ctx.state.scheduled.add(new ScheduledAction(fire,
                    () -> ChatMessageEvent.sendNullChat(ctx.player,
                            Component.translatable(RITUAL_KEYS[ctx.random.nextInt(RITUAL_KEYS.length)]))));
        }

        // The ring of lightning closes one bolt at a time, finishing just before the jumpscare.
        for (int i = 0; i < LIGHTNING_COUNT; i++) {
            final double angle = 2.0 * Math.PI * i / LIGHTNING_COUNT;
            final double bx = centre.x + Math.cos(angle) * RING_RADIUS;
            final double bz = centre.z + Math.sin(angle) * RING_RADIUS;
            final long fire = start + 8L + (long) (lockTicks - 16) * i / LIGHTNING_COUNT;
            ctx.state.scheduled.add(new ScheduledAction(fire, () -> strikeVisual(level, bx, centre.y, bz)));
        }

        // The finale: jumpscare and the apparition vanishes.
        ctx.state.scheduled.add(new ScheduledAction(start + lockTicks, () -> {
            HorrorNet.sendJumpscare(ctx.player, com.adaptivehorror.util.Jumpscares.randomImage(ctx.random), 1 + ctx.random.nextInt(4), 16);
            final Entity e = level.getEntity(apparitionId);
            if (e != null) {
                e.discard();
            }
        }));
    }

    private static final String[] RITUAL_KEYS = {
            "adaptivehorror.chat.watching",
            "adaptivehorror.chat.dont_look_away",
            "adaptivehorror.chat.behind_you",
            "adaptivehorror.chat.i_see_you",
            "adaptivehorror.chat.why",
            "adaptivehorror.chat.too_late"
    };

    private static void strikeVisual(ServerLevel level, double x, double y, double z) {
        final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x, y, z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    private static void faceTowards(StalkerEntity stalker, Vec3 target) {
        final Vec3 s = stalker.position();
        final float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - s.x), target.z - s.z));
        stalker.setYRot(yaw);
        stalker.setYHeadRot(yaw);
        stalker.setYBodyRot(yaw);
    }
}
