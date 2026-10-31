package com.wf.wflib.ammo.api;

import com.wf.wflib.ammo.AmmoCatalogue;
import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Calibre + variant registry. Catalogue ({@link AmmoCatalogue}) entries live only once requested
 * ({@link com.wf.wflib.core.api.WFLibContent}); requested calibre => all its catalogue variants. Own registration: mod
 * construction, any thread, until freeze; id registered twice / catalogue id => {@link IllegalStateException} naming
 * both sources. Frozen: one {@link CartridgeItem} per live calibre, one {@link KineticPreset} per live variant under
 * the variant id. Reads copy-on-write, any thread; live entries only.
 */
public final class Ammo {
    private static final WFRegistry<Calibre> OWN_CALIBRES = WFRegistry.create("ammo", "ammo calibres");
    private static final WFRegistry<AmmoVariant> OWN_VARIANTS = WFRegistry.create("ammo", "ammo variants");
    private static volatile Map<ResourceLocation, Calibre> calibres = Map.of();
    private static volatile Map<ResourceLocation, AmmoVariant> variants = Map.of();
    private static final Map<ResourceLocation, String> SOURCES = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, ProjectileLook> looks = Map.of();
    private static volatile Map<ResourceLocation, List<AmmoVariant>> byCalibre = Map.of();
    private static volatile boolean frozen;

    private Ammo() {
    }

    public static void register(Calibre c, String source) {
        synchronized (Ammo.class) {
            claim(c.id(), "calibre", source);
            OWN_CALIBRES.register(c.id(), c);
            calibres = ownMap(OWN_CALIBRES);
        }
    }

    public static void register(AmmoVariant v, String source) {
        synchronized (Ammo.class) {
            claim(v.id(), "variant", source);
            OWN_VARIANTS.register(v.id(), v);
            variants = ownMap(OWN_VARIANTS);
        }
    }

