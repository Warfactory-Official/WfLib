package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Each player's named loadouts. Unlike the workshop's variants they belong to a player, not the world, and need no operator. */
public final class GearBook extends SavedData {
    public static final int MAX_PER_PLAYER = 32, MAX_SPARES = 12;

    /** {@code role} "" and {@code spares} -1 leave those as they are. */
    public record Preset(String name, Want want, String role, int spares) {
        public Preset {
            name = name == null ? "" : name.strip();
            if (name.isEmpty() || name.length() > 32 || name.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("A preset name is 1-32 printable characters");
            if (role == null || role.length() > 16 || spares < -1 || spares > MAX_SPARES) throw new IllegalArgumentException("Invalid preset");
        }

        public JsonObject json() {
            var j = new JsonObject();
            j.addProperty("name", name);
            j.add("want", want.json());
            j.addProperty("role", role);
            j.addProperty("spares", spares);
            return j;
        }

        public static Preset read(JsonObject j) {
            return new Preset(j.get("name").getAsString(), Want.read(j.getAsJsonObject("want")),
                    j.has("role") ? j.get("role").getAsString() : "", j.has("spares") ? j.get("spares").getAsInt() : -1);
        }
    }

    private final Map<UUID, Map<String, Preset>> presets = new LinkedHashMap<>();

    public static GearBook get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(GearBook::new, (tag, reg) -> load(tag)), "wflib_soldier_gear");
    }

    public Map<String, Preset> of(UUID owner) {
        return Collections.unmodifiableMap(presets.getOrDefault(owner, Map.of()));
    }

    public void put(UUID owner, Preset p) {
        var mine = presets.computeIfAbsent(owner, k -> new LinkedHashMap<>());
        if (!mine.containsKey(p.name()) && mine.size() >= MAX_PER_PLAYER) throw new IllegalArgumentException("At most " + MAX_PER_PLAYER + " presets");
        mine.put(p.name(), p);
        setDirty();
    }

    public boolean remove(UUID owner, String name) {
        var mine = presets.get(owner);
        boolean gone = mine != null && mine.remove(name) != null;
        if (gone) setDirty();
        return gone;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var all = new JsonArray();
        presets.forEach((owner, mine) -> mine.values().forEach(p -> {
            var j = p.json();
            j.addProperty("owner", owner.toString());
            all.add(j);
        }));
        tag.putString("presets", all.toString());
        return tag;
    }

    static GearBook load(CompoundTag tag) {
        var book = new GearBook();
        for (var e : JsonParser.parseString(tag.getString("presets")).getAsJsonArray()) {
            var j = e.getAsJsonObject();
            try {
                book.presets.computeIfAbsent(UUID.fromString(j.get("owner").getAsString()), k -> new LinkedHashMap<>()).put(j.get("name").getAsString(), Preset.read(j));
            } catch (RuntimeException ignored) {
                // a preset that no longer reads is dropped, not the whole book
            }
        }
        return book;
    }
}
