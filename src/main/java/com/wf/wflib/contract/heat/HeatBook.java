package com.wf.wflib.contract.heat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Every operator's heat with each faction, and the hunts after them. Saved with the world. */
public final class HeatBook extends SavedData {
    /** A hunter team after one operator: {@code siteId} null while it is only scheduled. */
    public record Hunt(String faction, long due, @Nullable String siteId, long expires) {}

    private final Map<UUID, Map<String, HeatMeter>> meters = new HashMap<>();
    private final Map<UUID, Hunt> hunts = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public static HeatBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HeatBook::new, (tag, reg) -> load(tag)), "wflib_heat");
    }

    public HeatMeter meter(UUID player, String faction) {
        return meters.computeIfAbsent(player, p -> new TreeMap<>()).computeIfAbsent(faction, f -> new HeatMeter(0, 0));
    }

    public Map<String, HeatMeter> of(UUID player) {
        return meters.getOrDefault(player, Map.of());
    }

    public Map<UUID, Hunt> hunts() {
        return hunts;
    }

    public long cooldownUntil(UUID player) {
        return cooldown.getOrDefault(player, 0L);
    }

    public void setCooldown(UUID player, long until) {
        cooldown.put(player, until);
        setDirty();
    }

    public void put(UUID player, Hunt h) {
        hunts.put(player, h);
        setDirty();
    }

    public void endHunt(UUID player) {
        if (hunts.remove(player) != null) setDirty();
    }

    static HeatBook load(CompoundTag tag) {
        HeatBook b = new HeatBook();
        JsonObject j = JsonParser.parseString(tag.getString("heat")).getAsJsonObject();
        j.getAsJsonObject("meters").entrySet().forEach(p -> {
            Map<String, HeatMeter> m = new TreeMap<>();
            p.getValue().getAsJsonObject().entrySet().forEach(f -> {
                JsonObject o = f.getValue().getAsJsonObject();
                m.put(f.getKey(), new HeatMeter(o.get("v").getAsDouble(), o.get("t").getAsLong()));
            });
            b.meters.put(UUID.fromString(p.getKey()), m);
        });
        j.getAsJsonObject("hunts").entrySet().forEach(e -> {
            JsonObject o = e.getValue().getAsJsonObject();
            b.hunts.put(UUID.fromString(e.getKey()), new Hunt(o.get("faction").getAsString(), o.get("due").getAsLong(),
                    o.has("site") ? o.get("site").getAsString() : null, o.get("expires").getAsLong()));
        });
        j.getAsJsonObject("cooldown").entrySet().forEach(e -> b.cooldown.put(UUID.fromString(e.getKey()), e.getValue().getAsLong()));
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        JsonObject j = new JsonObject(), m = new JsonObject(), h = new JsonObject(), c = new JsonObject();
        meters.forEach((player, factions) -> {
            JsonObject f = new JsonObject();
            factions.forEach((id, meter) -> {
                JsonObject o = new JsonObject();
                o.addProperty("v", meter.raw());
                o.addProperty("t", meter.at());
                f.add(id, o);
            });
            m.add(player.toString(), f);
        });
        hunts.forEach((player, hunt) -> {
            JsonObject o = new JsonObject();
            o.addProperty("faction", hunt.faction());
            o.addProperty("due", hunt.due());
            if (hunt.siteId() != null) o.addProperty("site", hunt.siteId());
            o.addProperty("expires", hunt.expires());
            h.add(player.toString(), o);
        });
        cooldown.forEach((player, until) -> c.addProperty(player.toString(), until));
        j.add("meters", m);
        j.add("hunts", h);
        j.add("cooldown", c);
        tag.putString("heat", j.toString());
        return tag;
    }
}
