package com.wf.wflib.soldier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/** The blocks marked as barracks, saved with the world. */
public final class BarracksBook extends SavedData {
    private final Set<String> marked = new LinkedHashSet<>();

    public static BarracksBook get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BarracksBook::new, (tag, reg) -> load(tag)), "wflib_soldier_barracks");
    }

    private static String key(String dimension, BlockPos pos) {
        return dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public boolean has(String dimension, BlockPos pos) {
        return marked.contains(key(dimension, pos));
    }

    public boolean add(String dimension, BlockPos pos) {
        boolean added = marked.add(key(dimension, pos));
        if (added) setDirty();
        return added;
    }

    public boolean remove(String dimension, BlockPos pos) {
        boolean gone = marked.remove(key(dimension, pos));
        if (gone) setDirty();
        return gone;
    }

    public int size() {
        return marked.size();
    }

    static BarracksBook load(CompoundTag tag) {
        BarracksBook book = new BarracksBook();
        for (Tag t : tag.getList("marked", Tag.TAG_STRING)) book.marked.add(t.getAsString());
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        marked.forEach(k -> list.add(net.minecraft.nbt.StringTag.valueOf(k)));
        tag.put("marked", list);
        return tag;
    }
}
