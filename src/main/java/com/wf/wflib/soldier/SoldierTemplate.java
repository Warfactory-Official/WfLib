package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wf.wflib.soldier.api.SoldierRole;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** A named, world-owned variant. Equipment keys refer to installed catalogue choices, never arbitrary client NBT. */
public record SoldierTemplate(String id, String name, String faction, SoldierRole role, String gun,
                              List<String> grenades, Map<String,String> armour, int spares, int range, float spread) {
    public SoldierTemplate {
        key(id); key(faction);
        if (name==null || name.isBlank() || name.length()>48 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Soldier name must be 1–48 printable characters");
        if (role==null || gun==null || gun.length()>256 || grenades.size()>8 || armour.size()>4
                || spares<0 || spares>12 || range<20 || range>160 || !Float.isFinite(spread) || spread<.25f || spread>6)
            throw new IllegalArgumentException("Invalid loadout or tactical limits");
        grenades=List.copyOf(grenades); armour=Map.copyOf(armour);
    }
    public static String key(String key) {
        if (key==null || !key.matches("[a-z0-9_][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("IDs use 1–32 lowercase letters, digits, _ or -");
        return key;
    }
    public JsonObject json() {
        var j=new JsonObject(); j.addProperty("id",id); j.addProperty("name",name); j.addProperty("faction",faction);
        j.addProperty("role",role.id); j.addProperty("gun",gun); j.addProperty("spares",spares); j.addProperty("range",range); j.addProperty("spread",spread);
        var g=new JsonArray(); grenades.forEach(g::add); j.add("grenades",g);
        var a=new JsonObject(); armour.forEach(a::addProperty); j.add("armour",a); return j;
    }
    public static SoldierTemplate read(JsonObject j) {
        var a=new LinkedHashMap<String,String>(); j.getAsJsonObject("armour").entrySet().forEach(e->a.put(e.getKey(),e.getValue().getAsString()));
        var g=j.getAsJsonArray("grenades").asList().stream().map(e->e.getAsString()).toList();
        return new SoldierTemplate(j.get("id").getAsString(),j.get("name").getAsString(),j.get("faction").getAsString(),
                SoldierRole.of(j.get("role").getAsString()),j.get("gun").getAsString(),g,a,j.get("spares").getAsInt(),j.get("range").getAsInt(),j.get("spread").getAsFloat());
    }
}
