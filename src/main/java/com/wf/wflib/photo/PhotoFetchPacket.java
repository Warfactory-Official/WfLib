package com.wf.wflib.photo;

import com.wf.wflib.core.api.WFLib;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: stream photo {@code id} back as {@link PhotoChunkPacket}s tagged {@code transfer}. */
public record PhotoFetchPacket(int transfer, UUID id) implements CustomPacketPayload {
    public static final Type<PhotoFetchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_fetch"));
    public static final StreamCodec<FriendlyByteBuf, PhotoFetchPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhotoFetchPacket::transfer,
            UUIDUtil.STREAM_CODEC, PhotoFetchPacket::id,
            PhotoFetchPacket::new);

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> PhotoServer.fetch(player, transfer, id));
        }
    }

    @Override
    public Type<PhotoFetchPacket> type() {
        return TYPE;
    }
}
