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

    /**
     * Hard lifetime cap (server ticks). The manager relocates the far form well before this; this is a
     * pure failsafe so a stalker the manager lost track of (e.g. orphaned when its far chunk unloaded)
     * can NEVER persist forever.
     */
    private static final int MAX_LIFETIME_TICKS = 20 * 150;     // 150 s
    /** If no player is within this range, the stalker was abandoned and cleans itself up. */
    private static final double ABANDON_DISTANCE = 256.0;

    private int serverAge;

    public StalkerEntity(EntityType<? extends StalkerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoAi(true);
        setSilent(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // Self-despawn failsafe: the manager owns the lifecycle, but if it ever loses the reference
        // (chunk churn, dimension change, desync) the entity must still tidy itself up.
        if (++serverAge > MAX_LIFETIME_TICKS) {
            discard();
            return;
        }
        if ((serverAge & 31) == 0 && level().getNearestPlayer(this, ABANDON_DISTANCE) == null) {
            discard();
        }
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
