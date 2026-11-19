package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gear a soldier is told to get: catalogue keys (see {@link SoldierCatalogue}), found in storage and not conjured. Anything
 * absent is left as it is. {@code grenades} is the whole list it should carry (repeats count); {@code armour} is by slot.
 */
public record Want(String gun, List<String> grenades, Map<String, String> armour) {
    public static final int MAX_GRENADES = 8;
    public static final List<String> SLOTS = List.of("head", "chest", "legs", "feet");
    public static final Want NONE = new Want("", List.of(), Map.of());      // after SLOTS: the constructor reads it

    public Want {
        gun = gun == null ? "" : gun;
        grenades = List.copyOf(grenades);
        armour = Map.copyOf(armour);
        if (gun.length() > 256 || grenades.size() > MAX_GRENADES || armour.size() > SLOTS.size())
            throw new IllegalArgumentException("Too much gear");
        for (String g : grenades) if (g.length() > 256) throw new IllegalArgumentException("Too much gear");
        for (var e : armour.entrySet()) if (!SLOTS.contains(e.getKey()) || e.getValue().length() > 256) throw new IllegalArgumentException("Choose head, chest, legs or feet");
    }

    public boolean isEmpty() {
        return gun.isEmpty() && grenades.isEmpty() && armour.isEmpty();
    }

    public JsonObject json() {
        var j = new JsonObject();
        j.addProperty("gun", gun);
        var g = new JsonArray();
        grenades.forEach(g::add);
        j.add("grenades", g);
        var a = new JsonObject();
        armour.forEach(a::addProperty);
        j.add("armour", a);
        return j;
    }

    public static Want read(JsonObject j) {
        var armour = new LinkedHashMap<String, String>();
        if (j.has("armour")) j.getAsJsonObject("armour").entrySet().forEach(e -> armour.put(e.getKey(), e.getValue().getAsString()));
        var grenades = j.has("grenades") ? j.getAsJsonArray("grenades").asList().stream().map(e -> e.getAsString()).toList() : List.<String>of();
        return new Want(j.has("gun") ? j.get("gun").getAsString() : "", grenades, armour);
    }
}
