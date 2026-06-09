package com.adaptivehorror.world;

import com.adaptivehorror.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world record of which players have accepted the disclaimer. Stored on the overworld's data
 * storage so it is world-global (not per-dimension). The horror scheduler consults this so no event
 * fires for a player until they have accepted, satisfying "the player cannot continue until
 * accepting" without a client-only honour system.
 */
public final class DisclaimerState extends SavedData {

    private static final String NAME = Constants.MOD_ID + "_disclaimer";

    /** 1.21 builds SavedData through a Factory (constructor + deserializer + optional datafixer). */
    private static final SavedData.Factory<DisclaimerState> FACTORY =
            new SavedData.Factory<>(DisclaimerState::new, DisclaimerState::load, null);

    private final Set<UUID> accepted = new HashSet<>();

    public DisclaimerState() {
    }

    /** Resolves (or creates) the world-global instance for the given level. */
    public static DisclaimerState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public boolean hasAccepted(UUID playerId) {
        return accepted.contains(playerId);
    }

    public void accept(UUID playerId) {
        if (accepted.add(playerId)) {
            setDirty();
        }
    }

    // --- persistence ---------------------------------------------------------------------------

    private static DisclaimerState load(CompoundTag tag, HolderLookup.Provider provider) {
        final DisclaimerState state = new DisclaimerState();
        // NBT type id 11 = INT_ARRAY (the UUID storage format).
        final ListTag list = tag.getList("accepted", 11);
        for (int i = 0; i < list.size(); i++) {
            state.accepted.add(NbtUtils.loadUUID(list.get(i)));
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        final ListTag list = new ListTag();
        for (UUID id : accepted) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("accepted", list);
        return tag;
    }
}
