package com.wf.wflib.physics.api;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Convex hull posed for sweeping; swept SAT test against it.
 *
 * <p>Axes/shadow cached once per pose; entry() is then bare arithmetic per box. Poses change
 * once/tick, boxes counted in hundreds.
 *
 * <p>Not thread-safe: one instance per vehicle solve, confined to one thread for its life.
 */
public final class ConvexSweep {

    /** |speed| below this on an axis: treated as static (no closing/opening). */
    private static final float MOTION_EPS = 1.0e-7f;

    /** Axes with |dot| > 1-this treated as duplicates. */
    private static final float AXIS_EPS = 1.0e-5f;

    @Nullable
    private ConvexHull posed;
    private final Quaternionf rotation = new Quaternionf(Float.NaN, Float.NaN, Float.NaN, Float.NaN);

    /** World-space axes, 3 floats each. */
    private float[] axes = new float[0];
    /** Projection interval per axis, (min,max) pairs. */
    private float[] shadow = new float[0];
    private int axisCount;

    private float[] worldPoints = new float[0];

    /** Has a posed hull with built axes. */
    public boolean ready() {
        return posed != null && axisCount > 0;
    }

    public void clear() {
        posed = null;
        axisCount = 0;
    }

    /** Rebuilds axes/shadow only when hull or orientation changed vs last pose. */
    public void pose(@Nullable ConvexHull hull, Quaternionf orientation) {
        if (hull == null) {
            clear();
            return;
        }
        if (hull == posed && orientation.equals(rotation)) {
            return;
        }
        posed = hull;
        rotation.set(orientation);
        rebuild(hull, orientation);
    }

    private void rebuild(ConvexHull hull, Quaternionf orientation) {
        int vertices = hull.vertexCount();
        if (worldPoints.length < vertices * 3) {
            worldPoints = new float[vertices * 3];
        }
        Vector3f scratch = new Vector3f();
        float[] local = hull.points();
        for (int i = 0; i < vertices; i++) {
            scratch.set(local[i * 3], local[i * 3 + 1], local[i * 3 + 2]);
            orientation.transform(scratch);
            worldPoints[i * 3] = scratch.x;
            worldPoints[i * 3 + 1] = scratch.y;
            worldPoints[i * 3 + 2] = scratch.z;
        }

        float[] normals = hull.axes();
        float[] edges = hull.edgeDirections();
        int capacity = normals.length / 3 + 3 + (edges.length / 3) * 3;
        if (axes.length < capacity * 3) {
            axes = new float[capacity * 3];
            shadow = new float[capacity * 2];
        }
        axisCount = 0;

        for (int i = 0; i < normals.length; i += 3) {
            scratch.set(normals[i], normals[i + 1], normals[i + 2]);
            orientation.transform(scratch);
            addAxis(scratch.x, scratch.y, scratch.z);
        }
        addAxis(1, 0, 0);
        addAxis(0, 1, 0);
        addAxis(0, 0, 1);
        for (int i = 0; i < edges.length; i += 3) {
            scratch.set(edges[i], edges[i + 1], edges[i + 2]);
            orientation.transform(scratch);
            // Each addAxis = edge x world axis (inlined); degenerate only if edge || axis, dropped by length.
            addAxis(0, scratch.z, -scratch.y);
            addAxis(-scratch.z, 0, scratch.x);
            addAxis(scratch.y, -scratch.x, 0);
        }

        for (int a = 0; a < axisCount; a++) {
            float nx = axes[a * 3], ny = axes[a * 3 + 1], nz = axes[a * 3 + 2];
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (int i = 0; i < vertices; i++) {
                float dot = worldPoints[i * 3] * nx + worldPoints[i * 3 + 1] * ny
                        + worldPoints[i * 3 + 2] * nz;
                min = Math.min(min, dot);
                max = Math.max(max, dot);
            }
            shadow[a * 2] = min;
            shadow[a * 2 + 1] = max;
        }
    }

    private void addAxis(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < AXIS_EPS) {
            return;
        }
        x /= length;
        y /= length;
        z /= length;
        for (int a = 0; a < axisCount; a++) {
            float dot = axes[a * 3] * x + axes[a * 3 + 1] * y + axes[a * 3 + 2] * z;
            if (Math.abs(dot) > 1 - AXIS_EPS) {
                return;
            }
        }
        axes[axisCount * 3] = x;
        axes[axisCount * 3 + 1] = y;
        axes[axisCount * 3 + 2] = z;
        axisCount++;
    }

    /** @return entry fraction in [0,1]; 1 = never met, 0 = already overlapping. */
    public double entry(double centreX, double centreY, double centreZ,
                        double moveX, double moveY, double moveZ,
                        double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ) {
        double halfX = (maxX - minX) * 0.5;
        double halfY = (maxY - minY) * 0.5;
        double halfZ = (maxZ - minZ) * 0.5;
        // double: subtraction done while both terms are world-sized, same reason OBB centre is double.
        double offsetX = (minX + maxX) * 0.5 - centreX;
        double offsetY = (minY + maxY) * 0.5 - centreY;
        double offsetZ = (minZ + maxZ) * 0.5 - centreZ;

        double enter = 0;
        double exit = 1;
        for (int a = 0; a < axisCount; a++) {
            double nx = axes[a * 3], ny = axes[a * 3 + 1], nz = axes[a * 3 + 2];
            double centre = offsetX * nx + offsetY * ny + offsetZ * nz;
            double radius = Math.abs(nx) * halfX + Math.abs(ny) * halfY + Math.abs(nz) * halfZ;
            double boxMin = centre - radius;
            double boxMax = centre + radius;
            double hullMin = shadow[a * 2];
            double hullMax = shadow[a * 2 + 1];
            double speed = moveX * nx + moveY * ny + moveZ * nz;

            if (speed > -MOTION_EPS && speed < MOTION_EPS) {
                if (hullMin > boxMax || hullMax < boxMin) {
                    return 1.0;
                }
                continue;
            }
            double first = (boxMin - hullMax) / speed;
            double last = (boxMax - hullMin) / speed;
            if (first > last) {
                double swap = first;
                first = last;
                last = swap;
            }
            if (first > enter) {
                enter = first;
            }
            if (last < exit) {
                exit = last;
            }
            if (enter > exit || enter > 1) {
                return 1.0;
            }
        }
        return Math.max(0, Math.min(enter, 1));
    }

    /** For the readout that prices a hull by axes tested. */
    public int axisCount() {
        return axisCount;
    }

}
