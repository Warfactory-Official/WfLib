package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wf.wflib.soldier.api.SoldierOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * The force a territory raises and keeps up by itself: {@code count} soldiers drawn in turn from the saved variants in
 * {@code units} (empty: every saved variant), who {@code mode} (patrol or guard) the territory, and a lost soldier is
 * replaced {@code respawnSeconds} after the last loss. Which side they fight for is their variant's faction.
 */
public record Garrison(int count, List<String> units, SoldierOrder.Kind mode, int respawnSeconds) {
    public static final int MAX_COUNT = 100_000;

    public Garrison {
        if (count < 0 || count > MAX_COUNT) throw new IllegalArgumentException("Garrison size is 0-" + MAX_COUNT);
        if (mode != SoldierOrder.Kind.PATROL && mode != SoldierOrder.Kind.GUARD) throw new IllegalArgumentException("A garrison patrols or guards");
        if (respawnSeconds < 0 || respawnSeconds > 86_400) throw new IllegalArgumentException("Reinforcement delay is 0-86400 seconds");
        units.forEach(SoldierTemplate::key);
        units = List.copyOf(units);
    }

    public Garrison withCount(int n) {
        return new Garrison(n, units, mode, respawnSeconds);
    }

    /** The variant the {@code index}-th soldier of the garrison is raised from, among those that exist. */
    public String unitFor(int index, List<String> existing) {
        List<String> pool = units.isEmpty() ? existing : units.stream().filter(existing::contains).toList();
        return pool.isEmpty() ? null : pool.get(Math.floorMod(index, pool.size()));
    }

    public JsonObject json() {
        JsonObject j = new JsonObject();
        j.addProperty("count", count);
        JsonArray u = new JsonArray();
        units.forEach(u::add);
        j.add("units", u);
        j.addProperty("mode", mode.id);
        j.addProperty("respawn", respawnSeconds);
        return j;
    }

    public static Garrison read(JsonObject j) {
        List<String> units = new ArrayList<>();
        j.getAsJsonArray("units").forEach(e -> units.add(e.getAsString()));
        return new Garrison(j.get("count").getAsInt(), units, SoldierOrder.Kind.of(j.get("mode").getAsString()), j.get("respawn").getAsInt());
    }
}
