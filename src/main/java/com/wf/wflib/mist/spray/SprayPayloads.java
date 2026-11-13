package com.wf.wflib.mist.spray;

import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.mist.api.MistEffects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid (id, then tag in registration order) -> {@link SprayPayload}. A payload's {@code gas} is also registered in
 * {@link MistEffects}. A fluid only {@code MistEffects} knows is a gas payload without a record ({@link #fillable}).
 */
public final class SprayPayloads {

    private static final WFRegistry<SprayPayload> BY_FLUID = WFRegistry.create("mist", "spray payloads by fluid");
    private static final OrderedRegistry<ByTag> BY_TAG = OrderedRegistry.create("mist", "spray payloads by tag");

    private record ByTag(TagKey<Fluid> tag, SprayPayload payload) {
    }

    private SprayPayloads() {
    }

    public static void register(ResourceLocation fluidId, SprayPayload payload) {
        BY_FLUID.register(fluidId, payload);
        if (payload.gas() != null) {
            MistEffects.register(fluidId, payload.gas());
        }
    }

    public static void register(TagKey<Fluid> tag, SprayPayload payload) {
        BY_TAG.register(tag.location(), 0, new ByTag(tag, payload));
        if (payload.gas() != null) {
            MistEffects.register(tag, payload.gas());
        }
    }

    @Nullable
    public static SprayPayload get(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return null;
        }
        SprayPayload direct = BY_FLUID.get(BuiltInRegistries.FLUID.getKey(fluid));
        if (direct != null) {
            return direct;
        }
        for (ByTag entry : BY_TAG.values()) {
            if (fluid.defaultFluidState().is(entry.tag)) {
                return entry.payload;
            }
        }
        return null;
    }

    /** Payload or mist effect => spray tanks take it. */
    public static boolean fillable(Fluid fluid) {
        return get(fluid) != null || MistEffects.get(fluid) != null;
    }
}
