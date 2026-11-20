package com.wf.wflib.contract.site;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Which blocks stand in for the base materials, from every datapack's {@code data/<ns>/wflib/site_blocks/*.json}: an object of
 * role to block id, with the roles {@code heavy} (reinforced concrete), {@code body} (building concrete), {@code glass} (ballistic
 * glass), {@code steel}, {@code hesco} and {@code bag} (sandbags). A mod that has such blocks ships one file and its blocks are
 * used; with none, {@link Palette} uses vanilla stand-ins. Files apply in id order, later over earlier.
 */
public final class SiteBlocks extends SimpleJsonResourceReloadListener {
    private static volatile Map<String, ResourceLocation> roles = Map.of();

    public SiteBlocks() {
        super(new Gson(), "wflib/site_blocks");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, ResourceLocation> now = new HashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(f -> {
            try {
                f.getValue().getAsJsonObject().entrySet().forEach(e -> now.put(e.getKey(), ResourceLocation.parse(e.getValue().getAsString())));
            } catch (RuntimeException e) {
                com.wf.wflib.contract.ContractSystem.LOGGER.error("Site blocks {} are not loaded: {}", f.getKey(), e.getMessage());
            }
        });
        roles = Map.copyOf(now);
        Palette.reset();
    }

    /** The block chosen for {@code role}, or {@code fallback} when nothing names one or the named block does not exist. */
    static Block get(String role, Block fallback) {
        ResourceLocation id = roles.get(role);
        return id == null ? fallback : BuiltInRegistries.BLOCK.getOptional(id).orElse(fallback);
    }
}
