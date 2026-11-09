package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.Map;

/** Factions and variant definitions are saved with the world, including on dedicated servers. */
public final class SoldierRoster extends SavedData {
    public record Faction(String id, String name, boolean hostile) {
        public Faction {
            SoldierTemplate.key(id);
            if (name==null || name.isBlank() || name.length()>48 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid faction name");
        }
        public JsonObject json() { var j=new JsonObject(); j.addProperty("id",id); j.addProperty("name",name); j.addProperty("hostile",hostile); return j; }
    }
    private final Map<String,Faction> factions=new LinkedHashMap<>();
    private final Map<String,SoldierTemplate> units=new LinkedHashMap<>();
    private int revision;
    public SoldierRoster() { factions.put("friendly",new Faction("friendly","Friendly forces",false)); factions.put("enemy",new Faction("enemy","Enemy forces",true)); }
    public static SoldierRoster get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SoldierRoster::new, (tag,reg)->load(tag)),"wflib_soldier_roster");
    }
    public Map<String,Faction> factions() { return java.util.Collections.unmodifiableMap(factions); }
    public Map<String,SoldierTemplate> units() { return java.util.Collections.unmodifiableMap(units); }
    public int revision() { return revision; }
    private void changed() { revision++; setDirty(); }
    public void put(Faction f) {
        if (!factions.containsKey(f.id()) && factions.size()>=64) throw new IllegalArgumentException("At most 64 factions per world");
        factions.put(f.id(),f); changed();
    }
    public void put(SoldierTemplate u) {
        if (!factions.containsKey(u.faction())) throw new IllegalArgumentException("Choose an existing faction");
        if (!units.containsKey(u.id()) && units.size()>=512) throw new IllegalArgumentException("At most 512 variants per world");
        units.put(u.id(),u); changed();
    }
    public void removeUnit(String id) { if (units.remove(id)==null) throw new IllegalArgumentException("Unknown variant: "+id); changed(); }
    public void removeFaction(String id) {
        if (factions.remove(id)==null) throw new IllegalArgumentException("Unknown faction: "+id);
        units.values().removeIf(u->u.faction().equals(id)); changed();
    }
    public JsonObject json() {
        var j=new JsonObject(); j.addProperty("revision",revision); var f=new JsonArray(); factions.values().forEach(v->f.add(v.json())); j.add("factions",f);
        var u=new JsonArray(); units.values().forEach(v->u.add(v.json())); j.add("units",u); return j;
    }
    static SoldierRoster load(CompoundTag tag) {
        var j=JsonParser.parseString(tag.getString("roster")).getAsJsonObject(); var r=new SoldierRoster(); r.factions.clear();
        for (var f:j.getAsJsonArray("factions")) { var o=f.getAsJsonObject(); r.put(new Faction(o.get("id").getAsString(),o.get("name").getAsString(),o.get("hostile").getAsBoolean())); }
        for (var u:j.getAsJsonArray("units")) r.put(SoldierTemplate.read(u.getAsJsonObject()));
        r.revision=j.get("revision").getAsInt(); r.setDirty(false); return r;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) { tag.putString("roster",json().toString()); return tag; }
}
