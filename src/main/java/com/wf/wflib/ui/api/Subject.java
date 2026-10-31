package com.wf.wflib.ui.api;

import com.wf.wflib.core.api.WFRegistry;

import java.util.List;
import java.util.function.Supplier;

/**
 * A kind of object signals read from; its {@link #name()} is the first segment of every signal name
 * ({@code vehicle.airspeed}). The host's {@link UiContext} supplies the object each frame; a subject
 * with a {@code fallback} needs no context entry.
 */
public final class Subject<S> {
    private static final WFRegistry<Subject<?>> SUBJECTS = WFRegistry.create("ui", "ui.subjects");

    private final String name;
    private final Class<S> type;
    private final Supplier<? extends S> fallback;
    private final int ordinal;

    private Subject(String name, Class<S> type, Supplier<? extends S> fallback, int ordinal) {
        this.name = name;
        this.type = type;
        this.fallback = fallback;
        this.ordinal = ordinal;
    }

    public static synchronized <S> Subject<S> register(String name, Class<S> type) {
        return register(name, type, null);
    }

    public static synchronized <S> Subject<S> register(String name, Class<S> type, Supplier<? extends S> fallback) {
        if (!name.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("Subject name must be [a-z_][a-z0-9_]*: " + name);
        }
        Subject<S> subject = new Subject<>(name, type, fallback, SUBJECTS.ids().size());
        SUBJECTS.register(Signals.key(name), subject);
        Signals.registerPresence(subject);
        return subject;
    }

    public static synchronized Subject<?> byName(String name) {
        return SUBJECTS.get(Signals.key(name));
    }

    static synchronized List<Subject<?>> all() {
        return List.copyOf(SUBJECTS.values());
    }

    public String name() {
        return name;
    }

    public Class<S> type() {
        return type;
    }

    int ordinal() {
        return ordinal;
    }

    public Object fallback() {
       return fallback == null ? null : fallback.get();
    }

    @Override
    public String toString() {
        return "Subject[" + name + "]";
    }
}
