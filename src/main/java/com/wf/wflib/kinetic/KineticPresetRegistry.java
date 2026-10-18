package com.wf.wflib.kinetic;

import com.wf.wflib.MissileModels;
import com.wf.wflib.WFLib;
import com.wf.wflib.round.pen.PenMaterial;
import com.wf.wflib.warhead.FireWarhead;
import com.wf.wflib.warhead.GasWarhead;
import com.wf.wflib.warhead.RecursiveFrag;
import com.wf.wflib.warhead.WarheadRegistry;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loadable {@link KineticPreset}s, keyed by {@link ResourceLocation}. Catalogue entries live once requested
 * ({@link com.wf.wflib.content.WFLibContent}). Own registration before freeze => live + item; after => live, no item,
 * catalogue warhead pulled. Copy-on-write: any thread; reads live only.
 */
public final class KineticPresetRegistry {

    private static final ResourceLocation DEFAULT_ID = rl("he");
    /** Pre-1.6.4 blast-resistance 100/60 outcome: every class below ARMOR drilled. */
    private static final double DRILLS_METAL = PenMaterial.METAL.resistance;
    private static final Map<ResourceLocation, KineticPreset> CATALOGUE = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, KineticPreset> presets = Map.of();
    private static final List<KineticPreset> OWN = new ArrayList<>();
    private static volatile List<KineticPreset> withItems = List.of();
    private static boolean frozen;

    static {
        registerDefaults();
    }

    private KineticPresetRegistry() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static synchronized void register(KineticPreset preset) {
        if (CATALOGUE.containsKey(preset.id())) {
            throw new IllegalStateException("kinetic preset " + preset.id() + ": WFLib catalogue entry, request it");
        }
        put(preset);
        if (frozen) {
            requireWarheads(preset);
        } else {
            OWN.add(preset);
        }
    }

    /** Body + impact-effect warheads => {@link WarheadRegistry#require}. */
    public static void requireWarheads(KineticPreset preset) {
        WarheadRegistry.require(preset.warheadId());
        preset.effects().forEach(b -> b.effect().warheads().forEach(WarheadRegistry::require));
    }

    /** Live, never a shell item (ammo variants, test fixtures); catalogue warhead pulled once frozen. */
    public static synchronized void registerItemless(KineticPreset preset) {
        if (CATALOGUE.containsKey(preset.id())) {
            throw new IllegalStateException("kinetic preset " + preset.id() + ": WFLib catalogue entry, request it");
        }
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
        CATALOGUE.values().stream().filter(p -> requested.contains(p.id())).forEach(p -> {
            put(p);
            items.add(p);
        });
        items.addAll(OWN);
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
        return Collections.unmodifiableCollection(CATALOGUE.values());
    }

    @Nullable
    public static KineticPreset catalogueEntry(ResourceLocation id) {
        return CATALOGUE.get(id);
    }

    public static ResourceLocation defaultId() {
        return DEFAULT_ID;
    }

    /** {@code wflib:he}; null unless requested. */
    public static KineticPreset fallback() {
        return presets.get(DEFAULT_ID);
    }

    private static void define(KineticPreset preset) {
        CATALOGUE.put(preset.id(), preset);
    }

    private static void registerDefaults() {
        // A field gun's shell: the reference round every other one is a variation on.
        define(KineticPreset.builder(DEFAULT_ID, MissileModels.rl("micro"), WarheadRegistry.rl("standard"))
                .speed(6.0).mass(20.0).build());

        define(KineticPreset.builder(rl("ap"), MissileModels.rl("micro"), WarheadRegistry.rl("inert"))
                .speed(12.0).drag(0.005).mass(30.0).penetration(8, DRILLS_METAL).armorPenetration(550.0)
                .passesThroughEntities().build());

        // Shaped charge: a narrow jet along the impact heading, so it drills rather than craters.
        define(KineticPreset.builder(rl("heat"), MissileModels.rl("micro"), WarheadRegistry.rl("shaped_charge"))
                .speed(8.0).mass(22.0).penetration(2, DRILLS_METAL).blastHalfAngle(15.0).armorPenetration(450.0).build());

        // Airburst fragmentation: bursts short of the ground so the cone covers infantry in the open.
        define(KineticPreset.builder(rl("frag"), MissileModels.rl("micro"), WarheadRegistry.rl("fragmentation"))
                .speed(6.0).mass(18.0).fragments(24).airburst(8.0).build());

        define(KineticPreset.builder(rl("incendiary"), MissileModels.rl("micro"), FireWarhead.ID)
                .speed(6.0).mass(18.0).build());

        define(KineticPreset.builder(rl("chemical"), MissileModels.rl("micro_taint"), GasWarhead.ID)
                .speed(5.0).mass(18.0).airburst(6.0).build());

        define(KineticPreset.builder(rl("cluster"), MissileModels.rl("cluster_part"), RecursiveFrag.ID)
                .speed(6.0).mass(22.0).fragments(6).airburst(24.0).build());

        define(KineticPreset.builder(rl("emp"), MissileModels.rl("micro_emp"), WarheadRegistry.rl("emp"))
                .speed(6.0).mass(16.0).airburst(12.0).build());

        // Rocket assisted: less drag and more speed buy range, which is the whole point of one.
        define(KineticPreset.builder(rl("rap"), MissileModels.rl("micro"), WarheadRegistry.rl("standard"))
                .speed(9.0).drag(0.004).mass(24.0).life(4000).build());

        define(KineticPreset.builder(rl("nuclear"), MissileModels.rl("micro_schrab"), WarheadRegistry.rl("mininuke"))
                .speed(4.0).mass(60.0).build());

        define(KineticPreset.builder(rl("practice"), MissileModels.rl("micro"), WarheadRegistry.rl("inert"))
                .speed(6.0).mass(20.0).build());
    }
}
