package com.wf.wflib.rail;

import com.wf.wflib.rail.api.RouteStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import com.wf.wflib.core.api.WFLib;

/** Moving a route along its lifecycle: a draft becomes a plan, a plan becomes track. */
public record RailStatusPacket(UUID id, RouteStatus status) implements CustomPacketPayload {

    public static final Type<RailStatusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rail_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailStatusPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUUID(pkt.id);
                        buf.writeVarInt(pkt.status.ordinal());
                    },
                    buf -> new RailStatusPacket(buf.readUUID(),
                            RouteStatus.byOrdinal(buf.readVarInt())));

    @Override
    public Type<RailStatusPacket> type() {
        return TYPE;
    }
}
