package com.wf.wflib.pda.music;

import com.wf.wflib.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Download one library file (or, {@code background}, one background image) as {@link MusicChunkPacket}s tagged {@code transfer}; "" = cancel. */
public record MusicFetchPacket(int transfer, boolean background, String name) implements CustomPacketPayload {
    public static final Type<MusicFetchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_music_fetch"));
    public static final StreamCodec<FriendlyByteBuf, MusicFetchPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MusicFetchPacket::transfer,
            ByteBufCodecs.BOOL, MusicFetchPacket::background,
            ByteBufCodecs.stringUtf8(OggAudio.MAX_NAME), MusicFetchPacket::name,
            MusicFetchPacket::new);

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> ServerMusic.fetch(player, transfer, background, name));
        }
    }

    @Override
    public Type<MusicFetchPacket> type() {
        return TYPE;
    }
}
