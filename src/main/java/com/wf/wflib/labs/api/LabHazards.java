package com.wf.wflib.labs.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;

/** Hazards owners register at construction (guard {@code WFSystems.enabled("labs")}). */
public final class LabHazards {
    public static final WFRegistry<LabHazard> REGISTRY = WFRegistry.create("labs", "lab hazards");

    private LabHazards() {
    }

    public static LabHazard register(LabHazard hazard) {
        return REGISTRY.register(ResourceLocation.parse(hazard.id()), hazard);
    }
}
