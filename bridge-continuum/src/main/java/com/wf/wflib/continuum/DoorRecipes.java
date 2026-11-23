package com.wf.wflib.continuum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Door level 1 (docs, 3.2): Continuum's fifteen doors and WFLib's have the same names, and each has one assembly-machine recipe. Every Continuum recipe
 * whose output is one of those doors is copied into the generated pack at the same path (so it overrides the original) with the output pointed at the wflib
 * door of that name. New doors are WFLib's; placed ones and Continuum's items are untouched.
 */
final class DoorRecipes {
    private static final Set<String> DOORS = Set.of("transition_seal", "vault_door", "fire_door", "sliding_blast_door", "sliding_seal_door",
            "secure_access_door", "round_airlock_door", "qe_sliding_door", "qe_containment", "water_door", "silo_hatch", "silo_hatch_large",
            "large_vehicle_door", "cargo_door", "blast_door");

    private DoorRecipes() {
    }

    static boolean isContinuumDoor(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("hbm") && DOORS.contains(id.getPath());
    }

    /** Continuum's fifteen door items leave every creative tab; the WFLib doors are in WFLib's. Placed doors and the items themselves are untouched. */
    static void hideContinuumDoors(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        java.util.List<ItemStack> doors = new java.util.ArrayList<>();
        event.getParentEntries().forEach(stack -> { if (isContinuumDoor(stack)) doors.add(stack); });
        event.getSearchEntries().forEach(stack -> { if (isContinuumDoor(stack) && !doors.contains(stack)) doors.add(stack); });
        for (ItemStack stack : doors) event.remove(stack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    static void generate(Path pack) throws IOException {
        var info = ModList.get().getModFileById("hbm");
        if (info == null) return;
        Path recipes = info.getFile().findResource("data/hbm/recipe");
        if (!Files.isDirectory(recipes)) return;
        int changed = 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(recipes)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).toList();
        }
        for (Path file : files) {
            JsonObject recipe;
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(in);
                if (!parsed.isJsonObject()) continue;
                recipe = parsed.getAsJsonObject();
            } catch (RuntimeException e) {
                continue;
            }
            if (!recipe.has("output_items") || !recipe.get("output_items").isJsonArray()) continue;
            JsonArray out = recipe.getAsJsonArray("output_items");
            boolean hit = false;
            for (int i = 0; i < out.size(); i++) {
                JsonElement e = out.get(i);
                if (e.isJsonPrimitive() && e.getAsString().startsWith("hbm:") && DOORS.contains(e.getAsString().substring(4))) {
                    out.set(i, new com.google.gson.JsonPrimitive("wflib:" + e.getAsString().substring(4)));
                    hit = true;
                }
            }
            if (!hit) continue;
            String relative = recipes.relativize(file).toString().replace('\\', '/');
            GeneratedPack.write(pack.resolve("data/hbm/recipe/" + relative), GeneratedPack.GSON.toJson(recipe));
            changed++;
        }
        ContinuumBridge.LOG.info("Generated pack: {} Continuum door recipe(s) now produce WFLib doors", changed);
    }
}
