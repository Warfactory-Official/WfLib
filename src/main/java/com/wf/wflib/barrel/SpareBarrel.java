package com.wf.wflib.barrel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * {@code wflib:spare_barrel} component: quick-change family, {@link BarrelSpec#variants} key (empty = standard), the
 * barrel's own state.
 */
public record SpareBarrel(ResourceLocation type, Optional<String> variant, BarrelState state) {
    public static final Codec<SpareBarrel> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(SpareBarrel::type),
            Codec.STRING.optionalFieldOf("variant").forGetter(SpareBarrel::variant),
            BarrelState.CODEC.optionalFieldOf("state", BarrelState.NEW).forGetter(SpareBarrel::state)
    ).apply(i, SpareBarrel::new));

    public static final StreamCodec<ByteBuf, SpareBarrel> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SpareBarrel::type,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), SpareBarrel::variant,
            BarrelState.STREAM_CODEC, SpareBarrel::state,
            SpareBarrel::new);
}
