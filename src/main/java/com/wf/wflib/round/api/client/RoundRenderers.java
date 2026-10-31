package com.wf.wflib.round.api.client;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Preset or missile model id -> {@link RoundRenderer}. Precedes the glTF model. */
public final class RoundRenderers {

    private static final WFRegistry<RoundRenderer> BY_ID = WFRegistry.create("round", "round renderers");

    private RoundRenderers() {
    }

    public static void register(ResourceLocation id, RoundRenderer renderer) {
        BY_ID.register(id, renderer);
    }

    @Nullable
    public static RoundRenderer get(ResourceLocation id) {
        return BY_ID.get(id);
    }
}
