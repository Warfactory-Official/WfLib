package com.wf.wflib.core.api;

import java.util.Objects;

/** Single-slot override; a second {@link #set}, or one after {@link #freezeAll}, throws. */
public final class Hook<T> {
    private static final java.util.List<Hook<?>> ALL = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final String name;
    private final T fallback;
    private volatile T value;
    private volatile String owner;
    private volatile boolean frozen;

    private Hook(String name, T fallback) {
        this.name = Objects.requireNonNull(name);
        this.fallback = Objects.requireNonNull(fallback);
        ALL.add(this);
    }

    /** Ends the window in which dependents may {@link #set}; run at {@code FMLLoadCompleteEvent}. */
    public static void freezeAll() {
        for (Hook<?> hook : ALL) {
            hook.frozen = true;
        }
    }

    public static <T> Hook<T> of(String name, T fallback) {
        return new Hook<>(name, fallback);
    }

    public synchronized void set(String ownerModId, T newValue) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        if (frozen) {
            throw new IllegalStateException("Hook " + name + " is frozen, cannot set by " + ownerModId);
        }
        Objects.requireNonNull(newValue, "value");
        if (owner != null) {
            throw new IllegalStateException("Hook " + name + " already set by " + owner + ", cannot set by " + ownerModId);
        }
        value = newValue;
        owner = ownerModId;
    }

    public T get() {
        T v = value;
        return v != null ? v : fallback;
    }

    public String owner() {
        return owner;
    }
}
