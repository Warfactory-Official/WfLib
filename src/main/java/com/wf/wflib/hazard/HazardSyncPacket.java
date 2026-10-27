package com.wf.wflib.hazard;

import com.wf.wflib.WFLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owner-only hazard state, once a second (NTM HbmPlayerSyncPayload, hazard fields only). */
public record HazardSyncPacket(double radiation, double digamma, double radBuf, int asbestos, int blacklung)
        implements CustomPacketPayload {

    public static final Type<HazardSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "hazard_sync"));
    public static final StreamCodec<ByteBuf, HazardSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, HazardSyncPacket::radiation,
            ByteBufCodecs.DOUBLE, HazardSyncPacket::digamma,
            ByteBufCodecs.DOUBLE, HazardSyncPacket::radBuf,
            ByteBufCodecs.VAR_INT, HazardSyncPacket::asbestos,
            ByteBufCodecs.VAR_INT, HazardSyncPacket::blacklung,
            HazardSyncPacket::new);

    static HazardSyncPacket of(LivingHazardProps p) {
        return new HazardSyncPacket(p.radiation, p.digamma, p.radBuf, p.asbestos, p.blacklung);
    }

    @Override
    public Type<HazardSyncPacket> type() {
        return TYPE;
    }
}
