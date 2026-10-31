package com.wf.wflib.core;

import com.wf.wflib.core.api.SlotLayout;
import com.wf.wflib.core.api.SlotLayout.Entry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotLayoutTest {
    private static ResourceLocation id(String p) {
        return ResourceLocation.fromNamespaceAndPath("t", p);
    }

    @Test
    void orderIsPriorityThenIdIndependentOfRegistration() {
        Entry a = new Entry(id("a"), 0, 3);
        Entry b = new Entry(id("b"), 0, 2);
        Entry hi = new Entry(id("z"), 5, 1);
        assertEquals(List.of(hi, a, b), SlotLayout.order(List.of(b, hi, a)));
        assertEquals(List.of(hi, a, b), SlotLayout.order(List.of(a, b, hi)));
    }

    @Test
    void rangesAreContiguousFromBase() {
        var placed = SlotLayout.place(50, SlotLayout.order(List.of(new Entry(id("a"), 0, 64), new Entry(id("b"), -1, 3))));
        assertEquals(new SlotLayout.Placed(id("a"), 50, 64), placed.get(0));
        assertEquals(new SlotLayout.Placed(id("b"), 114, 3), placed.get(1));
        assertEquals(id("b"), SlotLayout.at(placed, 116).id());
        assertNull(SlotLayout.at(placed, 117));
        assertNull(SlotLayout.at(placed, 49));
    }

    @Test
    void emptyGroupRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Entry(id("a"), 0, 0));
    }
}
