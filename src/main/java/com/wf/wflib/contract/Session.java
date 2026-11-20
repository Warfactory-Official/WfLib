package com.wf.wflib.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A contract being worked: who took it, who shares it, where it is, when it ends. Saved with the world, so a restart loses nothing. */
public record Session(UUID id, ResourceLocation def, UUID owner, Set<UUID> party, String siteId, String dimension, Vec3 centre,
                      long deadline, @Nullable BlockPos cache) {
    public Session {
        party = Set.copyOf(party);
    }

    public boolean involves(UUID player) {
        return owner.equals(player) || party.contains(player);
    }

    public JsonObject json() {
        JsonObject j = new JsonObject();
        j.addProperty("id", id.toString());
        j.addProperty("def", def.toString());
        j.addProperty("owner", owner.toString());
        JsonArray p = new JsonArray();
        party.forEach(u -> p.add(u.toString()));
        j.add("party", p);
        j.addProperty("site", siteId);
        j.addProperty("dimension", dimension);
        JsonArray c = new JsonArray();
        c.add(centre.x);
        c.add(centre.y);
        c.add(centre.z);
        j.add("centre", c);
        j.addProperty("deadline", deadline);
        if (cache != null) j.addProperty("cache", cache.asLong());
        return j;
    }

    public static Session read(JsonObject j) {
        Set<UUID> party = new LinkedHashSet<>();
        j.getAsJsonArray("party").forEach(e -> party.add(UUID.fromString(e.getAsString())));
        JsonArray c = j.getAsJsonArray("centre");
        return new Session(UUID.fromString(j.get("id").getAsString()), ResourceLocation.parse(j.get("def").getAsString()),
                UUID.fromString(j.get("owner").getAsString()), party, j.get("site").getAsString(), j.get("dimension").getAsString(),
                new Vec3(c.get(0).getAsDouble(), c.get(1).getAsDouble(), c.get(2).getAsDouble()), j.get("deadline").getAsLong(),
                j.has("cache") ? BlockPos.of(j.get("cache").getAsLong()) : null);
    }
}
