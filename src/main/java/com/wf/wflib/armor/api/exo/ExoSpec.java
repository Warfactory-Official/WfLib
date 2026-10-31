package com.wf.wflib.armor.api.exo;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Exoskeleton capabilities of one garment.
 *
 * @param powered / unpowered  stats by power state; unpowered ignored while energy is disabled
 * @param drawPerSecond        energy per second in the configured unit, while worn
 * @param battery / belt       popup slots offered
 * @param repairTags           item tags that repair parts
 */
public record ExoSpec(ExoStats powered, ExoStats unpowered, double drawPerSecond, List<ExoPart> parts,
                      boolean battery, boolean belt, Set<ResourceLocation> repairTags) {
    public static final ResourceLocation EXOSKELETON = ResourceLocation.fromNamespaceAndPath("wflib", "exoskeleton");
    public static final ResourceLocation HEAVY = ResourceLocation.fromNamespaceAndPath("wflib", "armor/heavy");

    public ExoSpec {
        if (drawPerSecond < 0) {
            throw new IllegalArgumentException("drawPerSecond " + drawPerSecond);
        }
        parts = List.copyOf(parts);
        repairTags = repairTags.isEmpty() ? Set.of(EXOSKELETON, HEAVY) : Set.copyOf(repairTags);
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (ExoPart p : parts) {
            if (!ids.add(p.id())) {
                throw new IllegalArgumentException("duplicate exo part " + p.id());
            }
        }
    }

    public boolean hasSlots() {
        return battery || belt;
    }

    public int partIndex(String id) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
