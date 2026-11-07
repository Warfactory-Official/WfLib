package com.wf.wflib.labs.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;

/** Stages dependents fill at mod construction (guard with {@code WFSystems.enabled("labs")}); id is a bare word, namespaced as {@code wflabs:<id>} in the registry. */
public final class ScenarioStages {
    public static final WFRegistry<ScenarioStage> REGISTRY = WFRegistry.create("labs", "scenario stages");

    private ScenarioStages() {
    }

    public static ScenarioStage register(ScenarioStage stage) {
        return REGISTRY.register(ResourceLocation.fromNamespaceAndPath("wflabs", stage.id()), stage);
    }
}
