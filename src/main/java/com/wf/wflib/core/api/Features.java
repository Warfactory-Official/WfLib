package com.wf.wflib.core.api;

import com.wf.wflib.core.features.FeatureCatalogue;
import com.wf.wflib.core.features.FeatureCatalogue.Feature;
import com.wf.wflib.core.features.FeatureCatalogue.Group;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which of wflib's features are on, as read from {@code wflib-features.toml} at startup (a change takes effect after a restart,
 * so the answer never moves while the game runs). A feature is on when its group's master switch is on, its own box is ticked,
 * and, for a flag, the system it needs is running. Call {@link #on} wherever a part of a system should do nothing when switched
 * off; the PDA asks {@link #appShown} for every app.
 */
public final class Features {
    private static volatile Map<String, Boolean> read = Map.of();
    private static volatile Set<String> hiddenApps = Set.of();

    private Features() {
    }

    /** Installed once by the config at startup (and by tests). */
    public static void install(Map<String, Boolean> values) {
        read = Map.copyOf(values);
        Set<String> hidden = new HashSet<>();
        for (Feature f : FeatureCatalogue.all()) if (!configuredOn(values, f)) hidden.addAll(f.apps());
        hiddenApps = Set.copyOf(hidden);
    }

    /** The values the game started with, for telling what a restart would change. */
    public static Map<String, Boolean> startup() {
        return read;
    }

    /** The box a player ticked, with its group: before any system's requirements are counted. */
    public static boolean configuredOn(Map<String, Boolean> values, Feature f) {
        Group g = FeatureCatalogue.groupOf(f.id());
        return values.getOrDefault(g.id() + ".enabled", true) && values.getOrDefault(f.id(), f.defaultOn());
    }

    /** True when the feature is on. Features nobody listed are on. */
    public static boolean on(String id) {
        Feature f = FeatureCatalogue.byId(id);
        if (read.isEmpty()) return f.defaultOn();
        if (!configuredOn(read, f)) return false;
        return f.needsSystem() == null || WFSystems.enabled(f.needsSystem());
    }

    /** Whether the PDA may list this app: false while a feature that owns it is off. */
    public static boolean appShown(ResourceLocation app) {
        return !hiddenApps.contains(app.toString());
    }

    /** Whether the config switched {@code system} on: its feature is ticked and its group is on. A system no feature names is not gated. */
    public static boolean systemConfigured(String system, boolean fallback) {
        Feature f = FeatureCatalogue.ofSystem(system);
        return f == null ? fallback : read.isEmpty() ? f.defaultOn() : configuredOn(read, f);
    }
}
