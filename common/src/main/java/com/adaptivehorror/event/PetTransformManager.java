package com.adaptivehorror.event;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.events.ChatMessageEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * At night, a player's own tamed animal (a wolf, a cat...) can betray them: with a small chance each
 * minute it simply <em>becomes</em> null - the pet vanishes and in its place a black null locks onto
 * the player and charges, with the usual darkness/nausea and an always-on jumpscare.
 */
public final class PetTransformManager {

    private static final double SEARCH_RADIUS = 16.0;

    private PetTransformManager() {
    }

    public static void tick(ServerPlayer player, PlayerHorrorState state, HorrorConfig config, Random random) {
        if (!config.features.petTransform) {
            return;
        }
        final ServerLevel level = player.serverLevel();
        if (level.isDay()) {
            return; // only after dark
        }
        final long now = level.getGameTime();
        if (state.nextPetTransformTick == 0L) {
            state.nextPetTransformTick = now + 20L * 60L;
            return;
        }
        if (now < state.nextPetTransformTick) {
            return;
        }
        state.nextPetTransformTick = now + 20L * 60L; // re-roll each minute
        if (random.nextDouble() >= config.aggression.petNightChancePerMinute) {
            return;
        }

        final AABB box = player.getBoundingBox().inflate(SEARCH_RADIUS);
        final List<TamableAnimal> pets = level.getEntitiesOfClass(TamableAnimal.class, box,
                t -> t.isTame() && t.isAlive());
        if (pets.isEmpty()) {
            return;
        }
        final TamableAnimal pet = pets.get(random.nextInt(pets.size()));
        final Vec3 pos = pet.position();
        pet.discard();
        ChatMessageEvent.sendNullChat(player, Component.literal("o ben değildim"));
        // 1s lock, then it charges; 5% kill, always jumpscares.
        NullAssault.chargeFrom(level, player, pos, 20, false, config.aggression.chargeKillChance);
    }
}
