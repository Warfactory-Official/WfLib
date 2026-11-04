package com.wf.wflib.labs.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** Air defences for the {@code interception} table. Register at construction or common setup. */
public final class LabDefenders {
    public static final WFRegistry<LabDefender> REGISTRY = WFRegistry.create("labs", "lab defenders");
    /** Suppliers evaluated at each run (data-driven defences); same registration window. */
    public static final WFRegistry<Supplier<List<LabDefender>>> LAZY = WFRegistry.create("labs", "lab defender suppliers");

    private LabDefenders() {
    }

    public static LabDefender register(LabDefender defender) {
        return REGISTRY.register(ResourceLocation.parse(defender.id()), defender);
    }

    /** {@code id} only names the supplier. */
    public static void registerLazy(String id, Supplier<List<LabDefender>> defenders) {
        LAZY.register(ResourceLocation.parse(id), defenders);
    }

    public static List<LabDefender> all() {
        List<LabDefender> out = new ArrayList<>(REGISTRY.values());
        LAZY.values().forEach(s -> out.addAll(s.get()));
        out.sort(Comparator.comparing(LabDefender::id));
        return out;
    }
}
