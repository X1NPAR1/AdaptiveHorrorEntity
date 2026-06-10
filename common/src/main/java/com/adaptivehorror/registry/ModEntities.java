package com.adaptivehorror.registry;

import com.adaptivehorror.Constants;
import com.adaptivehorror.entity.FakePlayerEntity;
import com.adaptivehorror.entity.NullBossEntity;
import com.adaptivehorror.entity.StalkerEntity;
import com.adaptivehorror.platform.Services;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity registry. Registration is funnelled through {@link Services#REGISTRY} so the same call-site
 * works on NeoForge (deferred) and Fabric (eager). The built {@link EntityType} is stored in a
 * stable field for the rest of the mod to reference.
 */
public final class ModEntities {

    public static EntityType<StalkerEntity> STALKER;
    public static EntityType<FakePlayerEntity> FAKE_PLAYER;
    public static EntityType<NullBossEntity> NULL_BOSS;

    private ModEntities() {
    }

    public static void register() {
        STALKER = Services.REGISTRY.registerEntity(
                Constants.id("stalker"),
                () -> EntityType.Builder
                        .<StalkerEntity>of(StalkerEntity::new, MobCategory.MONSTER)
                        .sized(0.6F, 1.95F)
                        .clientTrackingRange(12)
                        .updateInterval(1) // sync every tick so the RUSH behaviour reads smoothly
                        .fireImmune()
                        .build("stalker"),
                StalkerEntity::createAttributes);

        FAKE_PLAYER = Services.REGISTRY.registerEntity(
                Constants.id("fake_player"),
                () -> EntityType.Builder
                        .<FakePlayerEntity>of(FakePlayerEntity::new, MobCategory.MONSTER)
                        .sized(0.6F, 1.8F)
                        .clientTrackingRange(16)
                        .updateInterval(3)
                        .build("fake_player"),
                FakePlayerEntity::createAttributes);

        NULL_BOSS = Services.REGISTRY.registerEntity(
                Constants.id("null_boss"),
                () -> EntityType.Builder
                        .<NullBossEntity>of(NullBossEntity::new, MobCategory.MONSTER)
                        .sized(0.7F, 2.1F)
                        .clientTrackingRange(32)
                        .updateInterval(2)
                        .fireImmune()
                        .build("null_boss"),
                NullBossEntity::createAttributes);
    }
}
