package com.wf.wflib.photo;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: send me my gallery list. */
public record PhotoListRequestPacket() implements CustomPacketPayload {
    public static final Type<PhotoListRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_list_request"));
    public static final StreamCodec<FriendlyByteBuf, PhotoListRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new PhotoListRequestPacket());

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> PhotoServer.list(player));
        }
    }

    @Override
    public Type<PhotoListRequestPacket> type() {
        return TYPE;
    }
}
