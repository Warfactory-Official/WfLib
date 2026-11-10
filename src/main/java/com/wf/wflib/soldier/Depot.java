package com.wf.wflib.soldier;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

/**
 * A container a force resupplies from. {@code infinite} depots refill any kit from nothing (an armoury crate);
 * others hand over what is actually inside.
 */
public record Depot(String id, String team, String dimension, BlockPos pos, boolean infinite) {
    public Depot {
        SoldierTemplate.key(id);
        if (team == null || team.length() > 64) throw new IllegalArgumentException("Invalid team");
        if (dimension == null || dimension.isBlank() || dimension.length() > 128) throw new IllegalArgumentException("Invalid dimension");
    }

    public JsonObject json() {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("team", team);
        j.addProperty("dimension", dimension);
        j.addProperty("x", pos.getX());
        j.addProperty("y", pos.getY());
        j.addProperty("z", pos.getZ());
        j.addProperty("infinite", infinite);
        return j;
    }

    public static Depot read(JsonObject j) {
        return new Depot(j.get("id").getAsString(), j.get("team").getAsString(), j.get("dimension").getAsString(),
                new BlockPos(j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt()), j.get("infinite").getAsBoolean());
    }
}
