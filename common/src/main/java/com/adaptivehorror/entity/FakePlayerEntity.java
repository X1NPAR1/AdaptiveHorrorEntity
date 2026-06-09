package com.adaptivehorror.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A motionless doppelganger that renders the observing player's own skin, standing far off and facing
 * away. It never moves and despawns the instant the player gets close (or after a lifetime), so it
 * can never be reached or inspected.
 *
 * <p>Self-contained: it manages its own proximity despawn in {@link #tick()}.
 */
public class FakePlayerEntity extends Mob {

    private static final double DESPAWN_RADIUS = 20.0;
    private static final int MAX_LIFETIME_TICKS = 20 * 60;

    private int age;

    public FakePlayerEntity(EntityType<? extends FakePlayerEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (++age > MAX_LIFETIME_TICKS) {
            discard();
            return;
        }
        final Player nearest = level().getNearestPlayer(this, DESPAWN_RADIUS);
        if (nearest != null) {
            discard(); // vanish when approached
        }
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void aiStep() {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
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
