package com.wf.wflib.photo;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: chunk {@code index} of {@code total} (in order); {@code total == 0} = refused (missing / not yours / busy). */
public record PhotoChunkPacket(int transfer, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final Type<PhotoChunkPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_chunk"));
    public static final StreamCodec<FriendlyByteBuf, PhotoChunkPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhotoChunkPacket::transfer,
            ByteBufCodecs.VAR_INT, PhotoChunkPacket::index,
            ByteBufCodecs.VAR_INT, PhotoChunkPacket::total,
            ByteBufCodecs.byteArray(PhotoLimits.DOWN_CHUNK), PhotoChunkPacket::data,
            PhotoChunkPacket::new);

    @Override
    public Type<PhotoChunkPacket> type() {
        return TYPE;
    }
}
