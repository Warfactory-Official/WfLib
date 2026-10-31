package com.wf.wflib.pda.music;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.core.api.WFLib;

/** Chunk {@code index} of {@code total} (in order); {@code total == 0} = refused (missing / invalid / busy). */
public record MusicChunkPacket(int transfer, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final int CHUNK = 60_000;
    public static final Type<MusicChunkPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_music_chunk"));
    public static final StreamCodec<FriendlyByteBuf, MusicChunkPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MusicChunkPacket::transfer,
            ByteBufCodecs.VAR_INT, MusicChunkPacket::index,
            ByteBufCodecs.VAR_INT, MusicChunkPacket::total,
            ByteBufCodecs.byteArray(CHUNK), MusicChunkPacket::data,
            MusicChunkPacket::new);

    /** Chunks of a maximum-size file. */
    public static final int MAX_CHUNKS = (OggAudio.MAX_BYTES + CHUNK - 1) / CHUNK;

    @Override
    public Type<MusicChunkPacket> type() {
        return TYPE;
    }
}
