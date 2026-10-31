package com.wf.wflib.missile.api;

import com.wf.wflib.view.api.Rotor;
import com.wf.wflib.view.api.Rotors;
import com.wf.wflib.geom.api.ObjBounds;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.wf.wflib.core.api.WFLib;

/** {@link MissileEntity} model registry; id persists/syncs on the entity (HBM-style runtime pick, not compiled in). */
public final class MissileModels {

    /** Fallback when a requested id is unknown or unset. */
    public static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "v2");
    public static final String DEFAULT_ATTITUDE = "missile";
    private static final Map<ResourceLocation, ResourceLocation> BY_ID = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Double> LENGTHS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Vec3> DIMENSIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Vec3> CENTERS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, String> ATTITUDES = new HashMap<>();

    private MissileModels() {
    }

    public static synchronized void register(String id, String modelName) {
        if (BY_ID.putIfAbsent(rl(id), partModel(modelName)) != null) {
            throw new IllegalStateException("missile model " + id + " already registered");
        }
    }

    public static ResourceLocation partModel(String modelName) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "entity/missiles/" + modelName);
    }

    public static ResourceLocation rl(String id) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, id);
    }

    public static ResourceLocation defaultId() {
        return DEFAULT;
    }

    /** Bare path under mod namespace, {@code ns:path} as-is, unparseable => {@link #DEFAULT}. */
    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null ? parsed : DEFAULT;
    }

    public static boolean exists(ResourceLocation id) {
        return BY_ID.containsKey(id);
    }

    public static ResourceLocation model(ResourceLocation id) {
        return BY_ID.getOrDefault(id, BY_ID.get(DEFAULT));
    }

    /** @return registered ids, in registration order. */
    public static Set<ResourceLocation> ids() {
        return Collections.unmodifiableSet(BY_ID.keySet());
    }

    /** @return longest mesh axis (model units), cached; reads off the jar (works server-side); 1.0 if unreadable. */
    public static double length(ResourceLocation id) {
        return LENGTHS.computeIfAbsent(id, i -> {
            try {
                double len = ObjBounds.longestAxisFromModel(model(i));
                return len > 1.0E-3 ? len : 1.0;
            } catch (Throwable t) {
                return 1.0;
            }
        });
    }

    /** @return mesh size per axis (model units), cached; reads off the jar (works server-side); 1x1x1 if unreadable. */
    public static Vec3 dimensions(ResourceLocation id) {
        return DIMENSIONS.computeIfAbsent(id, i -> {
            try {
                Vec3 d = ObjBounds.dimensionsFromModel(model(i));
                return (d.x > 1.0E-3 || d.y > 1.0E-3 || d.z > 1.0E-3) ? d : new Vec3(1.0, 1.0, 1.0);
            } catch (Throwable t) {
                return new Vec3(1.0, 1.0, 1.0);
            }
        });
    }

    /** @return center offset (model units), cached; base-at-origin mesh => roughly {@code (0, length/2, 0)}. */
    public static Vec3 center(ResourceLocation id) {
        return CENTERS.computeIfAbsent(id, i -> {
            try {
                return ObjBounds.centerFromModel(model(i));
            } catch (Throwable t) {
                return Vec3.ZERO;
            }
        });
    }

    public static void rotor(String id, String rotorModelName, Axis axis, float degreesPerTick) {
        Rotors.assign(rl(id), Rotor.of(partModel(rotorModelName), axis, degreesPerTick));
    }

    public static void rotor(String id, String rotorModelName, Vector3f axis, float degreesPerTick) {
        Rotors.assign(rl(id), Rotor.of(partModel(rotorModelName), axis, degreesPerTick));
    }

    public static void rotors(String id, Axis axis, float degreesPerTick, String... rotorModelNames) {
        for (String rotorModelName : rotorModelNames) {
            rotor(id, rotorModelName, axis, degreesPerTick);
        }
    }

    public static List<Rotor> rotors(ResourceLocation id) {
        return Rotors.of(id);
    }

    /** Set how a model orients to its heading, by attitude id (see {@code MissileAttitudes}). */
    public static void attitude(String id, String attitudeId) {
        ATTITUDES.put(rl(id), attitudeId);
    }

    public static String attitudeId(ResourceLocation id) {
        return ATTITUDES.getOrDefault(id, DEFAULT_ATTITUDE);
    }

}
