package com.wf.wflib.geom.api;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Segment {@code a -> b} vs point {@code p}. */
public final class Segments {
    private Segments() {
    }

    /** Unclamped parameter of the point nearest {@code p}; degenerate segment ({@code |b-a|^2 < 1e-12}) => 0. */
    public static double t(double ax, double ay, double az, double bx, double by, double bz, double px, double py,
                           double pz) {
        double sx = bx - ax, sy = by - ay, sz = bz - az;
        double len2 = sx * sx + sy * sy + sz * sz;
        return len2 < 1e-12 ? 0 : ((px - ax) * sx + (py - ay) * sy + (pz - az) * sz) / len2;
    }

    public static double t(Vec3 a, Vec3 b, Vec3 p) {
        return t(a.x, a.y, a.z, b.x, b.y, b.z, p.x, p.y, p.z);
    }

    /** Point of the segment nearest {@code p}; degenerate => {@code a}. */
    public static Vec3 closest(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 seg = b.subtract(a);
        double len2 = seg.lengthSqr();
        return len2 < 1e-12 ? a : a.add(seg.scale(Mth.clamp(p.subtract(a).dot(seg) / len2, 0, 1)));
    }
}
