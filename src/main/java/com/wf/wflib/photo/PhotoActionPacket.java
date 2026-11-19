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

/** Client to server: delete or print one of the sender's photographs. */
public record PhotoActionPacket(int op, UUID id) implements CustomPacketPayload {
    public static final int DELETE = 0;
    public static final int PRINT = 1;
    public static final Type<PhotoActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_action"));
    public static final StreamCodec<FriendlyByteBuf, PhotoActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhotoActionPacket::op,
            UUIDUtil.STREAM_CODEC, PhotoActionPacket::id,
            PhotoActionPacket::new);

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> PhotoServer.action(player, op, id));
        }
    }

    @Override
    public Type<PhotoActionPacket> type() {
        return TYPE;
    }
}
