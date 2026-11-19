package com.wf.wflib.photo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a printed photograph item carries: the id of a stored photo and who took it. */
public record PhotoRef(UUID id, String author) {
    public static final Codec<PhotoRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(PhotoRef::id),
            Codec.STRING.fieldOf("author").forGetter(PhotoRef::author)).apply(i, PhotoRef::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoRef> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PhotoRef::id,
            ByteBufCodecs.stringUtf8(64), PhotoRef::author,
            PhotoRef::new);
}
