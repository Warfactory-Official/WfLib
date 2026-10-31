package com.wf.wflib.barrel.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quick-change families: type -> display name + the spec its barrels cool / wear by. Reload-backed: each source
 * ({@link #reload}) hands over its whole set on every content load, both sides; unknown type =>
 * {@link BarrelSpec#DEFAULT}, id as name.
 */
public final class BarrelTypes {
    public record Type(ResourceLocation id, Component name, BarrelSpec spec) {
    }

    private static final Map<String, Map<ResourceLocation, Type>> SOURCES = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, Type> merged = Map.of();

    private BarrelTypes() {
    }

    /**
     * Replaces everything {@code source} contributed, atomically. Same type twice in {@code types}, or in another
     * source, with a different spec => {@link IllegalStateException}, nothing swapped; equal spec => first kept.
     */
    public static synchronized void reload(String source, Collection<Type> types) {
        Map<ResourceLocation, Type> own = new LinkedHashMap<>();
        for (Type t : types) {
            Type prev = own.putIfAbsent(t.id(), t);
            if (prev != null && !prev.spec().equals(t.spec())) {
                throw new IllegalStateException("barrel type " + t.id() + ": spec defined twice by " + source);
            }
        }
        Map<String, Map<ResourceLocation, Type>> next = new LinkedHashMap<>(SOURCES);
        next.put(source, Collections.unmodifiableMap(own));
        Map<ResourceLocation, Type> all = new HashMap<>();
        next.forEach((s, m) -> m.forEach((id, t) -> {
            Type prev = all.putIfAbsent(id, t);
            if (prev != null && !prev.spec().equals(t.spec())) {
                throw new IllegalStateException("barrel type " + id + ": spec defined by " + source + " and by " + s);
            }
        }));
        SOURCES.clear();
        SOURCES.putAll(next);
        merged = Collections.unmodifiableMap(all);
    }

    @Nullable
    public static Type get(ResourceLocation type) {
        return merged.get(type);
    }

    public static BarrelSpec spec(ResourceLocation type) {
        Type t = merged.get(type);
        return t == null ? BarrelSpec.DEFAULT : t.spec;
    }

    public static Component name(ResourceLocation type) {
        Type t = merged.get(type);
        return t == null ? Component.literal(type.toString()) : t.name;
    }

    /** Sorted by id. */
    public static List<Type> all() {
        return merged.values().stream().sorted(Comparator.comparing(t -> t.id().toString())).toList();
    }
}
