package com.wf.wflib.round.api;

import com.wf.wflib.warhead.api.Warheads;
import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.round.KineticCatalogue;
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
 * Loadable {@link KineticPreset}s, keyed by {@link ResourceLocation}. Catalogue entries live once requested
 * ({@link com.wf.wflib.core.api.WFLibContent}). Own registration before freeze => live + item; after => live, no item,
 * catalogue warhead pulled; after load complete => throws. Copy-on-write: any thread; reads live only.
 */
public final class KineticPresets {

    private static final ResourceLocation DEFAULT_ID = rl("he");
    private static final WFRegistry<KineticPreset> OWN = WFRegistry.create("round", "kinetic presets");
    private static final WFRegistry<KineticPreset> ITEMLESS = WFRegistry.create("round", "kinetic presets without item");
    private static volatile Map<ResourceLocation, KineticPreset> presets = Map.of();
    private static volatile List<KineticPreset> withItems = List.of();
    private static boolean frozen;

    private KineticPresets() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static synchronized void register(KineticPreset preset) {
        add(OWN, preset);
    }

    /** Body + impact-effect warheads => {@link Warheads#require}. */
    public static void requireWarheads(KineticPreset preset) {
        Warheads.require(preset.warheadId());
        preset.effects().forEach(b -> b.effect().warheads().forEach(Warheads::require));
    }

    /** Live, never a shell item (ammo variants, test fixtures); catalogue warhead pulled once frozen. */
    public static synchronized void registerItemless(KineticPreset preset) {
        add(ITEMLESS, preset);
    }

    private static void add(WFRegistry<KineticPreset> into, KineticPreset preset) {
        if (KineticCatalogue.get(preset.id()) != null) {
            throw new IllegalStateException("kinetic preset " + preset.id() + ": WFLib catalogue entry, request it");
        }
        if (presets.containsKey(preset.id())) {
            throw new IllegalStateException("kinetic preset " + preset.id() + " already registered");
        }
        into.register(preset.id(), preset);
        put(preset);
        if (frozen) {
            requireWarheads(preset);
        }
    }

    private static void put(KineticPreset preset) {
        Map<ResourceLocation, KineticPreset> next = new LinkedHashMap<>(presets);
        next.put(preset.id(), preset);
        presets = Collections.unmodifiableMap(next);
    }

    /** {@code requested} catalogue entries + own pre-freeze registrations => live with item. Once. */
    public static synchronized void freeze(Set<ResourceLocation> requested) {
        if (frozen) {
            throw new IllegalStateException("kinetic presets frozen twice");
        }
        List<KineticPreset> items = new ArrayList<>();
        KineticCatalogue.all().stream().filter(p -> requested.contains(p.id())).forEach(p -> {
            put(p);
            items.add(p);
        });
        items.addAll(OWN.values());
        withItems = List.copyOf(items);
        frozen = true;
    }

    public static KineticPreset get(ResourceLocation id) {
        return presets.get(id);
    }

    public static boolean exists(ResourceLocation id) {
        return presets.containsKey(id);
    }

    public static Collection<KineticPreset> all() {
        return presets.values();
    }

    public static Set<ResourceLocation> ids() {
        return presets.keySet();
    }

    /** Live presets with a {@link KineticShellItem}, registration order. */
    public static List<KineticPreset> withItems() {
        return withItems;
    }

    public static Collection<KineticPreset> catalogue() {
        return KineticCatalogue.all();
    }

    @Nullable
    public static KineticPreset catalogueEntry(ResourceLocation id) {
        return KineticCatalogue.get(id);
    }

    public static ResourceLocation defaultId() {
        return DEFAULT_ID;
    }

    /** {@code wflib:he}; null unless requested. */
    public static KineticPreset fallback() {
        return presets.get(DEFAULT_ID);
    }
}
