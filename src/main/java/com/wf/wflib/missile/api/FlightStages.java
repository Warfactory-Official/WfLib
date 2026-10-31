package com.wf.wflib.missile.api;

import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.missile.api.MissileEntity.Phase;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** {@link FlightStage}s per {@link Phase}; the phase's first registration is its default. */
public final class FlightStages {

    private static final Map<Phase, WFRegistry<FlightStage>> BY_PHASE = new EnumMap<>(Phase.class);

    static {
        for (Phase phase : Phase.values()) {
            BY_PHASE.put(phase, WFRegistry.create("missile", "flight stages " + phase));
        }
    }

    private FlightStages() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static ResourceLocation keyOf(FlightStage stage) {
        return rl(stage.id());
    }

    public static ResourceLocation parse(Phase phase, String id) {
        if (id == null || id.isEmpty()) {
            return defaultId(phase);
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null ? parsed : defaultId(phase);
    }

    public static void register(Phase phase, FlightStage stage) {
        BY_PHASE.get(phase).register(keyOf(stage), stage);
    }

    public static FlightStage get(Phase phase, ResourceLocation id) {
        WFRegistry<FlightStage> stages = BY_PHASE.get(phase);
        FlightStage stage = stages.get(id);
        if (stage != null) {
            return stage;
        }
        ResourceLocation fallback = defaultId(phase);
        return fallback == null ? null : stages.get(fallback);
    }

    public static boolean exists(Phase phase, ResourceLocation id) {
        return BY_PHASE.get(phase).containsKey(id);
    }

    public static ResourceLocation defaultId(Phase phase) {
        Set<ResourceLocation> ids = BY_PHASE.get(phase).ids();
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    public static Set<ResourceLocation> ids(Phase phase) {
        return BY_PHASE.get(phase).ids();
    }
}
