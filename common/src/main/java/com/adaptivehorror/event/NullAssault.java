package com.adaptivehorror.event;

import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.events.ChatMessageEvent;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.scheduler.HorrorScheduler;
import com.adaptivehorror.scheduler.ScheduledAction;
import com.adaptivehorror.util.Jumpscares;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.UUID;

/**
 * The shared "a null charges you" beat, reused by the pet transformation, the day-6 behind-lock, and
 * the paralysis ritual. It spawns a black null, locks the camera onto it, marches it into the player's
 * face over a second or so, then always fires a jumpscare (and, with a configured chance, kills). All
 * timed via the player's {@link ScheduledAction} queue so it unfolds smoothly and cleans itself up.
 */
public final class NullAssault {

    private static final Random RNG = new Random();

    private NullAssault() {
    }

    /** Spawn a charging null at {@code spawn}, lock onto it for {@code lockTicks}, then charge + scare. */
    public static void chargeFrom(ServerLevel level, ServerPlayer player, Vec3 spawn,
                                  int lockTicks, boolean hardLock, double killChance) {
        final StalkerEntity nul = ModEntities.STALKER.create(level);
        if (nul == null) {
            return;
        }
        nul.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot() + 180.0F, 0.0F);
        nul.setNightForm(true);
        if (!level.addFreshEntity(nul)) {
            return;
        }
        final UUID id = nul.getUUID();

        HorrorNet.sendAimLock(player, lockTicks, hardLock);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, lockTicks + 40, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, lockTicks + 40, 0, false, false, true));

        final var st = HorrorScheduler.getOrCreateState(player);
        final long start = level.getGameTime();
        final int chargeTicks = 24;
        // The charge begins as the lock ends.
        for (int i = 1; i <= chargeTicks; i++) {
            st.scheduled.add(new ScheduledAction(start + lockTicks + i, () -> step(level, id, player)));
        }
        // Finale: always a jumpscare; kill at the configured rate; the null vanishes; effects lift.
        st.scheduled.add(new ScheduledAction(start + lockTicks + chargeTicks + 1, () -> {
            HorrorNet.sendJumpscare(player, Jumpscares.randomImage(RNG), 1 + RNG.nextInt(4), 16);
            if (RNG.nextDouble() < killChance) {
                player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            }
            final Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.CONFUSION);
        }));
    }

    private static void step(ServerLevel level, UUID id, ServerPlayer player) {
        if (!(level.getEntity(id) instanceof StalkerEntity s)) {
            return;
        }
        final Vec3 from = s.position();
        final Vec3 to = player.position();
        final double d = from.distanceTo(to);
        if (d > 1.3) {
            final Vec3 next = from.add(to.subtract(from).normalize().scale(Math.min(0.9, d)));
            final float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - next.x), to.z - next.z));
            s.moveTo(next.x, next.y, next.z, yaw, 0.0F);
            s.setYHeadRot(yaw);
        }
    }

    /**
     * The paralysis ritual: the player is rooted and the camera hard-locked to a null six blocks ahead
     * for ten seconds of corrupted/meaningful chat, then it charges and always jumpscares.
     */
    public static void paralysisRitual(ServerLevel level, ServerPlayer player) {
        final var cfg = ConfigManager.get().aggression;
        final int lockTicks = 20 * 10; // 10 seconds
        // Root the player: very high slowness + dig/jump weakness so they truly cannot move or run.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, lockTicks + 30, 250, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, lockTicks + 30, 128, false, false, false)); // negative jump
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, lockTicks + 30, 250, false, false, false));

        // Null six blocks ahead, facing the player.
        final Vec3 look = player.getLookAngle();
        final Vec3 ahead = player.position().add(look.x * 6.0, 0.0, look.z * 6.0);

        final var st = HorrorScheduler.getOrCreateState(player);
        final long start = level.getGameTime();
        // Ten seconds of chat - corrupted glyphs and meaningful whispers, ~2 per second.
        for (int i = 0; i < 20; i++) {
            final long fire = start + (long) i * 10L;
            st.scheduled.add(new ScheduledAction(fire, () -> ChatMessageEvent.sendNullChat(player, ritualLine())));
        }
        // The lock + charge starts now; the charge fires its jumpscare right at the 10s mark.
        chargeFrom(level, player, ahead, lockTicks, true, cfg.paralysisKillChance);
    }

    private static Component ritualLine() {
        if (RNG.nextBoolean()) {
            return Component.literal(corrupted(6 + RNG.nextInt(12)));
        }
        final String[] real = {
                "bakma", "kıpırdama", "artık benimsin", "kaçış yok", "seni görüyorum",
                "çok geç", "gözlerini kapat", "buradayım", "son saniye", "gel"
        };
        return Component.literal(real[RNG.nextInt(real.length)]);
    }

    /** A run of unsettling glyph noise for the corrupted lines. */
    private static String corrupted(int len) {
        final String pool = "█▓▒░╬╪┼∎※⸸†‡∴∵⌁⏃⏥◆◈⟁⟒⫯ϟ░▚▞";
        final StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(pool.charAt(RNG.nextInt(pool.length())));
        }
        return sb.toString();
    }
}
