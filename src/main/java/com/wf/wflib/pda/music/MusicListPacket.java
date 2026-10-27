package com.wf.wflib.pda.music;

import com.wf.wflib.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Validated server library; capped at {@link ServerMusic#MAX_ENTRIES}. */
public record MusicListPacket(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<MusicListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_music_list"));

    public record Entry(String name, String title, String artist, String album, long millis, int size) {
        static final StreamCodec<FriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(OggAudio.MAX_NAME), Entry::name,
                ByteBufCodecs.stringUtf8(128), Entry::title,
                ByteBufCodecs.stringUtf8(128), Entry::artist,
                ByteBufCodecs.stringUtf8(128), Entry::album,
                ByteBufCodecs.VAR_LONG, Entry::millis,
                ByteBufCodecs.VAR_INT, Entry::size,
                Entry::new);
    }

    public static final StreamCodec<FriendlyByteBuf, MusicListPacket> STREAM_CODEC = StreamCodec.composite(
            Entry.CODEC.apply(ByteBufCodecs.list(ServerMusic.MAX_ENTRIES)), MusicListPacket::entries,
            MusicListPacket::new);

    @Override
    public Type<MusicListPacket> type() {
        return TYPE;
    }
}
