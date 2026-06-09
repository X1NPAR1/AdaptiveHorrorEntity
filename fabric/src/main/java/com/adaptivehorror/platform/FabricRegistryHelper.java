package com.adaptivehorror.platform;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.function.Supplier;

/**
 * Fabric implementation of {@link IRegistryHelper}. Fabric permits eager registration, so each call
 * registers immediately into the built-in registry and (for entities) installs the default attribute
 * supplier via the Fabric object-builder API.
 */
public final class FabricRegistryHelper implements IRegistryHelper {

    @Override
    public <T extends LivingEntity> EntityType<T> registerEntity(
            ResourceLocation id,
            Supplier<EntityType<T>> typeSupplier,
            Supplier<AttributeSupplier.Builder> attributesSupplier) {
        final EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, id, typeSupplier.get());
        FabricDefaultAttributeRegistry.register(type, attributesSupplier.get());
        return type;
    }

    @Override
    public SoundEvent registerSound(ResourceLocation id, Supplier<SoundEvent> soundSupplier) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, soundSupplier.get());
    }
}
