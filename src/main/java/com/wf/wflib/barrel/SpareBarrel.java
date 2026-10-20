package com.wf.wflib.barrel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** {@code wflib:spare_barrel} component: quick-change family + the barrel's own state. */
public record SpareBarrel(ResourceLocation type, BarrelState state) {
    public static final Codec<SpareBarrel> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(SpareBarrel::type),
            BarrelState.CODEC.optionalFieldOf("state", BarrelState.NEW).forGetter(SpareBarrel::state)
    ).apply(i, SpareBarrel::new));

    public static final StreamCodec<ByteBuf, SpareBarrel> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SpareBarrel::type,
            BarrelState.STREAM_CODEC, SpareBarrel::state,
            SpareBarrel::new);
}
