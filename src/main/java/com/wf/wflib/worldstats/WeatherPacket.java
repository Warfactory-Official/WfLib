package com.wf.wflib.worldstats;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: what the weather will do, in ticks. A client cannot see its own level's weather timers. */
public record WeatherPacket(int clear, int rain, int thunder, boolean raining, boolean thundering) implements CustomPacketPayload {
    public static final Type<WeatherPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "weather_timing"));
    public static final StreamCodec<FriendlyByteBuf, WeatherPacket> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.clear);
                b.writeVarInt(p.rain);
                b.writeVarInt(p.thunder);
                b.writeBoolean(p.raining);
                b.writeBoolean(p.thundering);
            },
            b -> new WeatherPacket(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean()));

    @Override
    public Type<WeatherPacket> type() {
        return TYPE;
    }
}
