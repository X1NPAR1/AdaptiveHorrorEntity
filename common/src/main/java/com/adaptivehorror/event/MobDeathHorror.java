package com.adaptivehorror.event;

import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.FakePlayerEntity;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.events.ChatMessageEvent;
import com.adaptivehorror.network.HorrorNet;
import net.minecraft.network.chat.Component;
import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.scheduler.HorrorScheduler;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

/**
 * A cinematic beat: occasionally, killing a mob is answered. A black null rises from the corpse amid a
 * lightning crack, the killer reels with nausea and darkness and a jumpscare sting - and two seconds
 * later it is simply gone. Loader death hooks (Fabric {@code ServerLivingEntityEvents.AFTER_DEATH},
 * NeoForge {@code LivingDeathEvent}) funnel into {@link #onMobKilled}.
 */
public final class MobDeathHorror {

    private static final Random RNG = new Random();
    private static final int APPARITION_TICKS = 40; // ~2 seconds

    private MobDeathHorror() {
    }

    public static void onMobKilled(ServerLevel level, LivingEntity victim, @Nullable ServerPlayer killer) {
        final HorrorConfig config = ConfigManager.get();
        if (!config.enabled || !config.features.mobDeathHorror || killer == null || !NullManager.hasJoined()) {
            return;
        }
        // Never trigger off our own apparitions.
        if (victim instanceof StalkerEntity || victim instanceof FakePlayerEntity) {
            return;
        }
        // 5% until the late day, then a much higher 45% - mob kills become genuinely dangerous.
        final int day = com.adaptivehorror.scheduler.DayProgression.dayOf(level);
        final double chance = day < config.mobDeathLateDay ? config.mobDeathChance : config.mobDeathChanceLate;
        if (RNG.nextDouble() >= chance) {
            return;
        }

        final Vec3 pos = victim.position();

        // Several lightning cracks around the corpse.
        for (int i = 0; i < 3; i++) {
            final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(pos.x + RNG.nextDouble() * 2 - 1, pos.y, pos.z + RNG.nextDouble() * 2 - 1);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }

        final StalkerEntity apparition = ModEntities.STALKER.create(level);
        if (apparition != null) {
            apparition.moveTo(pos.x, pos.y, pos.z, killer.getYRot() + 180.0F, 0.0F);
            apparition.setNightForm(true); // always the black form
            if (level.addFreshEntity(apparition)) {
                final UUID id = apparition.getUUID();
                HorrorScheduler.getOrCreateState(killer).scheduled.add(
                        new ScheduledAction(level.getGameTime() + APPARITION_TICKS, () -> {
                            final Entity e = level.getEntity(id);
                            if (e != null) {
                                e.discard();
                            }
                            // Once it is gone, the effects lift.
                            killer.removeEffect(MobEffects.DARKNESS);
                            killer.removeEffect(MobEffects.CONFUSION);
                        }));
            }
        }

        killer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false, true));  // nausea I
        killer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0, false, false, true));   // darkness I
        // Jumpscare SOUND only - never the on-screen image for this beat.
        HorrorNet.sendSound2D(killer, "jumpscare" + (1 + RNG.nextInt(4)), 1.6F, 1.0F);
        HorrorNet.sendVignettePulse(killer, 25);
        ChatMessageEvent.sendNullChat(killer, Component.literal(LINES[RNG.nextInt(LINES.length)]));
    }

    private static final String[] LINES = {
            "onu da aldım", "sıra sende", "teşekkürler", "bir tane daha", "beni besledin", "yaklaş"
    };
}
