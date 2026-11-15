package com.wf.wflib.labs.api;

import com.wf.wflib.core.api.WFRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Shelters; {@code hazards} tables run every hazard in the open plus in each of these. Same freeze rules as {@link LabLaunchers}. */
public final class LabShelters {
    public static final WFRegistry<LabShelter> REGISTRY = WFRegistry.create("labs", "lab shelters");
    public static final WFRegistry<Supplier<List<LabShelter>>> LAZY = WFRegistry.create("labs", "lab shelter suppliers");

    private LabShelters() {
    }

    public static LabShelter register(LabShelter shelter) {
        return REGISTRY.register(ResourceLocation.parse(shelter.id()), shelter);
    }

    public static void registerLazy(String id, Supplier<List<LabShelter>> shelters) {
        LAZY.register(ResourceLocation.parse(id), shelters);
    }

    public static List<LabShelter> all() {
        List<LabShelter> out = new ArrayList<>(REGISTRY.values());
        LAZY.values().forEach(s -> out.addAll(s.get()));
        out.sort(Comparator.comparing(LabShelter::id));
        return out;
    }

    /** {@code [item][shelter]} = {@link LabShelter#shield} for an unarmoured victim; call on the server thread, before fan-out. */
    public static <T> double[][] shield(ServerLevel level, List<LabShelter> shelters, List<T> items,
                                        Function<T, String> kind,
                                        ToDoubleFunction<T> amount) {
        LivingEntity bare = LabRows.victim(level);
        double[][] out = new double[items.size()][shelters.size()];
        for (int i = 0; i < items.size(); i++) {
            for (int j = 0; j < shelters.size(); j++) {
                out[i][j] = shelters.get(j).shield(bare, kind.apply(items.get(i)), amount.applyAsDouble(items.get(i)));
            }
        }
        return out;
    }
}
