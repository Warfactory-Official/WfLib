package com.wf.wflib.soldier;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a soldier was issued: each item it carries, how many, and one stack to copy from. Captured once the soldier is armed;
 * a supply container tops it back up to this, so equipping a force is "issue a loadout once, resupply restores it".
 */
public record Kit(Map<Item, Want> items) {
    public record Want(ItemStack proto, int target) {}

    public record Need(Item item, ItemStack proto, int count) {}

    public Kit {
        items = Map.copyOf(items);
    }

    /** Everything in the inventory except worn armour. */
    public static Kit of(Inventory inv) {
        Map<Item, Want> items = new LinkedHashMap<>();
        for (ItemStack s : inv.items) add(items, s);
        for (ItemStack s : inv.offhand) add(items, s);
        return new Kit(items);
    }

    private static void add(Map<Item, Want> items, ItemStack s) {
        if (s.isEmpty()) return;
        Want old = items.get(s.getItem());
        items.put(s.getItem(), new Want(old == null ? s.copyWithCount(1) : old.proto, (old == null ? 0 : old.target) + s.getCount()));
    }

    public static int count(Container inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    /** What the inventory lacks against this kit. */
    public List<Need> needs(Container inv) {
        List<Need> out = new ArrayList<>();
        items.forEach((item, want) -> {
            int missing = want.target - count(inv, item);
            if (missing > 0) out.add(new Need(item, want.proto, missing));
        });
        return out;
    }

    /** Worth a trip to a supply point: a stackable down to half or less, or a single item that is gone. */
    public boolean low(Container inv) {
        for (var e : items.entrySet()) {
            int have = count(inv, e.getKey()), target = e.getValue().target;
            if (target >= 2 && have * 2 <= target) return true;
            if (target == 1 && have == 0 && !e.getValue().proto.isDamageableItem()) return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
