package com.adaptivehorror.network;

import com.adaptivehorror.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S control payload (currently just disclaimer acceptance). Mirrors {@link FxPayload} but on the
 * client-to-server channel; the leading {@link HorrorNet.C2SType} byte selects the action.
 */
public record ControlPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<ControlPayload> TYPE = new Type<>(Constants.id("control"));

    public static final StreamCodec<FriendlyByteBuf, ControlPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByteArray(payload.data),
            buf -> new ControlPayload(buf.readByteArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
