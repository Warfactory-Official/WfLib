package com.wf.wflib.continuum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The data pack the bridge writes at launch from Continuum's own bundled data, so that it follows Continuum without a copy to maintain:
 * <ul>
 * <li>patches (Golem's {@code value_maps/patches}) that remove Continuum's damage resistance from every item and set, so only WFLib reduces a hit;</li>
 * <li>{@code wflib:armor_profile} elements seeded from those same numbers (converted by {@link ArmourConverter}), at priority -10, so a hand-tuned
 * element in any pack beats them.</li>
 * </ul>
 * Written to {@code <game dir>/wflib_continuum/pack} and rebuilt every launch.
 */
final class GeneratedPack {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PATCH = "data/" + ContinuumBridge.ID + "/value_maps/patches/hbm/item/";
    private static final String PROFILES = "data/" + ContinuumBridge.ID + "/wflib/armor_profile/";

    private GeneratedPack() {
    }

    static Path build() throws IOException {
        Path root = FMLPaths.GAMEDIR.get().resolve(ContinuumBridge.ID).resolve("pack");
        if (Files.exists(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
            }
        }
        Files.createDirectories(root);
        write(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"WFLib: Continuum bridge (generated at launch)\"}}");
        if (BridgeConfig.armour()) armour(root);
        if (BridgeConfig.doors()) DoorRecipes.generate(root);
        return root;
    }

    private static void armour(Path root) throws IOException {
        JsonObject sets = read("data/hbm/value_maps/item/armor_sets.json");
        JsonObject radiation = read("data/hbm/value_maps/item/radiation_resistance.json");

        write(root.resolve(PATCH + "damage_resistance.json"), "{\"replace\": true}");

        JsonObject derived = new JsonObject();
        JsonObject values = new JsonObject();
        if (sets != null && sets.has("values")) {
            for (Map.Entry<String, JsonElement> e : sets.getAsJsonObject("values").entrySet()) {
                JsonObject copy = e.getValue().getAsJsonObject().deepCopy();
                copy.remove("resistance");
                values.add(e.getKey(), copy);
            }
        }
        derived.add("values", values);
        write(root.resolve(PATCH + "armor_sets.json"), GSON.toJson(derived));

        Map<String, JsonObject> profiles = ArmourConverter.convert(sets, radiation);
        for (Map.Entry<String, JsonObject> e : profiles.entrySet()) {
            write(root.resolve(PROFILES + e.getKey() + ".json"), GSON.toJson(e.getValue()));
        }
        ContinuumBridge.LOG.info("Generated pack: Continuum armour stood down for {} set(s); {} WFLib armour profile(s) seeded", values.size(), profiles.size());
    }

    /** One of Continuum's bundled files, or null when this build of it lacks it. */
    static JsonObject read(String resource) throws IOException {
        var info = ModList.get().getModFileById("hbm");
        if (info == null) return null;
        Path path = info.getFile().findResource(resource);
        if (!Files.exists(path)) return null;
        try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(in);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }
    }

    static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** File-name-safe form of a target ({@code hbm:ajr_helmet} to {@code hbm_ajr_helmet}, {@code #c:x/y} to {@code tag_c_x_y}). */
    static String fileName(String target) {
        String s = target.startsWith("#") ? "tag_" + target.substring(1) : target;
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    static Map<String, JsonObject> newMap() {
        return new LinkedHashMap<>();
    }
}
