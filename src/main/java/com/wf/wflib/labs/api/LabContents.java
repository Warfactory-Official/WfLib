package com.wf.wflib.labs.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Provenance sources and dev-content flags. */
public final class LabContents {
    public static final WFRegistry<LabContent> REGISTRY = WFRegistry.create("labs", "lab content");
    private static final Set<String> DEV = ConcurrentHashMap.newKeySet();

    public static final String SHIPPED = "shipped";
    public static final String DEV_ORIGIN = "dev";

    private LabContents() {
    }

    public static LabContent register(LabContent content) {
        return REGISTRY.register(ResourceLocation.parse(content.id()), content);
    }

    /** Test fixtures call this for ids they add. */
    public static void markDev(String contentId) {
        DEV.add(contentId);
    }

    /** {@link #SHIPPED} or {@link #DEV_ORIGIN} for a content id (preset, armour piece, launcher). */
    public static String origin(String contentId) {
        if (DEV.contains(contentId)) {
            return DEV_ORIGIN;
        }
        for (LabContent c : REGISTRY.values()) {
            if (c.dev(contentId)) {
                return DEV_ORIGIN;
            }
        }
        return SHIPPED;
    }
}
