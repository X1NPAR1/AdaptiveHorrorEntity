package com.adaptivehorror.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

/**
 * Loader-specific registration glue. Forge requires registration to occur inside its registry
 * events while Fabric registers eagerly; both are hidden behind this interface so the common
 * {@code registry} package can register content with identical call-sites on either loader.
 */
public interface IRegistryHelper {

    /**
     * Registers an entity type and its default attributes. Implementations guarantee the returned
     * {@link EntityType} is the live, registered instance (Forge may defer the actual registry
     * insertion to its {@code RegistryEvent}, but the built type is returned immediately so callers
     * can hold a stable reference).
     *
     * @param id              namespaced id
     * @param typeSupplier    builds the (unregistered) entity type
     * @param attributesSupplier default attribute supplier for living entities
     */
    <T extends LivingEntity> EntityType<T> registerEntity(
            ResourceLocation id,
            Supplier<EntityType<T>> typeSupplier,
            Supplier<AttributeSupplier.Builder> attributesSupplier);

    /** Registers a sound event. */
    SoundEvent registerSound(ResourceLocation id, Supplier<SoundEvent> soundSupplier);
}
