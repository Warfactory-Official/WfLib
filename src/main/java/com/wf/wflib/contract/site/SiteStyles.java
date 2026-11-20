package com.wf.wflib.contract.site;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks a base can have, from every datapack's {@code data/<ns>/wflib/site_styles/*.json}. A style is the materials one base is built of instead of the plain
 * ones ({@link SiteBlocks}): an object {@code {"weight": 3, "roles": {"heavy": "ns:block", ...}, "weather": {"ns:block": ["ns:cracked", ...]}, "weatherChance": 0.12}}.
 * The roles are those of {@link Palette}: {@code heavy body trim floor slab glass steel hesco bag ground light bars}; any left out keep the base's own. {@code weather}
 * names, per block, the worn variants a share of its placements are swapped for. A style naming a block that does not exist (its mod is not here) is not offered.
 * Each base picks one by its position, so a region's bases agree with a rerun and differ from each other; the plain look has the weight {@link #PLAIN_WEIGHT}.
 */
public final class SiteStyles extends SimpleJsonResourceReloadListener {
    /** How often the unstyled look comes up against a style of weight 1. */
    public static final int PLAIN_WEIGHT = 2;

    public record Style(String id, int weight, Map<String, Block> roles, Map<Block, List<Block>> weather, float weatherChance) {
    }

    private static volatile List<Style> styles = List.of();

    public SiteStyles() {
        super(new Gson(), "wflib/site_styles");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Style> now = new ArrayList<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(f -> {
            try {
                Style style = parse(f.getKey().toString(), f.getValue().getAsJsonObject());
                if (style != null) now.add(style);
            } catch (RuntimeException e) {
                com.wf.wflib.contract.ContractSystem.LOGGER.error("Site style {} is not loaded: {}", f.getKey(), e.getMessage());
            }
        });
        styles = List.copyOf(now);
        Palette.reset();
    }

    /** Null when the style names a block that is not registered (its mod is absent). */
    static Style parse(String id, JsonObject json) {
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        Map<String, Block> roles = new LinkedHashMap<>();
        for (var e : json.getAsJsonObject("roles").entrySet()) {
            Block block = block(e.getValue().getAsString());
            if (block == null) return null;
            roles.put(e.getKey(), block);
        }
        Map<Block, List<Block>> weather = new LinkedHashMap<>();
        if (json.has("weather")) {
            for (var e : json.getAsJsonObject("weather").entrySet()) {
                Block from = block(e.getKey());
                if (from == null) return null;
                List<Block> to = new ArrayList<>();
                for (JsonElement v : e.getValue().getAsJsonArray()) {
                    Block b = block(v.getAsString());
                    if (b == null) return null;
                    to.add(b);
                }
                weather.put(from, List.copyOf(to));
            }
        }
        float chance = json.has("weatherChance") ? json.get("weatherChance").getAsFloat() : 0.12f;
        return weight > 0 ? new Style(id, weight, Map.copyOf(roles), Map.copyOf(weather), chance) : null;
    }

    private static Block block(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
    }

    public static List<Style> available() {
        return styles;
    }

    /** The style a base at {@code hash} takes, or null for the plain look. */
    public static Style pick(long hash) {
        List<Style> all = styles;
        int total = PLAIN_WEIGHT;
        for (Style s : all) total += s.weight;
        int roll = (int) Math.floorMod(hash, (long) total);
        if (roll < PLAIN_WEIGHT) return null;
        roll -= PLAIN_WEIGHT;
        for (Style s : all) {
            if (roll < s.weight) return s;
            roll -= s.weight;
        }
        return null;
    }

    /** A style by id (its file's id, or just the file name), for the command that forces one; null for none or unknown. */
    public static Style byId(String id) {
        for (Style s : styles) if (s.id.equals(id) || s.id.endsWith(":" + id)) return s;
        return null;
    }
}
