package com.wf.wflib.contract.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Every operator's profile, saved with the world (not in player data: offline lookups and leaderboards work). */
public final class ProfileBook extends SavedData {
    private final Map<UUID, Profile> profiles = new HashMap<>();

    public static ProfileBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ProfileBook::new, (tag, reg) -> load(tag)), "wflib_profiles");
    }

    /** The player's profile, created empty on first use. Callers that change it call {@link #setDirty()}. */
    public Profile of(UUID player) {
        return profiles.computeIfAbsent(player, id -> new Profile());
    }

    static ProfileBook load(CompoundTag tag) {
        ProfileBook book = new ProfileBook();
        JsonObject j = JsonParser.parseString(tag.getString("profiles")).getAsJsonObject();
        j.entrySet().forEach(e -> book.profiles.put(UUID.fromString(e.getKey()), Profile.read(e.getValue().getAsJsonObject())));
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        JsonObject j = new JsonObject();
        profiles.forEach((id, p) -> j.add(id.toString(), p.json()));
        tag.putString("profiles", j.toString());
        return tag;
    }
}
