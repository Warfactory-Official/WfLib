package com.wf.wflib.core.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Pure slot-group placement: order = priority descending then id; ranges are contiguous from the first free index. */
public final class SlotLayout {
    public record Entry(ResourceLocation id, int priority, int capacity) {
        public Entry {
            if (capacity < 1) {
                throw new IllegalArgumentException("slot group " + id + " capacity " + capacity);
            }
        }
    }

    public record Placed(ResourceLocation id, int first, int count) {
        public int end() {
            return first + count;
        }

        public boolean contains(int index) {
            return index >= first && index < end();
        }
    }

    private SlotLayout() {
    }

    public static List<Entry> order(Collection<Entry> entries) {
        return entries.stream().sorted(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(e -> e.id().toString())).toList();
    }

    public static List<Placed> place(int base, List<Entry> ordered) {
        List<Placed> out = new ArrayList<>(ordered.size());
        int next = base;
        for (Entry e : ordered) {
            out.add(new Placed(e.id(), next, e.capacity()));
            next += e.capacity();
        }
        return List.copyOf(out);
    }

    @Nullable
    public static Placed at(List<Placed> placed, int index) {
        for (Placed p : placed) {
            if (p.contains(index)) {
                return p;
            }
        }
        return null;
    }
}
