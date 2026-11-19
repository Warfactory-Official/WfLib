package com.wf.wflib.photo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Index of stored photographs (the bytes live in files, {@link PhotoFiles}); overworld data. */
public final class PhotoBook extends SavedData {
    private static final String NAME = "wflib_photos";

    private final Map<UUID, PhotoEntry> byId = new LinkedHashMap<>();

    public static PhotoBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PhotoBook::new, (tag, registries) -> load(tag)), NAME);
    }

    public int count(UUID owner) {
        int n = 0;
        for (PhotoEntry e : byId.values()) {
            if (e.owner().equals(owner)) {
                n++;
            }
        }
        return n;
    }

    /** @return false at {@code quota}. */
    public boolean add(PhotoEntry entry, int quota) {
        if (count(entry.owner()) >= quota) {
            return false;
        }
        byId.put(entry.id(), entry);
        setDirty();
        return true;
    }

    @Nullable
    public PhotoEntry get(UUID id) {
        return byId.get(id);
    }

    /** Newest first. */
    public List<PhotoEntry> of(UUID owner) {
        List<PhotoEntry> out = new ArrayList<>();
        for (PhotoEntry e : byId.values()) {
            if (e.owner().equals(owner)) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Long.compare(b.time(), a.time()));
        return out;
    }

    /** @return the removed entry, null if {@code owner} does not own {@code id}. */
    @Nullable
    public PhotoEntry delete(UUID id, UUID owner) {
        PhotoEntry e = byId.get(id);
        if (e == null || !e.owner().equals(owner)) {
            return null;
        }
        byId.remove(id);
        setDirty();
        return e;
    }

    /** Marks printed; false if not the owner's photo. */
    public boolean share(UUID id, UUID owner) {
        PhotoEntry e = byId.get(id);
        if (e == null || !e.owner().equals(owner)) {
            return false;
        }
        if (!e.shared()) {
            byId.put(id, e.withShared(true));
            setDirty();
        }
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PhotoEntry e : byId.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.id());
            t.putUUID("owner", e.owner());
            t.putString("name", e.ownerName());
            t.putLong("time", e.time());
            t.putString("dim", e.dimension());
            t.putInt("x", e.x());
            t.putInt("y", e.y());
            t.putInt("z", e.z());
            t.putFloat("yaw", e.yaw());
            t.putFloat("pitch", e.pitch());
            t.putInt("size", e.size());
            t.putBoolean("shared", e.shared());
            list.add(t);
        }
        tag.put("photos", list);
        return tag;
    }

    static PhotoBook load(CompoundTag tag) {
        PhotoBook book = new PhotoBook();
        for (Tag raw : tag.getList("photos", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            PhotoEntry e = new PhotoEntry(t.getUUID("id"), t.getUUID("owner"), t.getString("name"), t.getLong("time"),
                    t.getString("dim"), t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getFloat("yaw"),
                    t.getFloat("pitch"), t.getInt("size"), t.getBoolean("shared"));
            book.byId.put(e.id(), e);
        }
        return book;
    }
}
