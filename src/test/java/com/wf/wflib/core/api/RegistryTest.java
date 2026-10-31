package com.wf.wflib.core.api;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTest {
    private static ResourceLocation rl(String p) {
        return ResourceLocation.fromNamespaceAndPath("wflib", p);
    }

    @Test
    void duplicateThrows() {
        WFRegistry<String> r = WFRegistry.create("wflib:t");
        r.register(rl("a"), "x");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> r.register(rl("a"), "y"));
        assertTrue(e.getMessage().contains("wflib:t") && e.getMessage().contains("wflib:a"));
    }

    @Test
    void lateThrowsAfterFreeze() {
        WFRegistry<String> r = WFRegistry.create("wflib:t");
        OrderedRegistry<String> o = OrderedRegistry.create("wflib:o");
        r.register(rl("a"), "x");
        r.freeze();
        o.freeze();
        assertTrue(r.isFrozen());
        assertTrue(o.isFrozen());
        assertThrows(IllegalStateException.class, () -> r.register(rl("b"), "y"));
        assertThrows(IllegalStateException.class, () -> o.register(rl("b"), 0, "y"));
    }

    @Test
    void getRequireAndNulls() {
        WFRegistry<String> r = WFRegistry.create("wflib:t");
        assertEquals("x", r.register(rl("a"), "x"));
        assertNull(r.get(rl("b")));
        assertTrue(r.containsKey(rl("a")));
        assertEquals("x", r.require(rl("a")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> r.require(rl("b")));
        assertTrue(e.getMessage().contains("wflib:t") && e.getMessage().contains("wflib:b"));
        assertThrows(NullPointerException.class, () -> r.register(null, "z"));
        assertThrows(NullPointerException.class, () -> r.register(rl("c"), null));
    }

    @Test
    void insertionOrderAndImmutableSnapshots() {
        WFRegistry<String> r = WFRegistry.create("wflib:t");
        r.register(rl("b"), "1");
        r.register(rl("a"), "2");
        var before = r.values();
        r.register(rl("c"), "3");
        assertEquals(2, before.size());
        assertEquals(List.of("1", "2", "3"), List.copyOf(r.values()));
        assertEquals(List.of(rl("b"), rl("a"), rl("c")), List.copyOf(r.ids()));
        assertThrows(UnsupportedOperationException.class, () -> r.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.ids().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.entries().clear());
    }

    @Test
    void orderedByPriorityThenRegistration() {
        OrderedRegistry<String> o = OrderedRegistry.create("wflib:o");
        o.register(rl("a"), 0, "a");
        o.register(rl("b"), 10, "b");
        o.register(rl("c"), 0, "c");
        o.register(rl("d"), 10, "d");
        var before = o.values();
        o.register(rl("e"), 5, "e");
        assertEquals(List.of("b", "d", "a", "c"), before);
        assertEquals(List.of("b", "d", "e", "a", "c"), o.values());
        assertThrows(UnsupportedOperationException.class, () -> o.values().clear());
        assertThrows(IllegalStateException.class, () -> o.register(rl("a"), 1, "z"));
    }

    @Test
    void hookDoubleSetThrows() {
        Hook<String> h = Hook.of("wflib:h", "fb");
        assertEquals("fb", h.get());
        assertNull(h.owner());
        h.set("one", "v");
        assertEquals("v", h.get());
        assertEquals("one", h.owner());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> h.set("two", "w"));
        assertTrue(e.getMessage().contains("wflib:h") && e.getMessage().contains("one") && e.getMessage().contains("two"));
    }
}
