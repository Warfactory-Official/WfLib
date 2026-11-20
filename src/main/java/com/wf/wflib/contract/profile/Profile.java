package com.wf.wflib.contract.profile;

import com.google.gson.JsonObject;

import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One operator's record. Mutable; saved with {@link ProfileBook}. */
public final class Profile {
    public int rep;
    public int completed;
    public int failed;
    public long earnings;
    public final Map<String, Integer> kills = new TreeMap<>();
    /** Analysed intel not yet acted on: {@code [contract id, quality]} pairs, oldest first. */
    public final List<String[]> leads = new ArrayList<>();
    /** Contract id -> game tick before which it cannot be taken again (stops accept-and-abandon farming). */
    public final Map<String, Long> cooldowns = new TreeMap<>();

    public Rank rank() {
        return Rank.of(rep);
    }

    public int totalKills() {
        return kills.values().stream().mapToInt(Integer::intValue).sum();
    }

    public JsonObject json() {
        JsonObject j = new JsonObject();
        j.addProperty("rep", rep);
        j.addProperty("completed", completed);
        j.addProperty("failed", failed);
        j.addProperty("earnings", earnings);
        JsonObject k = new JsonObject();
        kills.forEach(k::addProperty);
        j.add("kills", k);
        JsonArray l = new JsonArray();
        leads.forEach(x -> l.add(x[0] + "|" + x[1]));
        j.add("leads", l);
        JsonObject c = new JsonObject();
        cooldowns.forEach(c::addProperty);
        j.add("cool", c);
        return j;
    }

    public static Profile read(JsonObject j) {
        Profile p = new Profile();
        p.rep = j.get("rep").getAsInt();
        p.completed = j.get("completed").getAsInt();
        p.failed = j.get("failed").getAsInt();
        p.earnings = j.get("earnings").getAsLong();
        j.getAsJsonObject("kills").entrySet().forEach(e -> p.kills.put(e.getKey(), e.getValue().getAsInt()));
        if (j.has("cool")) j.getAsJsonObject("cool").entrySet().forEach(e -> p.cooldowns.put(e.getKey(), e.getValue().getAsLong()));
        if (j.has("leads")) j.getAsJsonArray("leads").forEach(e -> p.leads.add(e.getAsString().split("\\|", 2)));
        return p;
    }
}
