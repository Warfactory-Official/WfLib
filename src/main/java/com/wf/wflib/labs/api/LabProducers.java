package com.wf.wflib.labs.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;

/** Producers dependents fill at mod construction (guard with {@code WFSystems.enabled("labs")}). */
public final class LabProducers {
    public static final WFRegistry<LabProducer> REGISTRY = WFRegistry.create("labs", "lab producers");

    private LabProducers() {
    }

    public static LabProducer register(LabProducer producer) {
        return REGISTRY.register(ResourceLocation.parse(producer.id()), producer);
    }
}
