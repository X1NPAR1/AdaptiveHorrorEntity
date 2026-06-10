package com.adaptivehorror.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * The summoned "null" boss - the only form of the entity that is corporeal, mortal and openly hostile.
 * Raised by the redstone totem ritual ({@link com.adaptivehorror.event.TotemManager}), it walks the
 * player down, hits hard, and shows a red boss bar with a huge health pool. Beating it frees the world
 * of null for good; if it kills the player instead, the totem detonates and the boss is empowered.
 *
 * <p>Deliberately a real {@link Monster} (unlike the inert {@link StalkerEntity}) so vanilla pathing,
 * targeting and combat just work; everything bespoke (boss bar, loot, the "freed/empowered" outcomes)
 * is layered on top.
 */
public class NullBossEntity extends Monster {

    private final ServerBossEventCompat bossEvent =
            new ServerBossEventCompat(Component.literal("null"), BossEvent.BossBarColor.RED);

    @Nullable
    private BlockPos totemPos;
    private int empowerLevel;

    public NullBossEntity(EntityType<? extends NullBossEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1500.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 1.0D, 48.0F));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 64.0F));
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public void setTotemPos(@Nullable BlockPos pos) {
        this.totemPos = pos;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        final boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide && target instanceof ServerPlayer player && player.isDeadOrDying()) {
            onKilledPlayer(player);
        }
        return hit;
    }

    /** The player fell: detonate the totem and grow stronger. The fight goes on. */
    private void onKilledPlayer(ServerPlayer player) {
        if (level() instanceof ServerLevel server) {
            if (totemPos != null) {
                server.explode(this, totemPos.getX() + 0.5, totemPos.getY() + 0.5, totemPos.getZ() + 0.5,
                        4.0F, Level.ExplosionInteraction.TNT);
            }
            empowerLevel++;
            final double newMax = getMaxHealth() * 1.5;
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMax);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(getAttributeValue(Attributes.ATTACK_DAMAGE) + 4.0);
            setHealth((float) newMax);
            server.players().forEach(p -> p.sendSystemMessage(
                    Component.literal("null daha da güçlendi...").withStyle(net.minecraft.ChatFormatting.DARK_RED)));
            playSound(SoundEvents.WITHER_SPAWN, 2.0F, 0.6F);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server && !this.isRemoved()) {
            dropBossLoot(server);
            com.adaptivehorror.npc.NullManager.defeat(server.getServer());
            server.players().forEach(p -> {
                p.sendSystemMessage(Component.literal("null'dan kurtuldun.")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                p.sendSystemMessage(Component.translatable("adaptivehorror.null.leave")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            });
        }
        this.bossEvent.removeAllPlayers();
        super.die(source);
    }

    private void dropBossLoot(ServerLevel server) {
        final int ingots = 7 + this.random.nextInt(8); // 7-14
        spawnAtSelf(new ItemStack(Items.NETHERITE_INGOT, ingots));
        spawnAtSelf(new ItemStack(Items.NETHER_STAR));
    }

    private void spawnAtSelf(ItemStack stack) {
        final var item = new net.minecraft.world.entity.item.ItemEntity(level(), getX(), getY() + 0.5, getZ(), stack);
        item.setDefaultPickUpDelay();
        level().addFreshEntity(item);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // a boss never despawns
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    /** Plays the totem-summon roar (used by the ritual the instant it spawns). */
    public void roar(ServerLevel level) {
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 4.0F, 0.7F);
    }

    /**
     * Thin wrapper so the boss bar lives entirely in this class without importing the server type into
     * the common entity at field-declaration time (keeps the dedicated-server class graph happy).
     */
    private static final class ServerBossEventCompat extends net.minecraft.server.level.ServerBossEvent {
        ServerBossEventCompat(Component name, BossEvent.BossBarColor color) {
            super(name, color, BossEvent.BossBarOverlay.PROGRESS);
        }
    }
}
