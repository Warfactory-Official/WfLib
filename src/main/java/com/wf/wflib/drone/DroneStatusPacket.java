package com.wf.wflib.drone;

import com.wf.wflib.drone.api.DroneStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.core.api.WFLib;

public record DroneStatusPacket(DroneStatus status) implements CustomPacketPayload {
    public static final Type<DroneStatusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_drone_status"));
    public static final StreamCodec<FriendlyByteBuf, DroneStatusPacket> STREAM_CODEC =
            DroneStatus.STREAM_CODEC.map(DroneStatusPacket::new, DroneStatusPacket::status);

    @Override
    public Type<DroneStatusPacket> type() {
        return TYPE;
    }
}
