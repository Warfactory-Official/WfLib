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
    private final Map<UUID, List<Companion>> squads = new HashMap<>();
    /** A hire on its way: who it is for, which saved variant, and the game time it arrives. */
    public record Pending(UUID owner, String variant, long due) {}

    private final List<Pending> pending = new ArrayList<>();
    /** The in-game day each owner's soldiers were last paid. */
    private final Map<UUID, Long> wageDay = new HashMap<>();

    public List<Pending> pending() {
        return pending;
    }

    public void addPending(Pending p) {
        pending.add(p);
        setDirty();
    }

    public void removePending(Pending p) {
        if (pending.remove(p)) setDirty();
    }

    public Long wageDay(UUID owner) {
        return wageDay.get(owner);
    }

    public void wageDay(UUID owner, long day) {
        wageDay.put(owner, day);
        setDirty();
    }

    public static SquadBook get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SquadBook::new, SquadBook::load), "wflib_soldier_squads");
    }

    public List<Companion> of(UUID owner) {
        return squads.getOrDefault(owner, List.of());
    }

    public void add(UUID owner, Companion c) {
        List<Companion> list = squads.computeIfAbsent(owner, k -> new ArrayList<>());
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

    /**
     * Moves companions into a fire team, to sit before that team's {@code index}-th member (past its last when {@code index} is
     * too big). The moved ones keep their order among themselves.
     */
    public void place(UUID owner, List<UUID> ids, int team, int index) {
        List<Companion> list = squads.get(owner);
        if (list == null) return;
        List<Companion> moved = new ArrayList<>();
        for (Companion c : list) if (ids.contains(c.id)) moved.add(c);
        if (moved.isEmpty()) return;
        list.removeAll(moved);
        moved.forEach(c -> c.fireTeam = team);
        int seen = 0, at = list.size();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).fireTeam != team) continue;
            if (seen++ == Math.max(0, index)) {
                at = i;
                break;
            }
        }
        list.addAll(at, moved);
        setDirty();
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
        for (Tag t : tag.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            book.pending.add(new Pending(c.getUUID("owner"), c.getString("variant"), c.getLong("due")));
        }
        CompoundTag wages = tag.getCompound("wages");
        for (String key : wages.getAllKeys()) book.wageDay.put(UUID.fromString(key), wages.getLong(key));
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
        ListTag arriving = new ListTag();
        for (Pending p : pending) {
            CompoundTag c = new CompoundTag();
            c.putUUID("owner", p.owner());
            c.putString("variant", p.variant());
            c.putLong("due", p.due());
            arriving.add(c);
        }
        tag.put("pending", arriving);
        CompoundTag wages = new CompoundTag();
        wageDay.forEach((owner, day) -> wages.putLong(owner.toString(), day));
        tag.put("wages", wages);
        return tag;
    }
}
