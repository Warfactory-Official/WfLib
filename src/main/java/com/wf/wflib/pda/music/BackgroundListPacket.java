package com.wf.wflib.pda.music;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import com.wf.wflib.core.api.WFLib;

/** Valid files of the server background folder. */
public record BackgroundListPacket(List<String> names) implements CustomPacketPayload {
    public static final Type<BackgroundListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_background_list"));
    public static final StreamCodec<FriendlyByteBuf, BackgroundListPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(BackgroundImage.MAX_NAME).apply(ByteBufCodecs.list(BackgroundImage.MAX_ENTRIES)),
            BackgroundListPacket::names, BackgroundListPacket::new);

    @Override
    public Type<BackgroundListPacket> type() {
        return TYPE;
    }
}
