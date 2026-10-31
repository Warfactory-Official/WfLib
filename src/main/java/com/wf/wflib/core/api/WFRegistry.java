package com.wf.wflib.core.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Startup-only keyed extension point; reads see an immutable snapshot, writes copy-on-write. */
public final class WFRegistry<V> {
    private static final List<Freezable> ALL = new CopyOnWriteArrayList<>();

    private final String name;
    private final String owner;
    private volatile Map<ResourceLocation, V> snapshot = Map.of();
    private volatile boolean frozen;

    private WFRegistry(String owner, String name) {
        this.owner = owner;
        this.name = Objects.requireNonNull(name);
    }

    public static <V> WFRegistry<V> create(String name) {
        return create(null, name);
    }

    /** Owned by a wflib system: registering while that system is off throws. */
    public static <V> WFRegistry<V> create(String system, String name) {
        WFRegistry<V> r = new WFRegistry<>(system, name);
        ALL.add(r::freeze);
        return r;
    }

    /** Also freezes every {@link OrderedRegistry}. */
    public static void freezeAll() {
        for (Freezable f : ALL) f.freeze();
    }

    static void track(Freezable f) {
        ALL.add(f);
    }

    interface Freezable {
        void freeze();
    }

    public synchronized V register(ResourceLocation id, V value) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        if (owner != null) WFSystems.checkOn(owner, "Registry " + name);
        if (frozen) throw new IllegalStateException("Registry " + name + " is frozen, cannot register " + id);
        if (snapshot.containsKey(id)) throw new IllegalStateException("Registry " + name + " already has " + id);
        Map<ResourceLocation, V> next = new LinkedHashMap<>(snapshot);
        next.put(id, value);
        snapshot = Collections.unmodifiableMap(next);
        return value;
    }

    public V get(ResourceLocation id) {
        return snapshot.get(id);
    }

    public V require(ResourceLocation id) {
        V v = snapshot.get(id);
        if (v == null) throw new IllegalArgumentException("Registry " + name + " has no " + id);
        return v;
    }

    public boolean containsKey(ResourceLocation id) {
        return snapshot.containsKey(id);
    }

    public Set<ResourceLocation> ids() {
        return snapshot.keySet();
    }

    public Collection<V> values() {
        return snapshot.values();
    }

    public Set<Map.Entry<ResourceLocation, V>> entries() {
        return snapshot.entrySet();
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
