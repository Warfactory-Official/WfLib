package com.wf.wflib.armor.api.hull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Plate material registry; packs add entries (composites) with {@link #register}. */
public final class PlateMaterials {

    private static final Map<String, PlateMaterial> MATERIALS = new ConcurrentHashMap<>();

    static {
        register(new PlateMaterial("rha", 1.0f, 1.0f, 1.0f, 1.0f));
        register(new PlateMaterial("hha", 1.25f, 1.1f, 1.0f, 1.2f));
        register(new PlateMaterial("cast", 0.92f, 0.92f, 1.0f, 1.0f));
        register(new PlateMaterial("aluminium", 0.35f, 0.4f, 1.0f, 1.5f));
        register(new PlateMaterial("nera", 1.1f, 2.0f, 0.0f, 0.5f));
        register(new PlateMaterial("spall_liner", 0.0f, 0.0f, 0.0f, 0.3f));
    }

    private PlateMaterials() {
    }

    /** Replaces an entry with the same id. */
    public static void register(PlateMaterial material) {
        MATERIALS.put(material.id(), material);
    }

    /** Unknown id => {@link IllegalArgumentException}. */
    public static PlateMaterial get(String id) {
        PlateMaterial m = id == null ? null : MATERIALS.get(id);
        if (m == null) {
            throw new IllegalArgumentException("unknown plate material '" + id + "'");
        }
        return m;
    }
}
