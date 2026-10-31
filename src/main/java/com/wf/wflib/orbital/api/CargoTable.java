package com.wf.wflib.orbital.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** What an asteroid miner comes back with, as a weighted pool anybody can add to. */
public final class CargoTable {

    /** Slots a shuttle can carry home. One chest, which is also what the block on the ground is. */
    public static final int SLOTS = 27;

    private record Entry(ItemStack prototype, int weight) {
    }

    private static final WFRegistry<Entry> POOL = WFRegistry.create("orbital", "asteroid cargo");

    private CargoTable() {
    }

    /**
     * Add an entry, keyed by the prototype's item; a dup item or registration after freeze throws.
     *
     * @param prototype one unit of this cargo. Copied on every roll, so the caller keeps ownership.
     */
    public static void register(ItemStack prototype, int weight) {
        if (prototype.isEmpty() || weight <= 0) {
            return;
        }
        POOL.register(BuiltInRegistries.ITEM.getKey(prototype.getItem()), new Entry(prototype.copy(), weight));
    }

    public static boolean isEmpty() {
        return POOL.values().isEmpty();
    }

    static int entryCount() {
        return POOL.values().size();
    }

    /**
     * Roll a manifest.
     *
     * @param units how much cargo the hold accrued.
     */
    public static List<ItemStack> roll(RandomSource random, int units) {
        List<ItemStack> out = new ArrayList<>();
        if (POOL.values().isEmpty() || units <= 0) {
            return out;
        }
        for (int i = 0; i < units; i++) {
            ItemStack picked = pick(random);
            if (picked == null) {
                continue;
            }
            ItemStack merged = null;
            for (ItemStack held : out) {
                if (ItemStack.isSameItemSameComponents(held, picked)
                        && held.getCount() + picked.getCount() <= held.getMaxStackSize()) {
                    held.grow(picked.getCount());
                    merged = held;
                    break;
                }
            }
            if (merged == null && out.size() < SLOTS) {
                out.add(picked.copy());
            }
        }
        return out;
    }

    private static ItemStack pick(RandomSource random) {
        int total = 0;
        for (Entry entry : POOL.values()) {
            total += entry.weight();
        }
        int roll = random.nextInt(Math.max(1, total));
        for (Entry entry : POOL.values()) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry.prototype();
            }
        }
        return null;
    }
}
