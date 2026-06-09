package com.adaptivehorror.network;

import com.adaptivehorror.world.DisclaimerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side reader for C2S control packets. Loader glue invokes {@link #handle} on the server
 * thread with the originating player.
 */
public final class ServerFxHandler {

    private ServerFxHandler() {
    }

    public static void handle(ServerPlayer player, FriendlyByteBuf data) {
        final HorrorNet.C2SType type = HorrorNet.C2SType.values()[data.readByte()];
        switch (type) {
            case DISCLAIMER_ACCEPTED:
                DisclaimerState.get(player.serverLevel()).accept(player.getUUID());
                break;
        }
    }
}
