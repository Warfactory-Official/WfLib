package com.wf.wflib.core.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.wf.wflib.core.WFLibMod;
import com.wf.wflib.ammo.api.Ammo;
import com.wf.wflib.armor.api.ArmorPreset;
import com.wf.wflib.armor.api.ArmorPresets;
import com.wf.wflib.ammo.api.Calibre;
import com.wf.wflib.barrel.api.Barrels;
import com.wf.wflib.countermeasure.api.Countermeasures;
import com.wf.wflib.missile.api.MissileItem;
import com.wf.wflib.mine.api.MinePresets;
import com.wf.wflib.nvg.api.NvgContent;
import com.wf.wflib.missile.api.MissilePreset;
import com.wf.wflib.missile.api.MissilePresets;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.KineticShellItem;
import com.wf.wflib.magazine.api.MagazineItems;
import com.wf.wflib.warhead.Catalogue;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * WFLib catalogue requests. Catalogue entry live (registry entry, item) iff requested by a dependent, implied by a
 * requested magazine, or (warhead) referenced by a live round / missile. Request: mod construction, any thread;
 * unknown id => {@link IllegalArgumentException}; after {@link #freeze} (first RegisterEvent) =>
 * {@link IllegalStateException}. Several requesters, one id => one shared {@code wflib:} entry.
 */
public final class WFLibContent {
    public enum Kind {
        /** + every catalogue variant of it. */
        CALIBRE,
        KINETIC,
        MISSILE,
        WARHEAD,
        /** + its rounds (can: calibre, pod: missiles). */
        MAGAZINE,
        /** {@code wflib:spare_barrel} only. */
        BARREL,
        /** Flare / smoke / APS cartridges + decoy launcher; carrier entities always registered. */
        COUNTERMEASURE,
        /** The five night-vision goggles. */
        NVG,
        /** Armour garments and inserts; dev-only {@code mw_*} when not production. */
        ARMOR,
        /** Mine items; presets themselves are always defined (carrier data). */
        MINE
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<Kind, Map<ResourceLocation, Set<String>>> REQUESTS = new EnumMap<>(Kind.class);
    private static volatile boolean frozen;

    static {
        for (Kind k : Kind.values()) {
            REQUESTS.put(k, new LinkedHashMap<>());
        }
    }

    private WFLibContent() {
    }

    public static void calibres(String source, String... paths) {
        request(Kind.CALIBRE, source, paths);
    }

    public static void kinetic(String source, String... paths) {
        request(Kind.KINETIC, source, paths);
    }

    public static void missiles(String source, String... paths) {
        request(Kind.MISSILE, source, paths);
    }

    public static void warheads(String source, String... paths) {
        request(Kind.WARHEAD, source, paths);
    }

    public static void magazines(String source, String... paths) {
        request(Kind.MAGAZINE, source, paths);
    }

    public static void spareBarrel(String source) {
        request(Kind.BARREL, source, Barrels.SPARE_ID);
    }

    public static void countermeasures(String source, String... paths) {
        request(Kind.COUNTERMEASURE, source, paths);
    }

    public static void nvg(String source, String... paths) {
        request(Kind.NVG, source, paths);
    }

    public static void armor(String source, String... paths) {
        request(Kind.ARMOR, source, paths);
    }

    /** {@code paths}: preset ids ({@code ap}, not {@code mine_ap}). */
    public static void mines(String source, String... paths) {
        request(Kind.MINE, source, paths);
    }

    /** {@code paths}: {@code wflib:} ids without namespace. */
    public static void request(Kind kind, String source, String... paths) {
        for (String p : paths) {
            request(kind, source, ResourceLocation.fromNamespaceAndPath(WFLibMod.MODID, p));
        }
    }

    public static synchronized void request(Kind kind, String source, ResourceLocation id) {
        if (!defined(kind, id)) {
            throw new IllegalArgumentException(source + ": " + kind + " " + id + " not in the WFLib catalogue");
        }
        if (frozen) {
            throw new IllegalStateException(source + ": " + kind + " " + id + " requested after registration");
        }
        REQUESTS.get(kind).computeIfAbsent(id, k -> new LinkedHashSet<>()).add(source);
    }

    /**
     * Item id of a catalogue cartridge / shell / missile / magazine => request; else false (not catalogue content,
     * caller's own resolution reports it).
     */
    public static boolean requestItem(String source, ResourceLocation item) {
        for (Calibre c : Ammo.catalogueCalibres()) {
            if (c.itemId().equals(item)) {
                request(Kind.CALIBRE, source, c.id());
                return true;
            }
        }
        for (KineticPreset p : KineticPresets.catalogue()) {
            if (itemId(p.id(), p.itemPath()).equals(item)) {
                request(Kind.KINETIC, source, p.id());
                return true;
            }
        }
        for (MissilePreset p : MissilePresets.catalogue()) {
            if (itemId(p.id(), p.itemPath()).equals(item)) {
                request(Kind.MISSILE, source, p.id());
                return true;
            }
        }
        if (MagazineItems.defined(item)) {
            request(Kind.MAGAZINE, source, item);
            return true;
        }
        if (Barrels.SPARE_ID.equals(item)) {
            spareBarrel(source);
            return true;
        }
        if (Countermeasures.defined(item)) {
            request(Kind.COUNTERMEASURE, source, item);
            return true;
        }
        if (NvgContent.defined(item)) {
            request(Kind.NVG, source, item);
            return true;
        }
        if (ArmorPresets.defined(item)) {
            request(Kind.ARMOR, source, item);
            return true;
        }
        if (item.getNamespace().equals(WFLibMod.MODID) && item.getPath().startsWith("mine_")) {
            ResourceLocation preset = ResourceLocation.fromNamespaceAndPath(WFLibMod.MODID, item.getPath().substring(5));
            if (MinePresets.defined(preset)) {
                request(Kind.MINE, source, preset);
                return true;
            }
        }
        return false;
    }

    /**
     * Every catalogue item WFLib's own {@code data/wflib/tags/item/<path>.json} lists ({@code #} entries recursed;
     * other mods' additions not seen). No such file => {@link IllegalArgumentException}.
     */
    public static void requestTag(String source, ResourceLocation tag) {
        if (!tag.getNamespace().equals(WFLibMod.MODID)) {
            throw new IllegalArgumentException(source + ": tag #" + tag + " not WFLib's");
        }
        List<String> path = new ArrayList<>(List.of("data", WFLibMod.MODID, "tags", "item"));
        String[] parts = (tag.getPath() + ".json").split("/");
        path.addAll(List.of(parts));
        Path file = ModList.get().getModFileById(WFLibMod.MODID).getFile().findResource(path.toArray(String[]::new));
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(source + ": no WFLib tag #" + tag);
        }
        JsonObject json;
        try {
            json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
        for (JsonElement e : json.getAsJsonArray("values")) {
            String id = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
            if (id.startsWith("#")) {
                ResourceLocation inner = ResourceLocation.parse(id.substring(1));
                if (inner.getNamespace().equals(WFLibMod.MODID)) {
                    requestTag(source, inner);
                }
            } else {
                requestItem(source, ResourceLocation.parse(id));
            }
        }
    }

    /** Whole catalogue: WFLib's own game tests / dev runs. */
    public static synchronized void everything(String source) {
        Ammo.catalogueCalibres().forEach(c -> request(Kind.CALIBRE, source, c.id()));
        KineticPresets.catalogue().forEach(p -> request(Kind.KINETIC, source, p.id()));
        MissilePresets.catalogue().forEach(p -> request(Kind.MISSILE, source, p.id()));
        Catalogue.ids().forEach(id -> request(Kind.WARHEAD, source, id));
        MagazineItems.catalogue().forEach(id -> request(Kind.MAGAZINE, source, id));
        spareBarrel(source);
        Countermeasures.catalogue().forEach(id -> request(Kind.COUNTERMEASURE, source, id));
        NvgContent.catalogue().forEach(id -> request(Kind.NVG, source, id));
        ArmorPresets.catalogue().forEach(p -> request(Kind.ARMOR, source, p.id()));
        MinePresets.catalogue().forEach(id -> request(Kind.MINE, source, id));
    }

    public static boolean defined(Kind kind, ResourceLocation id) {
        return switch (kind) {
            case CALIBRE -> Ammo.catalogueCalibre(id) != null;
            case KINETIC -> KineticPresets.catalogueEntry(id) != null;
            case MISSILE -> MissilePresets.catalogueEntry(id) != null;
            case WARHEAD -> Catalogue.defined(id);
            case MAGAZINE -> MagazineItems.defined(id);
            case BARREL -> Barrels.SPARE_ID.equals(id);
            case COUNTERMEASURE -> Countermeasures.defined(id);
            case NVG -> NvgContent.defined(id);
            case ARMOR -> ArmorPresets.defined(id);
            case MINE -> MinePresets.defined(id);
        };
    }

    public static ResourceLocation itemId(ResourceLocation preset, String itemPath) {
        return ResourceLocation.fromNamespaceAndPath(preset.getNamespace(), itemPath);
    }

    /** Requested id -> requesting sources, request order. */
    public static synchronized Map<ResourceLocation, Set<String>> requests(Kind kind) {
        Map<ResourceLocation, Set<String>> out = new LinkedHashMap<>();
        REQUESTS.get(kind).forEach((id, s) -> out.put(id, Set.copyOf(s)));
        return Collections.unmodifiableMap(out);
    }

    public static boolean frozen() {
        return frozen;
    }

    /**
     * Idempotent; any RegisterEvent listener may call it first. Magazines -> implied rounds; ammo; kinetic; missiles;
     * then every catalogue warhead a live preset (body or impact effect) references.
     */
    public static synchronized void freeze() {
        if (frozen) {
            return;
        }
        frozen = true;
        Map<ResourceLocation, Set<String>> mags = REQUESTS.get(Kind.MAGAZINE);
        for (ResourceLocation m : mags.keySet()) {
            MagazineItems.Def d = MagazineItems.def(m);
            Map<ResourceLocation, Set<String>> into = REQUESTS.get(d.calibre() != null ? Kind.CALIBRE : Kind.MISSILE);
            List<ResourceLocation> rounds = d.calibre() != null ? List.of(d.calibre()) : d.missiles();
            rounds.forEach(r -> into.computeIfAbsent(r, k -> new LinkedHashSet<>()).add("magazine " + m));
        }
        MagazineItems.freeze(mags.keySet());
        Countermeasures.freeze(REQUESTS.get(Kind.COUNTERMEASURE).keySet());
        NvgContent.freeze(REQUESTS.get(Kind.NVG).keySet());
        ArmorPresets.freeze(REQUESTS.get(Kind.ARMOR).keySet());
        MinePresets.freeze(REQUESTS.get(Kind.MINE).keySet());
        Barrels.freeze(!REQUESTS.get(Kind.BARREL).isEmpty());
        Ammo.freeze(REQUESTS.get(Kind.CALIBRE).keySet());
        KineticPresets.freeze(REQUESTS.get(Kind.KINETIC).keySet());
        MissilePresets.freeze(REQUESTS.get(Kind.MISSILE).keySet());
        REQUESTS.get(Kind.WARHEAD).keySet().forEach(Catalogue::require);
        KineticPresets.all().forEach(KineticPresets::requireWarheads);
        MissilePresets.all().forEach(p -> Catalogue.require(p.warheadId()));
    }

    /** FMLLoadComplete: every live round / missile item's warhead registered, else one ISE; logs the live set. */
    public static void loadComplete() {
        Ammo.checkWarheads(Warheads::exists);
        List<String> problems = new ArrayList<>();
        KineticPresets.all().forEach(p -> warhead(problems, "kinetic " + p.id(), p.warheadId()));
        MissilePresets.all().forEach(p -> warhead(problems, "missile " + p.id(), p.warheadId()));
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof MissileItem m) {
                warhead(problems, "item " + BuiltInRegistries.ITEM.getKey(item), m.preset().warheadId());
            } else if (item instanceof KineticShellItem k) {
                warhead(problems, "item " + BuiltInRegistries.ITEM.getKey(item), k.preset().warheadId());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("wflib content: " + String.join("; ", problems));
        }
        LOG.info("wflib content: calibres {} kinetic {} missiles {} warheads {} magazines {} spare barrel {} countermeasures {} nvg {} armor {} mines {}",
                ids(Ammo.calibres().stream().map(Calibre::id).toList()),
                ids(KineticPresets.withItems().stream().map(KineticPreset::id).toList()),
                ids(MissilePresets.withItems().stream().map(MissilePreset::id).toList()),
                ids(Warheads.ids()), ids(MagazineItems.live()), Barrels.live(),
                ids(Countermeasures.live()), ids(NvgContent.live()),
                ids(ArmorPresets.live().stream().map(ArmorPreset::id).toList()), ids(MinePresets.live()));
    }

    private static void warhead(List<String> problems, String what, ResourceLocation id) {
        if (!Warheads.exists(id)) {
            problems.add(what + ": warhead " + id + " not registered");
        }
    }

    private static String ids(Collection<ResourceLocation> ids) {
        Set<String> s = new TreeSet<>();
        ids.forEach(id -> s.add(id.toString()));
        return s.size() + " " + s;
    }
}
