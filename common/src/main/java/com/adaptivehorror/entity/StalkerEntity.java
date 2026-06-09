package com.adaptivehorror.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The single primary stalking entity.
 *
 * <p>It <em>never walks and never animates locomotion</em>. The illusion of pursuit is produced
 * entirely by {@link com.adaptivehorror.spawn.StalkerManager} despawning it and silently re-spawning
 * it elsewhere. This class is therefore deliberately inert - no AI goals, ignores damage, silent, and
 * never naturally despawns. It is a puppet; the manager is the puppeteer.
 *
 * <p>The only state it carries is a synced {@code night} flag driving client rendering (white by day,
 * black with glowing eyes by night).
 */
public class StalkerEntity extends Mob {

    private static final EntityDataAccessor<Boolean> DATA_NIGHT =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.BOOLEAN);

    public StalkerEntity(EntityType<? extends StalkerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoAi(true);
        setSilent(true);
    }

    /** Default attributes. Movement speed is zero - it must never path or drift. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 1.21 builds synched data through a builder rather than defining on a singleton.
        super.defineSynchedData(builder);
        builder.define(DATA_NIGHT, false);
    }

    public boolean isNightForm() {
        return this.entityData.get(DATA_NIGHT);
    }

    public void setNightForm(boolean night) {
        this.entityData.set(DATA_NIGHT, night);
    }

    // --- Inertness ----------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        // Intentionally empty: no behaviour of its own.
    }

    @Override
    public void aiStep() {
        // No-op: suppress vanilla mob ticking (look control, jump, wandering). Gravity still resolves
        // via the base tick so the model rests on the ground.
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // the horror is its presence, not combat
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
