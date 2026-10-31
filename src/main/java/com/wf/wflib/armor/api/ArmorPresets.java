package com.wf.wflib.armor.api;

import com.wf.wflib.armor.ArmorCatalogue;
import com.wf.wflib.core.api.WFLibContent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Armour catalogue (registered iff requested via {@link WFLibContent}); other mods build their own {@link ArmorPreset}s outside it. */
public final class ArmorPresets {
    private static volatile List<ArmorPreset> live = List.of();

    private ArmorPresets() {
    }

    public static boolean defined(ResourceLocation id) {
        return ArmorCatalogue.get(id) != null;
    }

    public static Collection<ArmorPreset> catalogue() {
        return ArmorCatalogue.all();
    }

    /** Registered presets, catalogue order. */
    public static List<ArmorPreset> live() {
        return live;
    }

    /** Registered preset, else null. */
    @Nullable
    public static ArmorPreset get(ResourceLocation id) {
        for (ArmorPreset p : live) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /** {@link WFLibContent#freeze} only. */
    public static void freeze(Set<ResourceLocation> requested) {
        live = ArmorCatalogue.all().stream().filter(p -> requested.contains(p.id())).toList();
    }
}
