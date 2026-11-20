package com.wf.wflib.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The contracts in progress, saved with the world. */
public final class SessionBook extends SavedData {
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();

    public static SessionBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SessionBook::new, (tag, reg) -> load(tag)), "wflib_sessions");
    }

    public List<Session> all() {
        return new ArrayList<>(sessions.values());
    }

    public void put(Session s) {
        sessions.put(s.id(), s);
        setDirty();
    }

    public void remove(UUID id) {
        if (sessions.remove(id) != null) setDirty();
    }

    /** The contract this player works on, as owner or party member. */
    public Optional<Session> of(UUID player) {
        return sessions.values().stream().filter(s -> s.involves(player)).findFirst();
    }

    static SessionBook load(CompoundTag tag) {
        SessionBook book = new SessionBook();
        JsonArray list = JsonParser.parseString(tag.getString("sessions")).getAsJsonArray();
        list.forEach(e -> {
            Session s = Session.read(e.getAsJsonObject());
            book.sessions.put(s.id(), s);
        });
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        JsonArray list = new JsonArray();
        sessions.values().forEach(s -> list.add(s.json()));
        tag.putString("sessions", list.toString());
        return tag;
    }
}
