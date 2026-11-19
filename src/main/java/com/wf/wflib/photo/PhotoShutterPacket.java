package com.wf.wflib.photo;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: take the picture now, from this eye, orientation and vertical field of view, and upload it tagged {@code token}. */
public record PhotoShutterPacket(long token, double x, double y, double z, float yaw, float pitch, float fov)
        implements CustomPacketPayload {
    public static final Type<PhotoShutterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_shutter"));
    public static final StreamCodec<FriendlyByteBuf, PhotoShutterPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarLong(p.token);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.yaw);
                buf.writeFloat(p.pitch);
                buf.writeFloat(p.fov);
            },
            buf -> new PhotoShutterPacket(buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<PhotoShutterPacket> type() {
        return TYPE;
    }
}
