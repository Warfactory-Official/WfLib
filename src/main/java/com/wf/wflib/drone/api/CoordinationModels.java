package com.wf.wflib.drone.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.drone.ai.coord.LeaderFollower;
import com.wf.wflib.drone.ai.coord.VirtualStructure;
import com.wf.wflib.core.api.WFLib;

/**
 * Registry of {@link CoordinationModel}s, keyed by id in the same shape as {@code Formations}: add an architecture
 * by implementing the interface and calling {@link #register}.
 */
public final class CoordinationModels {

    public static final ResourceLocation DEFAULT = rl(VirtualStructure.INSTANCE.id());
    /**
     * What a squad saved before coordination models existed is read back as: the architecture it was actually
     * flying.
     */
    public static final ResourceLocation LEGACY = rl(LeaderFollower.INSTANCE.id());

    public static final WFRegistry<CoordinationModel> REGISTRY =
            WFRegistry.create("drone", "drone.coordination_models");

    private CoordinationModels() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static CoordinationModel get(ResourceLocation id) {
        CoordinationModel model = id == null ? null : REGISTRY.get(id);
        return model != null ? model : REGISTRY.get(DEFAULT);
    }

    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null && REGISTRY.containsKey(parsed) ? parsed : DEFAULT;
    }

    public static Set<ResourceLocation> ids() {
        return REGISTRY.ids();
    }
}
