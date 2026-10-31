package com.wf.wflib.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wf.wflib.core.api.WFSystems;
import net.neoforged.neoforge.common.conditions.ICondition;

/** {@code {"type": "wflib:system", "system": "anomaly"}} in {@code neoforge:conditions}: data of an off system is skipped. */
public record SystemCondition(String system) implements ICondition {
    public static final MapCodec<SystemCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.STRING.fieldOf("system").forGetter(SystemCondition::system))
            .apply(instance, SystemCondition::new));

    @Override
    public boolean test(IContext context) {
        return WFSystems.enabled(system);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
