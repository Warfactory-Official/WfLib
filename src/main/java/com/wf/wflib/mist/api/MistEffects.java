package com.wf.wflib.mist.api;

import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/** The lookup that decides what a fluid does as mist. */
public final class MistEffects {

    private static final WFRegistry<MistEffect> BY_FLUID = WFRegistry.create("mist", "mist effects by fluid");
    private static final OrderedRegistry<ByTag> BY_TAG = OrderedRegistry.create("mist", "mist effects by tag");

    private record ByTag(TagKey<Fluid> tag, MistEffect effect) {
    }

    private MistEffects() {
    }

    public static void register(ResourceLocation fluidId, MistEffect effect) {
        BY_FLUID.register(fluidId, effect);
    }

    public static void register(TagKey<Fluid> tag, MistEffect effect) {
        BY_TAG.register(tag.location(), 0, new ByTag(tag, effect));
    }

    /**
     * @return the effect for this fluid, or {@code null} if the fluid is inert as mist
     */
    @Nullable
    public static MistEffect get(@Nullable Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) return null;
        MistEffect direct = BY_FLUID.get(BuiltInRegistries.FLUID.getKey(fluid));
        if (direct != null) return direct;
        for (ByTag entry : BY_TAG.values()) {
            if (fluid.defaultFluidState().is(entry.tag)) return entry.effect;
        }
        return null;
    }
}
