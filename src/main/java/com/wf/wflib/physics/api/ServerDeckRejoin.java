package com.wf.wflib.physics.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wf.wflib.core.api.WFLib;

/** carrierId: HOLD => freeze; RELEASE => let go; >= 0 => hull placed on, local pose in its frame. */
public record ServerDeckRejoin(int carrierId, double x, double y, double z, float yaw) implements CustomPacketPayload {

    public static final int HOLD = -2;
    public static final int RELEASE = -1;

    public static final StreamCodec<FriendlyByteBuf, ServerDeckRejoin> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeVarInt(msg.carrierId + 2);
                buf.writeDouble(msg.x);
                buf.writeDouble(msg.y);
                buf.writeDouble(msg.z);
                buf.writeFloat(msg.yaw);
            },
            buf -> new ServerDeckRejoin(buf.readVarInt() - 2, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
    public static final CustomPacketPayload.Type<ServerDeckRejoin> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "deck_rejoin"));

    public static ServerDeckRejoin hold() {
        return new ServerDeckRejoin(HOLD, 0, 0, 0, 0);
    }

    public static ServerDeckRejoin release() {
        return new ServerDeckRejoin(RELEASE, 0, 0, 0, 0);
    }

    public static ServerDeckRejoin place(int carrierId, double x, double y, double z, float yaw) {
        return new ServerDeckRejoin(carrierId, x, y, z, yaw);
    }

    public static void handle(ServerDeckRejoin message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> DeckRejoin.onClientMessage(ctx.player(), message));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
