package com.wf.wflib.rail;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import com.wf.wflib.core.api.WFLib;

/** A surveyor taking one of their faction's lines off the map. */
public record RailDeletePacket(UUID id) implements CustomPacketPayload {

    public static final Type<RailDeletePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rail_delete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailDeletePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeUUID(pkt.id), buf -> new RailDeletePacket(buf.readUUID()));

    @Override
    public Type<RailDeletePacket> type() {
        return TYPE;
    }
}
