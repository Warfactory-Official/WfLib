package com.wf.wflib.armor.api.hull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Brick types by id; packs register theirs ({@link #replaceAll} on every reload). */
public final class EraTypes {

    private static volatile Map<String, EraType> types = Map.of();

    private EraTypes() {
    }

    /** Unknown id => {@link IllegalArgumentException}. */
    public static EraType get(String id) {
        EraType type = types.get(id);
        if (type == null) {
            throw new IllegalArgumentException("unknown era type '" + id + "'");
        }
        return type;
    }

    public static boolean contains(String id) {
        return types.containsKey(id);
    }

    public static synchronized void register(EraType type) {
        Map<String, EraType> next = new HashMap<>(types);
        next.put(type.id(), type);
        types = Map.copyOf(next);
    }

    /** The whole registry: what a reload read. */
    public static synchronized void replaceAll(Collection<EraType> all) {
        Map<String, EraType> next = new HashMap<>();
        all.forEach(type -> next.put(type.id(), type));
        types = Map.copyOf(next);
    }
}
