package com.adaptivehorror.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-specific S2C networking. The mod uses a single channel carrying a discriminated "effect"
 * payload (see {@link com.adaptivehorror.network.HorrorNet}); each loader wires that channel to its
 * native networking API and forwards inbound buffers to
 * {@link com.adaptivehorror.network.ClientFxHandler} on the client thread.
 */
public interface INetworkHelper {

    /** Sends an effect buffer to a single player's client. Server-thread only. */
    void sendFx(ServerPlayer player, FriendlyByteBuf data);

    /** Sends a control buffer from the client to the server (e.g. disclaimer acceptance). */
    void sendToServer(FriendlyByteBuf data);

    /** Allocates a fresh, writable buffer for a payload. */
    FriendlyByteBuf createBuffer();
}
