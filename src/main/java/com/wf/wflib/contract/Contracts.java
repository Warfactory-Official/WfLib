package com.wf.wflib.contract;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The contract definitions, loaded from every datapack's {@code data/<ns>/wflib/contracts/*.json} and reloaded with them. */
public final class Contracts extends SimpleJsonResourceReloadListener {
    private static volatile Map<ResourceLocation, ContractDef> defs = Map.of();

    public Contracts() {
        super(new Gson(), "wflib/contracts");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ContractDef> loaded = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id, ContractDef.parse(id, (JsonObject) json));
            } catch (RuntimeException e) {
                com.wf.wflib.contract.ContractSystem.LOGGER.error("Contract {} is not loaded: {}", id, e.getMessage());
            }
        });
        defs = Collections.unmodifiableMap(loaded);
        com.wf.wflib.contract.ContractSystem.LOGGER.info("Loaded {} contract definitions", loaded.size());
    }

    /** Contracts made at runtime (the daily board), by id; replaced as a whole. */
    private static volatile Map<ResourceLocation, ContractDef> daily = Map.of();

    public static void setDaily(Collection<ContractDef> now) {
        Map<ResourceLocation, ContractDef> m = new LinkedHashMap<>();
        now.forEach(d -> m.put(d.id(), d));
        daily = Collections.unmodifiableMap(m);
    }

    public static Collection<ContractDef> all() {
        if (daily.isEmpty()) return defs.values();
        java.util.List<ContractDef> both = new java.util.ArrayList<>(defs.values());
        both.addAll(daily.values());
        return both;
    }

    public static Optional<ContractDef> get(ResourceLocation id) {
        ContractDef d = defs.get(id);
        return Optional.ofNullable(d != null ? d : daily.get(id));
    }
}
