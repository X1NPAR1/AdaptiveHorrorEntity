package com.adaptivehorror.platform;

import com.adaptivehorror.network.ClientFxHandler;
import com.adaptivehorror.network.ControlPayload;
import com.adaptivehorror.network.FxPayload;
import com.adaptivehorror.network.ServerFxHandler;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of {@link INetworkHelper} over 1.21 typed payloads.
 *
 * <p>Payload <em>types</em> are registered once on both sides (see {@link #registerCommon()}); the
 * server C2S receiver here, and the client S2C receiver in the Fabric client entrypoint. Sending
 * simply wraps the {@link HorrorNet}-built byte buffer in the matching payload.
 */
public final class FabricNetworkHelper implements INetworkHelper {

    /** Registers the payload types on both logical sides. Call from common/main init. */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(FxPayload.TYPE, FxPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ControlPayload.TYPE, ControlPayload.CODEC);
    }

    /** Registers the server-side C2S receiver. Call once during common/main init. */
    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ControlPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        ServerFxHandler.handle(context.player(), wrap(payload.data()))));
    }

    /** Registers the client-side S2C receiver. Call once during client init. */
    public static void registerClientReceiver() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                FxPayload.TYPE, (payload, context) ->
                        context.client().execute(() -> ClientFxHandler.handle(wrap(payload.data()))));
    }

    @Override
    public void sendFx(ServerPlayer player, FriendlyByteBuf data) {
        ServerPlayNetworking.send(player, new FxPayload(toBytes(data)));
    }

    @Override
    public void sendToServer(FriendlyByteBuf data) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ControlPayload(toBytes(data)));
    }

    @Override
    public FriendlyByteBuf createBuffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static byte[] toBytes(FriendlyByteBuf buf) {
        final byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    private static FriendlyByteBuf wrap(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }
}
