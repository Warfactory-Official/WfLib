package com.wf.wflib.soldier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every player's companions, saved with the world. */
public final class SquadBook extends SavedData {
    /** One player's squad: four fire teams of up to this many soldiers each at most (the server's global cap still applies). */
    public static final int MAX_PER_OWNER = 24;
    private final Map<UUID, List<Companion>> squads = new HashMap<>();

    public static SquadBook get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SquadBook::new, SquadBook::load), "wflib_soldier_squads");
    }

    public List<Companion> of(UUID owner) {
        return squads.getOrDefault(owner, List.of());
    }

    public void add(UUID owner, Companion c) {
        List<Companion> list = squads.computeIfAbsent(owner, k -> new ArrayList<>());
        if (list.size() >= MAX_PER_OWNER) throw new IllegalArgumentException("A squad is at most " + MAX_PER_OWNER + " soldiers");
        list.add(c);
        setDirty();
    }

    public boolean remove(UUID owner, UUID id) {
        List<Companion> list = squads.get(owner);
        boolean gone = list != null && list.removeIf(c -> c.id.equals(id));
        if (gone) setDirty();
        return gone;
    }

    public Companion find(UUID owner, UUID id) {
        for (Companion c : of(owner)) if (c.id.equals(id)) return c;
        return null;
    }

    /** The fire team with the fewest soldiers: where a recruit goes unless told otherwise. */
    public int emptiestTeam(UUID owner) {
        int[] n = new int[Companion.TEAMS.length];
        for (Companion c : of(owner)) n[c.fireTeam]++;
        int best = 0;
        for (int i = 1; i < n.length; i++) if (n[i] < n[best]) best = i;
        return best;
    }

    @Override
    public void setDirty() {
        super.setDirty();
    }

    static SquadBook load(CompoundTag tag, HolderLookup.Provider registries) {
        SquadBook book = new SquadBook();
        CompoundTag all = tag.getCompound("squads");
        for (String key : all.getAllKeys()) {
            List<Companion> list = new ArrayList<>();
            for (Tag t : all.getList(key, Tag.TAG_COMPOUND)) list.add(Companion.read((CompoundTag) t, registries));
            book.squads.put(UUID.fromString(key), list);
        }
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        squads.forEach((owner, list) -> {
            ListTag l = new ListTag();
            list.forEach(c -> l.add(c.save(registries)));
            all.put(owner.toString(), l);
        });
        tag.put("squads", all);
        return tag;
    }
}
