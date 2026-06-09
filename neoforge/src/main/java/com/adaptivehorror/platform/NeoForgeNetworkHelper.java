package com.adaptivehorror.platform;

import com.adaptivehorror.network.ClientFxHandler;
import com.adaptivehorror.network.ControlPayload;
import com.adaptivehorror.network.FxPayload;
import com.adaptivehorror.network.ServerFxHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge implementation of {@link INetworkHelper} over 1.21 typed payloads. Payload types and their
 * handlers are registered from {@link #register(RegisterPayloadHandlersEvent)} (forwarded by the main
 * class on the mod bus). Sending wraps the {@link com.adaptivehorror.network.HorrorNet}-built byte
 * buffer in the matching payload and routes it via {@link PacketDistributor}.
 */
public final class NeoForgeNetworkHelper implements INetworkHelper {

    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(FxPayload.TYPE, FxPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientFxHandler.handle(wrap(payload.data()))));
        registrar.playToServer(ControlPayload.TYPE, ControlPayload.CODEC, (payload, context) ->
                context.enqueueWork(() ->
                        ServerFxHandler.handle((ServerPlayer) context.player(), wrap(payload.data()))));
    }

    @Override
    public void sendFx(ServerPlayer player, FriendlyByteBuf data) {
        PacketDistributor.sendToPlayer(player, new FxPayload(toBytes(data)));
    }

    @Override
    public void sendToServer(FriendlyByteBuf data) {
        PacketDistributor.sendToServer(new ControlPayload(toBytes(data)));
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
