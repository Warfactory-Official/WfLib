package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The world's supply depots, saved with it. */
public final class DepotBook extends SavedData {
    public static final int MAX = 256;
    private final Map<String, Depot> depots = new LinkedHashMap<>();

    public static DepotBook get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DepotBook::new, (tag, reg) -> load(tag)), "wflib_soldier_depots");
    }

    public Map<String, Depot> all() {
        return Collections.unmodifiableMap(depots);
    }

    public void put(Depot d) {
        if (!depots.containsKey(d.id()) && depots.size() >= MAX) throw new IllegalArgumentException("At most " + MAX + " depots per world");
        depots.put(d.id(), d);
        setDirty();
    }

    public void remove(String id) {
        if (depots.remove(id) == null) throw new IllegalArgumentException("Unknown depot: " + id);
        setDirty();
    }

    /** The nearest depot a side may use (its own, or unowned) in that dimension within {@code range} blocks. */
    public Depot nearest(String team, String dimension, BlockPos from, double range, java.util.Set<String> except) {
        Depot best = null;
        double bestSq = range * range;
        for (Depot d : depots.values()) {
            if (!d.dimension().equals(dimension) || !(d.team().isEmpty() || d.team().equals(team)) || except.contains(d.id())) continue;
            double sq = d.pos().distSqr(from);
            if (sq <= bestSq) {
                best = d;
                bestSq = sq;
            }
        }
        return best;
    }

    static DepotBook load(CompoundTag tag) {
        DepotBook book = new DepotBook();
        JsonParser.parseString(tag.getString("depots")).getAsJsonArray().forEach(e -> {
            Depot d = Depot.read(e.getAsJsonObject());
            book.depots.put(d.id(), d);
        });
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        JsonArray list = new JsonArray();
        depots.values().forEach(d -> list.add(d.json()));
        tag.putString("depots", list.toString());
        return tag;
    }
}
