package com.wf.wflib.warhead;

import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.warhead.api.Warheads.Detonation;
import com.wf.wflib.warhead.api.Warheads.InterceptDetonation;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** WFLib's own warheads: defined at startup, live only once {@link #require}d. */
public final class Catalogue {
    private static final WFRegistry<Detonation> DETONATIONS = WFRegistry.create("warhead", "warhead catalogue");
    private static final WFRegistry<InterceptDetonation> INTERCEPTS = WFRegistry.create("warhead", "warhead catalogue intercepts");
    private static final WFRegistry<Float> BLAST = WFRegistry.create("warhead", "warhead catalogue blast");
    /** Spawned sub-munition warheads: live with the parent. */
    private static final WFRegistry<ResourceLocation> CHILD = WFRegistry.create("warhead", "warhead catalogue children");

    private static volatile Map<ResourceLocation, Detonation> warheads = Map.of();
    private static volatile Map<ResourceLocation, InterceptDetonation> intercepts = Map.of();
    private static volatile Map<ResourceLocation, Float> blastSizes = Map.of();

    private Catalogue() {
    }

    public static void define(ResourceLocation id, Detonation detonation) {
        DETONATIONS.register(id, detonation);
    }

    public static void define(ResourceLocation id, Detonation detonation, InterceptDetonation intercept) {
        DETONATIONS.register(id, detonation);
        INTERCEPTS.register(id, intercept);
    }

    public static void blast(ResourceLocation id, float size) {
        BLAST.register(id, size);
    }

    public static void child(ResourceLocation parent, ResourceLocation child) {
        CHILD.register(parent, child);
    }

    public static boolean defined(ResourceLocation id) {
        return DETONATIONS.containsKey(id);
    }

    public static Set<ResourceLocation> ids() {
        return DETONATIONS.ids();
    }

    /** Catalogue id not live => live (+ sub-munition child); live / other id => no-op. */
    public static synchronized void require(ResourceLocation id) {
        Detonation d = DETONATIONS.get(id);
        if (d == null || warheads.containsKey(id)) {
            return;
        }
        InterceptDetonation intercept = INTERCEPTS.get(id);
        Map<ResourceLocation, Detonation> w = new LinkedHashMap<>(warheads);
        w.put(id, d);
        warheads = Collections.unmodifiableMap(w);
        if (intercept != null) {
            Map<ResourceLocation, InterceptDetonation> i = new LinkedHashMap<>(intercepts);
            i.put(id, intercept);
            intercepts = Collections.unmodifiableMap(i);
        }
        Float blast = BLAST.get(id);
        if (blast != null) {
            Map<ResourceLocation, Float> b = new LinkedHashMap<>(blastSizes);
            b.put(id, blast);
            blastSizes = Collections.unmodifiableMap(b);
        }
        ResourceLocation child = CHILD.get(id);
        if (child != null) {
            require(child);
        }
    }

    public static boolean live(ResourceLocation id) {
        return warheads.containsKey(id);
    }

    public static Set<ResourceLocation> liveIds() {
        return warheads.keySet();
    }

    @Nullable
    public static Detonation detonation(ResourceLocation id) {
        return warheads.get(id);
    }

    @Nullable
    public static InterceptDetonation intercept(ResourceLocation id) {
        return intercepts.get(id);
    }

    public static float blastSize(ResourceLocation id) {
        return blastSizes.getOrDefault(id, 0f);
    }
}
