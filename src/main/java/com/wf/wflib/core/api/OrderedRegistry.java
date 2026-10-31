package com.wf.wflib.core.api;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Startup-only priority list; {@link #values()} = priority descending, then registration order. */
public final class OrderedRegistry<V> {
    private record Entry<V>(ResourceLocation id, int priority, V value) {}

    private final String name;
    private final String owner;
    private final List<Entry<V>> entries = new ArrayList<>();
    private volatile List<V> snapshot = List.of();
    private volatile boolean frozen;

    private OrderedRegistry(String owner, String name) {
        this.owner = owner;
        this.name = Objects.requireNonNull(name);
    }

    public static <V> OrderedRegistry<V> create(String name) {
        return create(null, name);
    }

    /** Owned by a wflib system: registering while that system is off throws. */
    public static <V> OrderedRegistry<V> create(String system, String name) {
        OrderedRegistry<V> r = new OrderedRegistry<>(system, name);
        WFRegistry.track(r::freeze);
        return r;
    }

    public synchronized V register(ResourceLocation id, int priority, V value) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        if (owner != null) WFSystems.checkOn(owner, "Registry " + name);
        if (frozen) throw new IllegalStateException("Registry " + name + " is frozen, cannot register " + id);
        for (Entry<V> e : entries) {
            if (e.id.equals(id)) throw new IllegalStateException("Registry " + name + " already has " + id);
        }
        entries.add(new Entry<>(id, priority, value));
        List<Entry<V>> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> Integer.compare(b.priority, a.priority));
        List<V> out = new ArrayList<>(sorted.size());
        for (Entry<V> e : sorted) out.add(e.value);
        snapshot = Collections.unmodifiableList(out);
        return value;
    }

    public List<V> values() {
        return snapshot;
    }

    public synchronized Set<ResourceLocation> ids() {
        Set<ResourceLocation> s = new HashSet<>();
        for (Entry<V> e : entries) s.add(e.id);
        return Collections.unmodifiableSet(s);
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
