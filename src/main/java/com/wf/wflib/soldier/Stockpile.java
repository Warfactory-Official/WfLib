package com.wf.wflib.soldier;

import com.wf.wflib.compat.api.WarforgeCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a player's force has in storage: the contents of chests, barrels and shulker boxes in the chunks their WarForge
 * faction has claimed (only those loaded now), plus the containers marked as depots for a team. Counted by what an item
 * is, not by how worn it is: a damaged helmet is still that helmet. A scan is cached for a few seconds, so a screen that
 * refreshes does not walk every block entity each time.
 */
public final class Stockpile {
    /** One kind of item: where it is held and how many there are. */
    public record Entry(ItemStack proto, int count, List<Spot> spots) {}

    public record Spot(String dimension, BlockPos pos, int count) {}

    private static final long TTL = 100;
    private static final Map<UUID, Stockpile> CACHE = new HashMap<>();

    private final long taken;
    private final Map<String, Entry> items = new LinkedHashMap<>();
    private final Map<net.minecraft.world.item.Item, Integer> byItem = new HashMap<>();

    private Stockpile(long taken) {
        this.taken = taken;
    }

    /** A worn or damaged copy is the same thing: damage and repair cost are not part of what an item is. */
    static ItemStack plain(ItemStack s) {
        ItemStack c = s.copyWithCount(1);
        c.remove(DataComponents.DAMAGE);
        c.remove(DataComponents.REPAIR_COST);
        return c;
    }

    public static String key(ItemStack s) {
        ItemStack c = plain(s);
        return BuiltInRegistries.ITEM.getKey(c.getItem()) + "#" + c.getComponentsPatch().hashCode();
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(plain(a), plain(b));
    }

    /** The stock of {@code owner}'s force, rescanned when the last scan is older than a few seconds. */
    public static Stockpile of(MinecraftServer server, UUID owner, String team) {
        long now = server.getTickCount();
        Stockpile s = CACHE.get(owner);
        if (s != null && now - s.taken < TTL) return s;
        s = scan(server, owner, team, now);
        CACHE.put(owner, s);
        return s;
    }

    public static void forget() {
        CACHE.clear();
    }

    /** Drops a cached scan: something was just taken from or put into storage. */
    public static void changed(UUID owner) {
        CACHE.remove(owner);
    }

    public int count(ItemStack proto) {
        Entry e = items.get(key(proto));
        return e == null ? 0 : e.count;
    }

    /** All of one item however it is made (guns and grenades, which are matched by what they are). */
    public int countItem(net.minecraft.world.item.Item item) {
        return byItem.getOrDefault(item, 0);
    }

    public Entry entry(ItemStack proto) {
        return items.get(key(proto));
    }

    public Map<String, Entry> all() {
        return java.util.Collections.unmodifiableMap(items);
    }

    private void add(ItemStack stack, String dimension, BlockPos pos) {
        if (stack.isEmpty()) return;
        Entry old = items.get(key(stack));
        if (old == null) {
            old = new Entry(plain(stack), 0, new ArrayList<>());
            items.put(key(stack), old);
        }
        items.put(key(stack), new Entry(old.proto, old.count + stack.getCount(), old.spots));
        byItem.merge(stack.getItem(), stack.getCount(), Integer::sum);
        old.spots.add(new Spot(dimension, pos.immutable(), stack.getCount()));
    }

    private static Stockpile scan(MinecraftServer server, UUID owner, String team, long now) {
        Stockpile out = new Stockpile(now);
        UUID faction = WarforgeCompat.factionOfPlayer(owner);
        for (var dim : WarforgeCompat.claimedChunks(faction).entrySet()) {
            ServerLevel level = server.getLevel(dim.getKey());
            if (level == null) continue;
            for (long packed : dim.getValue()) {
                var chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved() || !storage(be)) continue;
                    Container box = (Container) be;
                    for (int i = 0; i < box.getContainerSize(); i++) out.add(box.getItem(i), dimension(dim.getKey()), be.getBlockPos());
                }
            }
        }
        for (Depot d : DepotBook.get(server).all().values()) {
            if (d.infinite() || !d.team().equals(team)) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.parse(d.dimension())));
            Container box = level == null ? null : Resupply.containerAt(level, d.pos());
            if (box == null) continue;
            for (int i = 0; i < box.getContainerSize(); i++) out.add(box.getItem(i), d.dimension(), d.pos());
        }
        return out;
    }

    private static String dimension(ResourceKey<Level> key) {
        return key.location().toString();
    }

    /** Plain storage only: not a furnace's fuel slot, a hopper in transit or a dispenser. */
    private static boolean storage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }
}
