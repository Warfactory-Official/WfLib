package com.wf.wflib.contract.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** The war archive: the last few things that happened, kept with the world. */
public final class News extends SavedData {
    public static final int KEEP = 50;
    private final List<String> lines = new ArrayList<>();

    public static News get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(News::new, (tag, reg) -> {
            News n = new News();
            JsonParser.parseString(tag.getString("lines")).getAsJsonArray().forEach(e -> n.lines.add(e.getAsString()));
            return n;
        }), "wflib_news");
    }

    public static void add(MinecraftServer server, String line) {
        News n = get(server);
        n.push(line);
    }

    /** Adds a line, dropping the oldest beyond {@link #KEEP}. */
    public void push(String line) {
        lines.add(line);
        while (lines.size() > KEEP) lines.remove(0);
        setDirty();
    }

    /** The newest {@code n} lines, newest first. */
    public List<String> latest(int n) {
        List<String> out = new ArrayList<>(lines);
        java.util.Collections.reverse(out);
        return out.subList(0, Math.min(n, out.size()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        JsonArray a = new JsonArray();
        lines.forEach(a::add);
        tag.putString("lines", a.toString());
        return tag;
    }
}
