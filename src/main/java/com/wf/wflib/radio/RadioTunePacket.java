package com.wf.wflib.radio;

import com.wf.wflib.radio.api.RadioCarrier;
import com.wf.wflib.radio.api.RadioTuning;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;
import com.wf.wflib.core.api.WFLib;

/** Tune {@code carrier}; see {@link RadioTuning#tune}. */
public record RadioTunePacket(UUID carrier, String station) implements CustomPacketPayload {
    public static final Type<RadioTunePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "radio_tune"));
    public static final StreamCodec<FriendlyByteBuf, RadioTunePacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RadioTunePacket::carrier, ByteBufCodecs.stringUtf8(RadioCarrier.MAX_STATION),
            RadioTunePacket::station, RadioTunePacket::new);

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> RadioTuning.tune(player, carrier, station));
        }
    }

    @Override
    public Type<RadioTunePacket> type() {
        return TYPE;
    }
}
