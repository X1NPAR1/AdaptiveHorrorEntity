package com.adaptivehorror.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * NeoForge implementation of {@link IRegistryHelper}.
 *
 * <p>NeoForge forbids registering content outside its registry events, so each object is built
 * eagerly (a stable reference is returned to callers) but its registry insertion is queued until the
 * matching {@link RegisterEvent} fires. The mod's main class forwards the relevant mod-bus events to
 * the static {@code onRegister}/{@code onAttributes} hooks here. Pending state is static because
 * exactly one helper instance exists (the ServiceLoader singleton).
 */
public final class NeoForgeRegistryHelper implements IRegistryHelper {

    private record EntityEntry(ResourceLocation id, EntityType<?> type, AttributeSupplier.Builder attributes) {
    }

    private record SoundEntry(ResourceLocation id, SoundEvent sound) {
    }

    private static final List<EntityEntry> PENDING_ENTITIES = new ArrayList<>();
    private static final List<SoundEntry> PENDING_SOUNDS = new ArrayList<>();

    @Override
    public <T extends LivingEntity> EntityType<T> registerEntity(
            ResourceLocation id,
            Supplier<EntityType<T>> typeSupplier,
            Supplier<AttributeSupplier.Builder> attributesSupplier) {
        final EntityType<T> type = typeSupplier.get();
        PENDING_ENTITIES.add(new EntityEntry(id, type, attributesSupplier.get()));
        return type;
    }

    @Override
    public SoundEvent registerSound(ResourceLocation id, Supplier<SoundEvent> soundSupplier) {
        final SoundEvent sound = soundSupplier.get();
        PENDING_SOUNDS.add(new SoundEntry(id, sound));
        return sound;
    }

    // --- mod-bus hooks (forwarded by the main class) -------------------------------------------

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            for (EntityEntry entry : PENDING_ENTITIES) {
                event.register(Registries.ENTITY_TYPE, entry.id(), entry::type);
            }
        } else if (event.getRegistryKey().equals(Registries.SOUND_EVENT)) {
            for (SoundEntry entry : PENDING_SOUNDS) {
                event.register(Registries.SOUND_EVENT, entry.id(), entry::sound);
            }
        }
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (EntityEntry entry : PENDING_ENTITIES) {
            @SuppressWarnings("unchecked")
            final EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) entry.type();
            event.put(living, entry.attributes().build());
        }
    }
}
