package com.wf.wflib.soldier.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** What hiring (or keeping) a soldier costs. A source or a wage picks one; any mix is fine. */
public interface Cost {
    Cost FREE = new Cost() {
        @Override public boolean affordable(ServerPlayer p) { return true; }
        @Override public boolean charge(ServerPlayer p) { return true; }
        @Override public String describe() { return "free"; }
    };

    boolean affordable(ServerPlayer p);

    /** Takes the cost. False, and nothing taken, when the player cannot pay. */
    boolean charge(ServerPlayer p);

    /** Short text for a menu: "3 x Emerald", "50 credits", "free". */
    String describe();

    static Cost items(Item item, int count) {
        if (count <= 0) return FREE;
        String name = new ItemStack(item).getHoverName().getString();
        return new Cost() {
            @Override public boolean affordable(ServerPlayer p) { return held(p) >= count; }

            @Override public boolean charge(ServerPlayer p) {
                if (!affordable(p)) return false;
                int left = count;
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.is(item)) continue;
                    int take = Math.min(left, s.getCount());
                    s.shrink(take);
                    left -= take;
                }
                return true;
            }

            private int held(ServerPlayer p) {
                int n = 0;
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
                return n;
            }

            @Override public String describe() { return count + " x " + name; }
        };
    }

    /** Currency from the {@link Recruiting#wallet wallet} a mod registered; unaffordable while there is none. */
    static Cost currency(int amount) {
        if (amount <= 0) return FREE;
        return new Cost() {
            @Override public boolean affordable(ServerPlayer p) {
                var w = Recruiting.wallet();
                return w != null && w.has(p, amount);
            }

            @Override public boolean charge(ServerPlayer p) {
                var w = Recruiting.wallet();
                return w != null && w.has(p, amount) && w.take(p, amount);
            }

            @Override public String describe() {
                var w = Recruiting.wallet();
                return w == null ? amount + " credits" : w.format(amount);
            }
        };
    }

    static Cost all(List<Cost> parts) {
        List<Cost> real = parts.stream().filter(c -> c != FREE).toList();
        if (real.isEmpty()) return FREE;
        if (real.size() == 1) return real.get(0);
        return new Cost() {
            @Override public boolean affordable(ServerPlayer p) { return real.stream().allMatch(c -> c.affordable(p)); }

            @Override public boolean charge(ServerPlayer p) {
                if (!affordable(p)) return false;
                real.forEach(c -> c.charge(p));
                return true;
            }

            @Override public String describe() { return String.join(" + ", real.stream().map(Cost::describe).toList()); }
        };
    }
}
