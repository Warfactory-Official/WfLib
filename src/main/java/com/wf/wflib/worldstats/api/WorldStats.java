package com.wf.wflib.worldstats.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Tags for the world-stats overlay ({@code {name}} in a line template). Other mods add their own, e.g. a difficulty tier. */
public final class WorldStats {
    private static final Map<String, Supplier<String>> TAGS = new ConcurrentHashMap<>();

    private WorldStats() {
    }

    /** Client. {@code name} is lower-case letters; the supplier runs every frame the overlay is drawn, so keep it cheap. */
    public static void tag(String name, Supplier<String> value) {
        TAGS.put(name, value);
    }

    private static final Map<String, Supplier<List<String>>> SECTIONS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Client. A block of lines drawn under the templates (a mission log): {@code lines} runs every frame and may return nothing,
     * in which case the section is not drawn. Legacy colour codes (section sign + 0-f) work. Sections draw in the order of their names.
     */
    public static void section(String name, Supplier<List<String>> lines) {
        SECTIONS.put(name, lines);
    }

    public static Map<String, Supplier<List<String>>> sections() {
        return new java.util.TreeMap<>(SECTIONS);
    }

    public static Map<String, Supplier<String>> tags() {
        return TAGS;
    }
}