    private static <V> Map<ResourceLocation, V> ownMap(WFRegistry<V> r) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(
                r.entries().stream().collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()),
                        Map::putAll)));
    }

    public static Collection<Calibre> catalogueCalibres() {
        return Collections.unmodifiableCollection(AmmoCatalogue.calibres());
    }

    public static Collection<AmmoVariant> catalogueVariants() {
        return Collections.unmodifiableCollection(AmmoCatalogue.variants());
    }

    @Nullable
    public static Calibre catalogueCalibre(ResourceLocation id) {
        return AmmoCatalogue.calibre(id);
    }

    @Nullable
    public static AmmoVariant catalogueVariant(ResourceLocation id) {
        return AmmoCatalogue.variant(id);
    }

    /** Calibre + variant ids share one namespace (a variant id must not name a calibre). */
    private static void claim(ResourceLocation id, String kind, String source) {
        String prev = AmmoCatalogue.calibre(id) != null || AmmoCatalogue.variant(id) != null
                ? AmmoCatalogue.SOURCE : SOURCES.get(id);
        if (prev != null) {
            throw new IllegalStateException(kind + " " + id + ": registered by " + source + " and by " + prev);
        }
        if (frozen) {
            throw new IllegalStateException(kind + " " + id + " from " + source + ": registry frozen at item"
                    + " registration");
        }
        SOURCES.put(id, source);
    }

    @Nullable
    public static Calibre calibre(ResourceLocation id) {
        return calibres.get(id);
    }

    @Nullable
    public static AmmoVariant variant(ResourceLocation id) {
        return variants.get(id);
    }

    public static Collection<Calibre> calibres() {
        return calibres.values();
    }

    public static Collection<AmmoVariant> variants() {
        return variants.values();
    }

    @Nullable
    public static synchronized String source(ResourceLocation id) {
        return SOURCES.get(id);
    }

    public static boolean frozen() {
        return frozen;
    }

    /** Registration order; [0] = calibre default. Frozen only. */
    public static List<AmmoVariant> variantsOf(ResourceLocation calibre) {
        requireFrozen();
        return byCalibre.getOrDefault(calibre, List.of());
    }

    public static AmmoVariant defaultVariant(ResourceLocation calibre) {
        List<AmmoVariant> vs = variantsOf(calibre);
        if (vs.isEmpty()) {
            throw new IllegalArgumentException("calibre " + calibre + " has no variant");
        }
        return vs.getFirst();
    }

    /** Frozen only. */
    public static ProjectileLook look(ResourceLocation variant) {
        requireFrozen();
        ProjectileLook l = looks.get(variant);
        if (l == null) {
            throw new IllegalArgumentException("variant " + variant + " unknown");
        }
        return l;
    }

    /** Null for presets that are not variants. */
    @Nullable
    public static ProjectileLook lookOrNull(ResourceLocation preset) {
        return looks.get(preset);
    }

    private static void requireFrozen() {
        if (!frozen) {
            throw new IllegalStateException("ammo registry read before item registration");
        }
    }

    /**
     * {@code requested} catalogue calibres + own registrations go live; all problems collected into one
     * {@link IllegalStateException} (not fail-fast). Called once by {@link com.wf.wflib.core.api.WFLibContent#freeze}.
     */
    public static synchronized void freeze(Set<ResourceLocation> requested) {
        if (frozen) {
            throw new IllegalStateException("ammo frozen twice");
        }
        Map<ResourceLocation, Calibre> liveCalibres = new LinkedHashMap<>();
        Map<ResourceLocation, AmmoVariant> liveVariants = new LinkedHashMap<>();
        for (Calibre c : AmmoCatalogue.calibres()) {
            if (requested.contains(c.id())) {
                liveCalibres.put(c.id(), c);
                SOURCES.put(c.id(), AmmoCatalogue.SOURCE);
            }
        }
        for (AmmoVariant v : AmmoCatalogue.variants()) {
            if (requested.contains(v.calibre())) {
                liveVariants.put(v.id(), v);
                SOURCES.put(v.id(), AmmoCatalogue.SOURCE);
            }
        }
        liveCalibres.putAll(calibres);
        liveVariants.putAll(variants);
        calibres = Collections.unmodifiableMap(liveCalibres);
        variants = Collections.unmodifiableMap(liveVariants);
        List<String> problems = new ArrayList<>();
        Map<ResourceLocation, List<AmmoVariant>> by = new LinkedHashMap<>();
        Map<ResourceLocation, ProjectileLook> lk = new LinkedHashMap<>();
        calibres.keySet().forEach(c -> by.put(c, new ArrayList<>()));
        List<KineticPreset> presets = new ArrayList<>();
        for (AmmoVariant v : variants.values()) {
            Calibre c = calibres.get(v.calibre());
            if (c == null) {
                problems.add("variant " + v.id() + " (" + SOURCES.get(v.id()) + "): calibre " + v.calibre()
                        + " not registered");
                continue;
            }
            by.get(c.id()).add(v);
            try {
                lk.put(v.id(), ProjectileLook.resolve(c, v));
                presets.add(AmmoPresets.preset(c, v));
            } catch (IllegalArgumentException e) {
                problems.add("variant " + v.id() + " (" + SOURCES.get(v.id()) + "): " + e.getMessage());
            }
        }
        by.forEach((c, vs) -> {
            if (vs.isEmpty()) {
                problems.add("calibre " + c + " (" + SOURCES.get(c) + "): no variant");
            }
        });
        if (!problems.isEmpty()) {
            throw new IllegalStateException("ammo: " + String.join("; ", problems));
        }
        Map<ResourceLocation, List<AmmoVariant>> frozenBy = new LinkedHashMap<>();
        by.forEach((c, vs) -> frozenBy.put(c, List.copyOf(vs)));
        byCalibre = Collections.unmodifiableMap(frozenBy);
        looks = Collections.unmodifiableMap(lk);
        presets.forEach(KineticPresets::registerItemless);
        frozen = true;
    }

    /** Explosive warheads absent from {@code exists} => one {@link IllegalStateException} naming each source. */
    public static void checkWarheads(Predicate<ResourceLocation> exists) {
        List<String> problems = new ArrayList<>();
        for (AmmoVariant v : variants()) {
            if (v.explosive() != null && !exists.test(v.explosive().warhead())) {
                problems.add("variant " + v.id() + " (" + source(v.id()) + "): warhead " + v.explosive().warhead()
                        + " unknown");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("ammo: " + String.join("; ", problems));
        }
    }
}
