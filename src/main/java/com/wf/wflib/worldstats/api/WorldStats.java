package com.wf.wflib.worldstats.api;

import java.util.Map;
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

    public static Map<String, Supplier<String>> tags() {
        return TAGS;
    }
}
