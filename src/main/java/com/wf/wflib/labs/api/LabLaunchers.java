package com.wf.wflib.labs.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Launchers dependents register until {@code FMLLoadCompleteEvent} (mod construction or {@code FMLCommonSetupEvent}
 * work); {@link #registerLazy} suppliers run at each lab run, so they may read any registry. Every live round also gets a
 * built-in default.
 */
public final class LabLaunchers {
    public static final WFRegistry<LabLauncher> REGISTRY = WFRegistry.create("labs", "lab launchers");

    /** Suppliers evaluated at run time (after every registry is frozen); register at construction or in common setup. */
    public static final WFRegistry<java.util.function.Supplier<java.util.List<LabLauncher>>> LAZY =
            WFRegistry.create("labs", "lab launcher suppliers");

    private LabLaunchers() {
    }

    /** {@code id} only names the supplier. */
    public static void registerLazy(String id, java.util.function.Supplier<java.util.List<LabLauncher>> launchers) {
        LAZY.register(ResourceLocation.parse(id), launchers);
    }

    public static LabLauncher register(LabLauncher launcher) {
        return REGISTRY.register(ResourceLocation.parse(launcher.id()), launcher);
    }
}
