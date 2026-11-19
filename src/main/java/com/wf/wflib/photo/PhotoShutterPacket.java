package com.wf.wflib.photo;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: take the picture now, from this eye and orientation, and upload it tagged {@code token}. */
public record PhotoShutterPacket(long token, double x, double y, double z, float yaw, float pitch)
        implements CustomPacketPayload {
    public static final Type<PhotoShutterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_shutter"));
    public static final StreamCodec<FriendlyByteBuf, PhotoShutterPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PhotoShutterPacket::token,
            ByteBufCodecs.DOUBLE, PhotoShutterPacket::x,
            ByteBufCodecs.DOUBLE, PhotoShutterPacket::y,
            ByteBufCodecs.DOUBLE, PhotoShutterPacket::z,
            ByteBufCodecs.FLOAT, PhotoShutterPacket::yaw,
            ByteBufCodecs.FLOAT, PhotoShutterPacket::pitch,
            PhotoShutterPacket::new);

    @Override
    public Type<PhotoShutterPacket> type() {
        return TYPE;
    }
}
