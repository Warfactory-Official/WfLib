package com.wf.wflib.barrel;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quick-change families: type -> display name + the spec its barrels cool / wear by. Filled by consumers at content
 * load (both sides, re-registered on reload); unknown type => {@link BarrelSpec#DEFAULT}, id as name.
 */
public final class BarrelTypes {
    public record Type(ResourceLocation id, Component name, BarrelSpec spec) {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<ResourceLocation, Type> TYPES = new ConcurrentHashMap<>();

    private BarrelTypes() {
    }

    /** Same type, different spec => last wins, warned. */
    public static void register(ResourceLocation type, Component name, BarrelSpec spec) {
        Type old = TYPES.put(type, new Type(type, name, spec));
        if (old != null && !old.spec.equals(spec)) {
            LOG.warn("barrel type {}: spec redefined by {}", type, name.getString());
        }
    }

    @Nullable
    public static Type get(ResourceLocation type) {
        return TYPES.get(type);
    }

    public static BarrelSpec spec(ResourceLocation type) {
        Type t = TYPES.get(type);
        return t == null ? BarrelSpec.DEFAULT : t.spec;
    }

    public static Component name(ResourceLocation type) {
        Type t = TYPES.get(type);
        return t == null ? Component.literal(type.toString()) : t.name;
    }

    /** Sorted by id. */
    public static List<Type> all() {
        return TYPES.values().stream().sorted(java.util.Comparator.comparing(t -> t.id().toString())).toList();
    }
}
