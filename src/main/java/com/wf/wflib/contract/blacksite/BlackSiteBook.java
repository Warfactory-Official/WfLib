package com.wf.wflib.contract.blacksite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The Black Site's state, saved with the world: where it is, who holds it, when things happen next. */
public final class BlackSiteBook extends SavedData {
    /** A retaliation wave's site, removed at {@code expires}. */
    public record Wave(String siteId, long expires) {}

    public boolean active;
    @Nullable
    public BlockPos pos;
    public long start, end, dayEnd, nextWave, nextStart;
    public int days;
    public boolean autoStart = true;
    public final Capture capture = new Capture();
    public final List<Wave> waves = new ArrayList<>();
    @Nullable
    public String guardSite;

    public static BlackSiteBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BlackSiteBook::new, (tag, reg) -> load(tag)), "wflib_blacksite");
    }

    static BlackSiteBook load(CompoundTag tag) {
        BlackSiteBook b = new BlackSiteBook();
        JsonObject j = JsonParser.parseString(tag.getString("bs")).getAsJsonObject();
        b.active = j.get("active").getAsBoolean();
        b.pos = j.has("pos") ? BlockPos.of(j.get("pos").getAsLong()) : null;
        b.start = j.get("start").getAsLong();
        b.end = j.get("end").getAsLong();
        b.dayEnd = j.get("dayEnd").getAsLong();
        b.nextWave = j.get("nextWave").getAsLong();
        b.nextStart = j.get("nextStart").getAsLong();
        b.days = j.get("days").getAsInt();
        b.autoStart = j.get("auto").getAsBoolean();
        b.guardSite = j.has("guard") ? j.get("guard").getAsString() : null;
        Map<UUID, Integer> presence = new HashMap<>();
        j.getAsJsonObject("presence").entrySet().forEach(e -> presence.put(UUID.fromString(e.getKey()), e.getValue().getAsInt()));
        b.capture.restore(j.has("holder") ? UUID.fromString(j.get("holder").getAsString()) : null, j.get("progress").getAsInt(), presence);
        j.getAsJsonArray("waves").forEach(e -> b.waves.add(new Wave(e.getAsJsonObject().get("site").getAsString(), e.getAsJsonObject().get("expires").getAsLong())));
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        JsonObject j = new JsonObject();
        j.addProperty("active", active);
        if (pos != null) j.addProperty("pos", pos.asLong());
        j.addProperty("start", start);
        j.addProperty("end", end);
        j.addProperty("dayEnd", dayEnd);
        j.addProperty("nextWave", nextWave);
        j.addProperty("nextStart", nextStart);
        j.addProperty("days", days);
        j.addProperty("auto", autoStart);
        if (guardSite != null) j.addProperty("guard", guardSite);
        if (capture.holder() != null) j.addProperty("holder", capture.holder().toString());
        j.addProperty("progress", capture.progress());
        JsonObject presence = new JsonObject();
        capture.presence().forEach((id, n) -> presence.addProperty(id.toString(), n));
        j.add("presence", presence);
        JsonArray w = new JsonArray();
        waves.forEach(x -> {
            JsonObject o = new JsonObject();
            o.addProperty("site", x.siteId());
            o.addProperty("expires", x.expires());
            w.add(o);
        });
        j.add("waves", w);
        tag.putString("bs", j.toString());
        return tag;
    }
}
