package com.wf.wflib.continuum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;

/**
 * Continuum's item hazards and protection data, as WFLib's (docs, 3.4), written into the generated pack from Continuum's own bundled data maps:
 * <ul>
 * <li>{@code wflib:hazard} elements from {@code hbm:hazards}: the same targets (ids and tags), the same levels, the same mutex bits; the type names match
 * one to one ({@code hbm:radiation} is {@code wflib:radiation}). {@code immune} becomes an element with no entries. Types WFLib has no spelling for
 * ({@code dangerous_drop}, {@code unstable}) are left to Continuum, and a hazard's modifiers (RBMK, RTG, depletion) are not carried: the level is the base level.</li>
 * <li>the WFLib item tags the protection checks read: {@code hazmat}, {@code hazmat_advanced}, {@code digamma_protection} (the pieces of every set whose checks
 * name them), {@code faraday}, and {@code hazard_protection/<class>} (head pieces by the classes they block).</li>
 * </ul>
 */
final class HazardBridge {
    private static final Set<String> TYPES = Set.of("radiation", "contaminating", "digamma", "hot", "blinding", "asbestos", "coal", "hydroactive", "explosive", "toxic", "cold");
    private static final String HAZARD = "data/" + ContinuumBridge.ID + "/wflib/hazard/";
    private static final String TAGS = "data/wflib/tags/item/";

    private HazardBridge() {
    }

    static void generate(Path root) throws IOException {
        int rows = 0, skippedTypes = 0, modifiers = 0;
        JsonObject hazards = GeneratedPack.read("data/hbm/value_maps/item/hazards.json");
        if (hazards != null && hazards.has("values")) {
            for (Map.Entry<String, JsonElement> e : hazards.getAsJsonObject("values").entrySet()) {
                JsonObject spec = e.getValue().getAsJsonObject();
                JsonArray entries = new JsonArray();
                if (!(spec.has("immune") && spec.get("immune").getAsBoolean())) {
                    if (spec.has("entries")) {
                        for (JsonElement el : spec.getAsJsonArray("entries")) {
                            JsonObject entry = el.getAsJsonObject();
                            String type = entry.get("type").getAsString();
                            String name = type.startsWith("hbm:") ? type.substring(4) : type;
                            if (!TYPES.contains(name)) {
                                skippedTypes++;
                                continue;
                            }
                            if (entry.has("modifiers") && entry.getAsJsonArray("modifiers").size() > 0) modifiers++;
                            JsonObject out = new JsonObject();
                            out.addProperty("type", "wflib:" + name);
                            out.addProperty("level", entry.has("level") ? entry.get("level").getAsDouble() : 1.0D);
                            entries.add(out);
                        }
                    }
                    if (entries.isEmpty()) continue;                       // nothing WFLib can state, and not an immunity either
                }
                JsonObject element = new JsonObject();
                element.addProperty("targets", e.getKey());
                element.add("entries", entries);
                if (spec.has("mutex")) element.addProperty("mutex", spec.get("mutex").getAsInt());
                GeneratedPack.write(root.resolve(HAZARD + GeneratedPack.fileName(e.getKey()) + ".json"), GeneratedPack.GSON.toJson(element));
                rows++;
            }
        }

        Map<String, Set<String>> tags = new LinkedHashMap<>();
        JsonObject sets = GeneratedPack.read("data/hbm/value_maps/item/armor_sets.json");
        if (sets != null && sets.has("values")) {
            for (Map.Entry<String, JsonElement> e : sets.getAsJsonObject("values").entrySet()) {
                JsonObject set = e.getValue().getAsJsonObject();
                if (!set.has("checks")) continue;
                Set<String> pieces = new LinkedHashSet<>();
                pieces.add(e.getKey());
                for (String slot : new String[]{"helmet", "legs", "boots"}) if (set.has(slot) && !set.get(slot).isJsonNull()) pieces.add(set.get(slot).getAsString());
                for (JsonElement check : set.getAsJsonArray("checks")) {
                    String tag = switch (check.getAsString()) {
                        case "hazmat" -> "hazmat";
                        case "hazmat2" -> "hazmat_advanced";
                        case "digamma", "digamma2" -> "digamma_protection";
                        default -> null;
                    };
                    if (tag != null) tags.computeIfAbsent(tag, k -> new LinkedHashSet<>()).addAll(pieces);
                }
            }
        }
        JsonObject faraday = GeneratedPack.read("data/hbm/value_maps/item/faraday_armor.json");
        if (faraday != null && faraday.has("values")) {
            for (Map.Entry<String, JsonElement> e : faraday.getAsJsonObject("values").entrySet()) {
                if (e.getValue().getAsBoolean()) tags.computeIfAbsent("faraday", k -> new LinkedHashSet<>()).add(e.getKey());
            }
        }
        JsonObject classes = GeneratedPack.read("data/hbm/value_maps/item/protection_classes.json");
        if (classes != null && classes.has("values")) {
            for (Map.Entry<String, JsonElement> e : classes.getAsJsonObject("values").entrySet()) {
                for (JsonElement c : e.getValue().getAsJsonArray()) {
                    tags.computeIfAbsent("hazard_protection/" + c.getAsString().toLowerCase(Locale.ROOT), k -> new LinkedHashSet<>()).add(e.getKey());
                }
            }
        }
        for (Map.Entry<String, Set<String>> t : tags.entrySet()) {
            JsonObject tag = new JsonObject();
            JsonArray values = new JsonArray();
            for (String id : t.getValue()) {
                JsonObject v = new JsonObject();
                v.addProperty("id", id.startsWith("#") ? id.substring(1) : id);
                v.addProperty("required", false);
                if (id.startsWith("#")) values.add("#" + id.substring(1)); else values.add(v);
            }
            tag.add("values", values);
            GeneratedPack.write(root.resolve(TAGS + t.getKey() + ".json"), GeneratedPack.GSON.toJson(tag));
        }
        ContinuumBridge.LOG.info("Generated pack: {} hazard row(s) ({} entries of types WFLib has no name for left to Continuum, {} carry modifiers that are not carried), {} protection tag(s)",
                rows, skippedTypes, modifiers, tags.size());
    }
}
