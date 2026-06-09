package com.adaptivehorror.registry;

import com.adaptivehorror.Constants;
import com.adaptivehorror.entity.FakePlayerEntity;
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

    private ModEntities() {
    }

    public static void register() {
        STALKER = Services.REGISTRY.registerEntity(
                Constants.id("stalker"),
                () -> EntityType.Builder
                        .<StalkerEntity>of(StalkerEntity::new, MobCategory.MONSTER)
                        .sized(0.6F, 1.95F)
                        .clientTrackingRange(12)
                        .updateInterval(3)
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
    }
}
