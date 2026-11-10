package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The world's territories, saved with it (so on dedicated servers too). */
public final class TerritoryBook extends SavedData {
    public static final int MAX = 128;
    private final Map<String, Territory> regions = new LinkedHashMap<>();

    public static TerritoryBook get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TerritoryBook::new, (tag, reg) -> load(tag)), "wflib_soldier_territory");
    }

    public Map<String, Territory> all() {
        return Collections.unmodifiableMap(regions);
    }

    public Territory get(String id) {
        return regions.get(id);
    }

    public void put(Territory t) {
        if (!regions.containsKey(t.id()) && regions.size() >= MAX) throw new IllegalArgumentException("At most " + MAX + " territories per world");
        regions.put(t.id(), t);
        setDirty();
    }

    public void remove(String id) {
        if (regions.remove(id) == null) throw new IllegalArgumentException("Unknown territory: " + id);
        setDirty();
    }

    /** Territories of that dimension holding the point, smallest first (the most specific one). */
    public List<Territory> at(String dimension, Vec3 p) {
        List<Territory> out = new ArrayList<>();
        for (Territory t : regions.values()) if (t.dimension().equals(dimension) && t.contains(p)) out.add(t);
        out.sort(java.util.Comparator.comparingInt(Territory::radius));
        return out;
    }

    static TerritoryBook load(CompoundTag tag) {
        TerritoryBook book = new TerritoryBook();
        JsonArray list = JsonParser.parseString(tag.getString("territories")).getAsJsonArray();
        list.forEach(e -> book.regions.put(Territory.read(e.getAsJsonObject()).id(), Territory.read(e.getAsJsonObject())));
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        JsonArray list = new JsonArray();
        regions.values().forEach(t -> list.add(t.json()));
        tag.putString("territories", list.toString());
        return tag;
    }
}
