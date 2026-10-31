package com.wf.wflib.armor.api.hull;

import com.google.gson.annotations.SerializedName;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Hull-local hit face. +Z forward, matching {@link net.minecraft.world.entity.Entity#getLookAngle()} at zero yaw. */
public enum HullFace {

    @SerializedName("front") FRONT,
    @SerializedName("side") SIDE,
    @SerializedName("rear") REAR,
    @SerializedName("top") TOP,
    @SerializedName("bottom") BOTTOM;

    /** Face whose outward normal is {@code worldNormal}; {@code hullRotation}: hull -> world. */
    public static HullFace of(Quaternionf hullRotation, Vector3f worldNormal) {
        return of(toHullFrame(hullRotation, worldNormal));
    }

    public static HullFace of(Vector3f localNormal) {
        float ax = Math.abs(localNormal.x);
        float ay = Math.abs(localNormal.y);
        float az = Math.abs(localNormal.z);
        if (ay >= ax && ay >= az) {
            return localNormal.y >= 0 ? TOP : BOTTOM;
        }
        if (az >= ax) {
            return localNormal.z >= 0 ? FRONT : REAR;
        }
        return SIDE;
    }

    /** Signed unit hull axis along {@code local}'s largest component; ties as {@link #of(Vector3f)}. */
    public static Vector3f aspectAxis(Vector3f local) {
        float ax = Math.abs(local.x), ay = Math.abs(local.y), az = Math.abs(local.z);
        if (ay >= ax && ay >= az) {
            return new Vector3f(0, local.y >= 0 ? 1 : -1, 0);
        }
        if (az >= ax) {
            return new Vector3f(0, 0, local.z >= 0 ? 1 : -1);
        }
        return new Vector3f(local.x >= 0 ? 1 : -1, 0, 0);
    }

    /** World direction -> hull frame. */
    public static Vector3f toHullFrame(Quaternionf hullRotation, Vector3f world) {
        Vector3f local = new Vector3f(world);
        new Quaternionf(hullRotation).conjugate().transform(local);
        return local;
    }
}
