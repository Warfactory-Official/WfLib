package com.wf.wflib.missile.api;

import com.wf.wflib.warhead.api.Warheads;
import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.missile.MissileCatalogue;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/**
 * Launch-ready {@link MissilePreset}s, keyed by {@link ResourceLocation}. Catalogue entries live once requested
 * ({@link com.wf.wflib.core.api.WFLibContent}). Own registration before freeze => live + item; after => live, no item,
 * catalogue warhead pulled; after load complete => throws. Copy-on-write: any thread; reads live only.
 */
public final class MissilePresets {

    private static final ResourceLocation DEFAULT_ID = rl("cruise");
    private static final WFRegistry<MissilePreset> OWN = WFRegistry.create("missile", "missile presets");
    private static volatile Map<ResourceLocation, MissilePreset> presets = Map.of();
    private static volatile List<MissilePreset> withItems = List.of();
    private static boolean frozen;

    private MissilePresets() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT_ID;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null ? parsed : DEFAULT_ID;
    }

    public static synchronized void register(MissilePreset preset) {
        if (MissileCatalogue.get(preset.id()) != null) {
            throw new IllegalStateException("missile preset " + preset.id() + ": WFLib catalogue entry, request it");
        }
        OWN.register(preset.id(), preset);
        put(preset);
        if (frozen) {
            Warheads.require(preset.warheadId());
        }
    }

    private static void put(MissilePreset preset) {
        Map<ResourceLocation, MissilePreset> next = new LinkedHashMap<>(presets);
        next.put(preset.id(), preset);
        presets = Collections.unmodifiableMap(next);
    }

    /** {@code requested} catalogue entries + own pre-freeze registrations => live with item. Once. */
    public static synchronized void freeze(Set<ResourceLocation> requested) {
        if (frozen) {
            throw new IllegalStateException("missile presets frozen twice");
        }
        List<MissilePreset> items = new ArrayList<>();
        MissileCatalogue.all().stream().filter(p -> requested.contains(p.id())).forEach(p -> {
            put(p);
            items.add(p);
        });
        items.addAll(OWN.values());
        withItems = List.copyOf(items);
        frozen = true;
    }

    public static MissilePreset get(ResourceLocation id) {
        return presets.get(id);
    }

    public static boolean exists(ResourceLocation id) {
        return presets.containsKey(id);
    }

    public static Collection<MissilePreset> all() {
        return presets.values();
    }

    public static Set<ResourceLocation> ids() {
        return presets.keySet();
    }

    /** Live presets with a {@link MissileItem}, registration order. */
    public static List<MissilePreset> withItems() {
        return withItems;
    }

    public static Collection<MissilePreset> catalogue() {
        return MissileCatalogue.all();
    }

    /** Live or not. */
    @Nullable
    public static MissilePreset catalogueEntry(ResourceLocation id) {
        return MissileCatalogue.get(id);
    }

    public static ResourceLocation defaultId() {
        return DEFAULT_ID;
    }
}
