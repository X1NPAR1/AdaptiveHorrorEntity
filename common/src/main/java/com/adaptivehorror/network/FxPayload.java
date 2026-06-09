package com.adaptivehorror.network;

import com.adaptivehorror.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C effect payload. 1.21 networking is type-safe: every packet is a {@link CustomPacketPayload}
 * with a {@link Type} id and a {@link StreamCodec}. We keep the mod's "single discriminated stream"
 * design by carrying the raw effect bytes (built by {@link HorrorNet}) inside one payload type; the
 * leading {@link HorrorNet.FxType} byte selects behaviour on the client.
 */
public record FxPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<FxPayload> TYPE = new Type<>(Constants.id("fx"));

    public static final StreamCodec<FriendlyByteBuf, FxPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByteArray(payload.data),
            buf -> new FxPayload(buf.readByteArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
