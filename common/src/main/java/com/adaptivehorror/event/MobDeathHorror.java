package com.adaptivehorror.event;

import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.entity.FakePlayerEntity;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.network.HorrorNet;
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
        if (RNG.nextDouble() >= config.mobDeathChance) {
            return;
        }

        final Vec3 pos = victim.position();

        final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
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
                        }));
            }
        }

        killer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false, true));  // nausea I
        killer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));   // darkness I
        HorrorNet.sendSound2D(killer, "jumpscare" + (1 + RNG.nextInt(4)), 1.0F, 1.0F);
        HorrorNet.sendVignettePulse(killer, 25);
    }
}
