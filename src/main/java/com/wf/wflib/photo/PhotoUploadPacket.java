package com.wf.wflib.photo;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: fragment {@code index} of {@code total} of the JPEG for shutter {@code token}. */
public record PhotoUploadPacket(long token, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final Type<PhotoUploadPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_upload"));
    public static final StreamCodec<FriendlyByteBuf, PhotoUploadPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PhotoUploadPacket::token,
            ByteBufCodecs.VAR_INT, PhotoUploadPacket::index,
            ByteBufCodecs.VAR_INT, PhotoUploadPacket::total,
            ByteBufCodecs.byteArray(PhotoLimits.UP_CHUNK), PhotoUploadPacket::data,
            PhotoUploadPacket::new);

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> PhotoServer.upload(player, this));
        }
    }

    @Override
    public Type<PhotoUploadPacket> type() {
        return TYPE;
    }
}
