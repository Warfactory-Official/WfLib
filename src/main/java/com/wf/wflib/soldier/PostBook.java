package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.soldier.api.SoldierSpec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The soldiers that belong to no roster (spawn-egg and editor soldiers, enemy posts, garrisons), saved with the world so
 * a restart does not empty it. A companion is not in here: it is in its owner's {@link SquadBook} and comes back with them.
 */
public final class PostBook extends SavedData {
    /**
     * One soldier as it stood: who it fights for, what it carries and wears, where it was and what it was told.
     *
     * @param owner (nullable) who a friendly follows
     * @param post where it stands guard (an enemy returns there after a chase)
     * @param garrison the territory that raised it, or empty
     */
    public record Post(String team, String prefix, boolean hunts, @Nullable ResourceLocation gun, List<ResourceLocation> grenades, int spares,
                       SoldierRole role, int range, float spread, String voice, String light, String name, @Nullable UUID owner,
                       String dimension, Vec3 pos, float yaw, Vec3 post, Map<EquipmentSlot, ItemStack> armour, SoldierOrder order,
                       String garrison) {
        public SoldierSpec spec() {
            return new SoldierSpec(team, prefix, hunts, gun, grenades, Map.of(), spares, role);
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag t = new CompoundTag();
            t.putString("team", team);
            t.putString("prefix", prefix);
            t.putBoolean("hunts", hunts);
            if (gun != null) t.putString("gun", gun.toString());
            t.put("grenades", Tags.ids(grenades));
            t.putInt("spares", spares);
            t.putString("role", role.id);
            t.putInt("range", range);
            t.putFloat("spread", spread);
            t.putString("voice", voice);
            t.putString("light", light);
            t.putString("name", name);
            if (owner != null) t.putUUID("owner", owner);
            t.putString("dimension", dimension);
            t.putDouble("x", pos.x);
            t.putDouble("y", pos.y);
            t.putDouble("z", pos.z);
            t.putFloat("yaw", yaw);
            t.putDouble("px", post.x);
            t.putDouble("py", post.y);
            t.putDouble("pz", post.z);
            t.put("armour", Tags.armour(armour, registries));
            t.put("order", Tags.order(order));
            t.putString("garrison", garrison);
            return t;
        }

        static Post read(CompoundTag t, HolderLookup.Provider registries) {
            Vec3 pos = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
            return new Post(t.getString("team"), t.getString("prefix"), t.getBoolean("hunts"),
                    t.contains("gun") ? ResourceLocation.parse(t.getString("gun")) : null, Tags.ids(t.getList("grenades", Tag.TAG_STRING)),
                    t.getInt("spares"), SoldierRole.tactics(t.getString("role")), t.getInt("range"), t.getFloat("spread"), t.getString("voice"),
                    t.getString("light").isEmpty() ? "auto" : t.getString("light"), t.getString("name"), t.hasUUID("owner") ? t.getUUID("owner") : null,
                    t.getString("dimension"), pos, t.getFloat("yaw"), t.contains("px") ? new Vec3(t.getDouble("px"), t.getDouble("py"), t.getDouble("pz")) : pos,
                    Tags.armour(t.getCompound("armour"), registries), Tags.order(t.getCompound("order")), t.getString("garrison"));
        }
    }

    private List<Post> posts = new ArrayList<>();

    public static PostBook get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PostBook::new, PostBook::load), "wflib_soldier_posts");
    }

    public List<Post> posts() {
        return posts;
    }

    public void replace(List<Post> now) {
        posts = new ArrayList<>(now);
        setDirty();
    }

    static PostBook load(CompoundTag tag, HolderLookup.Provider registries) {
        PostBook book = new PostBook();
        for (Tag t : tag.getList("posts", Tag.TAG_COMPOUND)) book.posts.add(Post.read((CompoundTag) t, registries));
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        posts.forEach(p -> list.add(p.save(registries)));
        tag.put("posts", list);
        return tag;
    }
}
