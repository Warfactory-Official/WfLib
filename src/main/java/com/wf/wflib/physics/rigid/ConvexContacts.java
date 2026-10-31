package com.wf.wflib.physics.rigid;

import java.util.Arrays;

/**
 * Polytope pair -> contact points, normal a -> b, separation (< 0 overlap): SAT over face normals of both and edge
 * cross products; face axis => incident face clipped by the reference face's sides; edge axis => closest points.
 * Separated within the margin => speculative points. One instance per thread.
 */
public final class ConvexContacts {

    /** Edge axis must separate this much more than the best face; flush faces then never flicker to an edge. */
    private static final float EDGE_PREFERENCE = 0.01f;
    static final float PARALLEL = 1.0e-4f;

    private final float[] rangeA = new float[2], rangeB = new float[2];
    /** (a direction, b direction) pairs some edge pair of which is a Minkowski-difference face. */
    private boolean[] minkowski = new boolean[64];
    private float[] polygon = new float[96], clipped = new float[96];
    private float bestEdge, edgeX, edgeY, edgeZ;
    private int edgeA, edgeB;
    private static final int NEAR = 4;
    private final int[] near = new int[NEAR];
    private final float[] nearDistance = new float[NEAR];
    private int nearCount;

    /** @return points added */
    public int collide(Polytope a, Polytope b, float margin, ContactBuffer out) {
        if (a.aligned) {
            return collideAligned(a, b, margin, out);
        }
        b.projectFaces();
        float bestFace = -Float.MAX_VALUE, faceX = 0, faceY = 0, faceZ = 0;
        boolean faceOfA = true;
        for (int pass = 0; pass < 2; pass++) {
            Polytope owner = pass == 0 ? a : b;
            for (int k = 0; k < owner.axisCount; k++) {
                float x = owner.axes[k * 3], y = owner.axes[k * 3 + 1], z = owner.axes[k * 3 + 2];
                float separation;
                if (pass == 0) {
                    separation = separation(a, b, x, y, z);
                } else {
                    a.project(x, y, z, rangeA);
                    rangeB[0] = b.faceRanges[k * 2];
                    rangeB[1] = b.faceRanges[k * 2 + 1];
                    separation = gap();
                }
                if (separation > margin) {
                    return 0;
                }
                if (separation > bestFace) {
                    bestFace = separation;
                    float sign = signOf(a, b, x, y, z);
                    faceX = x * sign;
                    faceY = y * sign;
                    faceZ = z * sign;
                    faceOfA = pass == 0;
                }
            }
        }
        // Face-separated: pruned pass result would be discarded by rescan (resets best) => straight to it.
        if (bestFace > 0) {
            if (!rescan(a, b, margin, bestFace + EDGE_PREFERENCE)) {
                return 0;
            }
        } else {
            markMinkowski(a, b);
            if (!edges(a, b, margin, true)
                    || Math.max(bestFace, bestEdge) > 0 && !rescan(a, b, margin, Math.max(bestEdge, bestFace + EDGE_PREFERENCE))) {
                return 0;
            }
        }
        if (edgeA >= 0 && bestEdge > bestFace + EDGE_PREFERENCE) {
            return edge(a, b, edgeA, edgeB, edgeX, edgeY, edgeZ, bestEdge, out);
        }
        return face(a, b, faceOfA, faceX, faceY, faceZ, margin, out);
    }

    /**
     * {@link #collide} for a world box a: same axes, order and floats (Minkowski-pruned edge axes). Per box O(axes):
     * b's ranges from {@link Polytope#projectAxes}, a's from its bounds (per-component corner by axis sign = extreme
     * vertex; rounding monotone => bitwise the vertex scan).
     */
    private int collideAligned(Polytope a, Polytope b, float margin, ContactBuffer out) {
        b.projectAxes();
        float bestFace = -Float.MAX_VALUE, faceX = 0, faceY = 0, faceZ = 0;
        boolean faceOfA = true;
        for (int k = 0; k < 3; k++) {
            float x = k == 0 ? 1 : 0, y = k == 1 ? 1 : 0, z = k == 2 ? 1 : 0;
            alignedRange(a, x, y, z);
            rangeB[0] = k == 0 ? b.minX : k == 1 ? b.minY : b.minZ;
            rangeB[1] = k == 0 ? b.maxX : k == 1 ? b.maxY : b.maxZ;
            float separation = gap();
            if (separation > margin) {
                return 0;
            }
            if (separation > bestFace) {
                bestFace = separation;
                float sign = signOf(a, b, x, y, z);
                faceX = x * sign;
                faceY = y * sign;
                faceZ = z * sign;
            }
        }
        for (int k = 0; k < b.axisCount; k++) {
            float x = b.axes[k * 3], y = b.axes[k * 3 + 1], z = b.axes[k * 3 + 2];
            alignedRange(a, x, y, z);
            rangeB[0] = b.faceRanges[k * 2];
            rangeB[1] = b.faceRanges[k * 2 + 1];
            float separation = gap();
            if (separation > margin) {
                return 0;
            }
            if (separation > bestFace) {
                bestFace = separation;
                float sign = signOf(a, b, x, y, z);
                faceX = x * sign;
                faceY = y * sign;
                faceZ = z * sign;
                faceOfA = false;
            }
        }
        if (bestFace > 0 ? !alignedEdges(a, b, margin, false)
                : !alignedEdges(a, b, margin, true) || Math.max(bestFace, bestEdge) > 0 && !alignedEdges(a, b, margin, false)) {
            return 0;
        }
        if (edgeA >= 0 && bestEdge > bestFace + EDGE_PREFERENCE) {
            return edge(a, b, edgeA, edgeB, edgeX, edgeY, edgeZ, bestEdge, out);
        }
        return face(a, b, faceOfA, faceX, faceY, faceZ, margin, out);
    }

    /**
     * Edge axes -> {@link #bestEdge}; false => one separates beyond margin. Pruned = Minkowski cells only: exact while
     * a and b overlap; separated (best > 0) => caller rescans all (non-face axis may separate further).
     */
    private boolean edges(Polytope a, Polytope b, float margin, boolean pruned) {
        bestEdge = -Float.MAX_VALUE;
        edgeA = -1;
        for (int i = 0; i < a.edgeCount; i++) {
            float ax = a.edges[i * 3], ay = a.edges[i * 3 + 1], az = a.edges[i * 3 + 2];
            for (int j = 0; j < b.edgeCount; j++) {
                if (pruned && !minkowski[i * b.edgeCount + j]) {
                    continue;
                }
                float bx = b.edges[j * 3], by = b.edges[j * 3 + 1], bz = b.edges[j * 3 + 2];
                float x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
                float length = (float) Math.sqrt(x * x + y * y + z * z);
                if (length < PARALLEL) {
                    continue;
                }
                x /= length;
                y /= length;
                z /= length;
                float separation = separation(a, b, x, y, z);
                if (!edgeAxis(a, b, separation, margin, x, y, z, i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * {@link #edges} unpruned. Axis skipped when bound U >= gap stays <= margin and < floor (can neither early-out nor
     * win): U = max(lo - maxA, minA - hi), lo/hi over b's points nearest a (in [minB, maxB]; rounding monotone) => same
     * result bitwise.
     */
    private boolean rescan(Polytope a, Polytope b, float margin, float floor) {
        nearest(a, b);
        float[] p = b.points;
        bestEdge = -Float.MAX_VALUE;
        edgeA = -1;
        for (int i = 0; i < a.edgeCount; i++) {
            float ax = a.edges[i * 3], ay = a.edges[i * 3 + 1], az = a.edges[i * 3 + 2];
            for (int j = 0; j < b.edgeCount; j++) {
                float bx = b.edges[j * 3], by = b.edges[j * 3 + 1], bz = b.edges[j * 3 + 2];
                float x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
                float length = (float) Math.sqrt(x * x + y * y + z * z);
                if (length < PARALLEL) {
                    continue;
                }
                x /= length;
                y /= length;
                z /= length;
                a.project(x, y, z, rangeA);
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (int k = 0; k < nearCount; k++) {
                    int v = near[k] * 3;
                    float d = p[v] * x + p[v + 1] * y + p[v + 2] * z;
                    if (d < lo) {
                        lo = d;
                    }
                    if (d > hi) {
                        hi = d;
                    }
                }
                float bound = Math.max(lo - rangeA[1], rangeA[0] - hi);
                // Best so far raises the floor: gap <= bound < best can neither win nor early-out.
                if (bound <= margin && (bound < floor || bound < bestEdge)) {
                    continue;
                }
                b.project(x, y, z, rangeB);
                if (!edgeAxis(a, b, gap(), margin, x, y, z, i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@link #near}: b's {@code NEAR} vertices closest to a's vertex mean. */
    private void nearest(Polytope a, Polytope b) {
        float cx = 0, cy = 0, cz = 0;
        for (int i = 0, end = a.vertexCount * 3; i < end; i += 3) {
            cx += a.points[i];
            cy += a.points[i + 1];
            cz += a.points[i + 2];
        }
        cx /= a.vertexCount;
        cy /= a.vertexCount;
        cz /= a.vertexCount;
        nearCount = 0;
        float[] p = b.points;
        for (int v = 0; v < b.vertexCount; v++) {
            float dx = p[v * 3] - cx, dy = p[v * 3 + 1] - cy, dz = p[v * 3 + 2] - cz, d = dx * dx + dy * dy + dz * dz;
            int k = nearCount < NEAR ? nearCount++ : NEAR;
            for (; k > 0 && nearDistance[k - 1] > d; k--) {
                if (k < NEAR) {
                    near[k] = near[k - 1];
                    nearDistance[k] = nearDistance[k - 1];
                }
            }
            if (k < NEAR) {
                near[k] = v;
                nearDistance[k] = d;
            }
        }
    }

    /** {@link #edges} over {@link Polytope#crossAxes}. */
    private boolean alignedEdges(Polytope a, Polytope b, float margin, boolean pruned) {
        if (!pruned) {
            b.projectRest();
        }
        bestEdge = -Float.MAX_VALUE;
        edgeA = -1;
        for (int c = 0, count = 3 * b.edgeCount; c < count; c++) {
            float x = b.crossAxes[c * 3];
            if (Float.isNaN(x) || pruned && !b.alignedMinkowski[c]) {
                continue;
            }
            float y = b.crossAxes[c * 3 + 1], z = b.crossAxes[c * 3 + 2];
            alignedRange(a, x, y, z);
            rangeB[0] = b.crossRanges[c * 2];
            rangeB[1] = b.crossRanges[c * 2 + 1];
            if (!edgeAxis(a, b, gap(), margin, x, y, z, c / b.edgeCount, c % b.edgeCount)) {
                return false;
            }
        }
        return true;
    }


    private boolean edgeAxis(Polytope a, Polytope b, float separation, float margin, float x, float y, float z, int i, int j) {
        if (separation > margin) {
            return false;
        }
        if (separation > bestEdge) {
            bestEdge = separation;
            float sign = signOf(a, b, x, y, z);
            edgeX = x * sign;
            edgeY = y * sign;
            edgeZ = z * sign;
            edgeA = i;
            edgeB = j;
        }
        return true;
    }

    /**
     * Gauss-map arcs (adjacent face normals; b's negated) intersect => that edge pair bounds a face of a - b. Other edge
     * axes never separate more than a face of a - b (Gregorius, GDC 2013) => skipped; floats of tested axes unchanged.
     */
    private void markMinkowski(Polytope a, Polytope b) {
        int cells = a.edgeCount * b.edgeCount;
        if (minkowski.length < cells) {
            minkowski = new boolean[cells];
        }
        markMinkowski(a.planes, a.edgeFaces, a.edgeFaceCount, a.edgeCount, b, minkowski);
    }

    /** out[a direction * b.edgeCount + b direction] for a's {@code directionsA} directions: cleared, then marked. */
    static void markMinkowski(float[] pa, int[] edgesA, int countA, int directionsA, Polytope b, boolean[] out) {
        b.poseArcs();
        int stride = b.edgeCount;
        Arrays.fill(out, 0, directionsA * stride, false);
        float[] arcs = b.arcs;
        int[] edgesB = b.edgeFaces;
        for (int ea = 0; ea < countA; ea++) {
            int f0 = edgesA[ea * 3] * 4, f1 = edgesA[ea * 3 + 1] * 4, row = edgesA[ea * 3 + 2] * stride;
            float ax = pa[f0], ay = pa[f0 + 1], az = pa[f0 + 2];
            float bx = pa[f1], by = pa[f1 + 1], bz = pa[f1 + 2];
            float bxaX = by * az - bz * ay, bxaY = bz * ax - bx * az, bxaZ = bx * ay - by * ax;
            for (int eb = 0, countB = b.edgeFaceCount; eb < countB; eb++) {
                int cell = row + edgesB[eb * 3 + 2];
                if (out[cell]) {
                    continue;
                }
                int o = eb * 9;
                float cba = arcs[o] * bxaX + arcs[o + 1] * bxaY + arcs[o + 2] * bxaZ;
                float dba = arcs[o + 3] * bxaX + arcs[o + 4] * bxaY + arcs[o + 5] * bxaZ;
                if (cba * dba > 0) {
                    continue;
                }
                float adc = ax * arcs[o + 6] + ay * arcs[o + 7] + az * arcs[o + 8];
                float bdc = bx * arcs[o + 6] + by * arcs[o + 7] + bz * arcs[o + 8];
                if (adc * bdc <= 0 && cba * bdc >= 0) {
                    out[cell] = true;
                }
            }
        }
    }

    private void alignedRange(Polytope a, float x, float y, float z) {
        rangeA[0] = (x >= 0 ? a.minX : a.maxX) * x + (y >= 0 ? a.minY : a.maxY) * y + (z >= 0 ? a.minZ : a.maxZ) * z;
        rangeA[1] = (x >= 0 ? a.maxX : a.minX) * x + (y >= 0 ? a.maxY : a.minY) * y + (z >= 0 ? a.maxZ : a.minZ) * z;
    }

    private float gap() {
        return Math.max(rangeB[0] - rangeA[1], rangeA[0] - rangeB[1]);
    }

    /** Gap between the projections; < 0 overlap. */
    private float separation(Polytope a, Polytope b, float x, float y, float z) {
        a.project(x, y, z, rangeA);
        b.project(x, y, z, rangeB);
        return Math.max(rangeB[0] - rangeA[1], rangeA[0] - rangeB[1]);
    }

    /** +1 if b lies along +axis from a (ranges from the last {@link #separation}). */
    private float signOf(Polytope a, Polytope b, float x, float y, float z) {
        return rangeB[0] - rangeA[1] >= rangeA[0] - rangeB[1] ? 1 : -1;
    }

    private int face(Polytope a, Polytope b, boolean faceOfA, float nx, float ny, float nz, float margin,
                     ContactBuffer out) {
        Polytope reference = faceOfA ? a : b, incident = faceOfA ? b : a;
        // Reference normal points from reference toward incident.
        float rx = faceOfA ? nx : -nx, ry = faceOfA ? ny : -ny, rz = faceOfA ? nz : -nz;
        int refFace = extremeFace(reference, rx, ry, rz, 1);
        int incFace = extremeFace(incident, rx, ry, rz, -1);
        float fx = reference.planes[refFace * 4], fy = reference.planes[refFace * 4 + 1];
        float fz = reference.planes[refFace * 4 + 2], fd = reference.planes[refFace * 4 + 3];

        int n = 0;
        int start = incident.faceStart[incFace], end = incident.faceStart[incFace + 1];
        ensure(end - start + reference.faceStart[refFace + 1] - reference.faceStart[refFace]);
        for (int k = start; k < end; k++) {
            int v = incident.loops[k] * 3;
            polygon[n * 3] = incident.points[v];
            polygon[n * 3 + 1] = incident.points[v + 1];
            polygon[n * 3 + 2] = incident.points[v + 2];
            n++;
        }
        int refStart = reference.faceStart[refFace], refEnd = reference.faceStart[refFace + 1];
        for (int k = refStart; k < refEnd && n > 0; k++) {
            int v0 = reference.loops[k] * 3;
            int v1 = reference.loops[k + 1 < refEnd ? k + 1 : refStart] * 3;
            float ex = reference.points[v1] - reference.points[v0];
            float ey = reference.points[v1 + 1] - reference.points[v0 + 1];
            float ez = reference.points[v1 + 2] - reference.points[v0 + 2];
            // Counter-clockwise from outside => interior on the left: n x e.
            float ix = fy * ez - fz * ey, iy = fz * ex - fx * ez, iz = fx * ey - fy * ex;
            float offset = ix * reference.points[v0] + iy * reference.points[v0 + 1] + iz * reference.points[v0 + 2];
            n = clip(n, ix, iy, iz, offset);
        }
        int added = 0;
        float sign = faceOfA ? 1 : -1;
        for (int k = 0; k < n; k++) {
            float qx = polygon[k * 3], qy = polygon[k * 3 + 1], qz = polygon[k * 3 + 2];
            float separation = qx * fx + qy * fy + qz * fz - fd;
            if (separation > margin) {
                continue;
            }
            float half = separation * 0.5f;
            out.add(qx - fx * half, qy - fy * half, qz - fz * half, fx * sign, fy * sign, fz * sign, separation,
                    a.tag, b.tag);
            added++;
        }
        return added;
    }

    /** Face whose normal is most (side 1) or least (side -1) along (x, y, z). */
    private static int extremeFace(Polytope p, float x, float y, float z, int side) {
        int best = 0;
        float bestDot = -Float.MAX_VALUE;
        for (int f = 0; f < p.faceCount; f++) {
            float dot = side * (p.planes[f * 4] * x + p.planes[f * 4 + 1] * y + p.planes[f * 4 + 2] * z);
            if (dot > bestDot) {
                bestDot = dot;
                best = f;
            }
        }
        return best;
    }

    /** Keeps (p . i) >= offset. */
    private int clip(int n, float ix, float iy, float iz, float offset) {
        int out = 0;
        for (int k = 0; k < n; k++) {
            int next = k + 1 < n ? k + 1 : 0;
            float ax = polygon[k * 3], ay = polygon[k * 3 + 1], az = polygon[k * 3 + 2];
            float bx = polygon[next * 3], by = polygon[next * 3 + 1], bz = polygon[next * 3 + 2];
            float da = ax * ix + ay * iy + az * iz - offset;
            float db = bx * ix + by * iy + bz * iz - offset;
            if (da >= 0) {
                clipped[out * 3] = ax;
                clipped[out * 3 + 1] = ay;
                clipped[out * 3 + 2] = az;
                out++;
            }
            if ((da < 0) != (db < 0)) {
                float t = da / (da - db);
                clipped[out * 3] = ax + (bx - ax) * t;
                clipped[out * 3 + 1] = ay + (by - ay) * t;
                clipped[out * 3 + 2] = az + (bz - az) * t;
                out++;
            }
        }
        float[] swap = polygon;
        polygon = clipped;
        clipped = swap;
        return out;
    }

    private int edge(Polytope a, Polytope b, int edgeA, int edgeB, float nx, float ny, float nz, float separation,
                     ContactBuffer out) {
        int segmentA = supportEdge(a, a.edges[edgeA * 3], a.edges[edgeA * 3 + 1], a.edges[edgeA * 3 + 2], nx, ny, nz);
        int segmentB = supportEdge(b, b.edges[edgeB * 3], b.edges[edgeB * 3 + 1], b.edges[edgeB * 3 + 2], -nx, -ny, -nz);
        if (segmentA < 0 || segmentB < 0) {
            return 0;
        }
        int a0 = a.loops[segmentA] * 3, a1 = a.loops[next(a, segmentA)] * 3;
        int b0 = b.loops[segmentB] * 3, b1 = b.loops[next(b, segmentB)] * 3;
        float px = a.points[a0], py = a.points[a0 + 1], pz = a.points[a0 + 2];
        float ux = a.points[a1] - px, uy = a.points[a1 + 1] - py, uz = a.points[a1 + 2] - pz;
        float qx = b.points[b0], qy = b.points[b0 + 1], qz = b.points[b0 + 2];
        float vx = b.points[b1] - qx, vy = b.points[b1 + 1] - qy, vz = b.points[b1 + 2] - qz;
        float wx = px - qx, wy = py - qy, wz = pz - qz;
        float uu = ux * ux + uy * uy + uz * uz, uv = ux * vx + uy * vy + uz * vz, vv = vx * vx + vy * vy + vz * vz;
        float uw = ux * wx + uy * wy + uz * wz, vw = vx * wx + vy * wy + vz * wz;
        float denominator = uu * vv - uv * uv;
        float s = denominator > 1.0e-9f ? clamp01((uv * vw - vv * uw) / denominator) : 0;
        float t = vv > 1.0e-9f ? clamp01((uv * s + vw) / vv) : 0;
        s = uu > 1.0e-9f ? clamp01((uv * t - uw) / uu) : 0;
        float cx = (px + ux * s + qx + vx * t) * 0.5f;
        float cy = (py + uy * s + qy + vy * t) * 0.5f;
        float cz = (pz + uz * s + qz + vz * t) * 0.5f;
        out.add(cx, cy, cz, nx, ny, nz, separation, a.tag, b.tag);
        return 1;
    }

    /** Loop index of the edge parallel to dir whose midpoint lies furthest along (x, y, z); -1 none. */
    private static int supportEdge(Polytope p, float dx, float dy, float dz, float x, float y, float z) {
        int best = -1;
        float bestDot = -Float.MAX_VALUE;
        for (int f = 0; f < p.faceCount; f++) {
            for (int k = p.faceStart[f], end = p.faceStart[f + 1]; k < end; k++) {
                int v0 = p.loops[k] * 3, v1 = p.loops[k + 1 < end ? k + 1 : p.faceStart[f]] * 3;
                float ex = p.points[v1] - p.points[v0], ey = p.points[v1 + 1] - p.points[v0 + 1];
                float ez = p.points[v1 + 2] - p.points[v0 + 2];
                float length = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                if (length < PARALLEL || Math.abs(ex * dx + ey * dy + ez * dz) < 0.999f * length) {
                    continue;
                }
                float dot = (p.points[v0] + p.points[v1]) * x + (p.points[v0 + 1] + p.points[v1 + 1]) * y
                        + (p.points[v0 + 2] + p.points[v1 + 2]) * z;
                if (dot > bestDot) {
                    bestDot = dot;
                    best = k;
                }
            }
        }
        return best;
    }

    /** Next loop index of the face containing loop index k. */
    private static int next(Polytope p, int k) {
        for (int f = 0; f < p.faceCount; f++) {
            if (k >= p.faceStart[f] && k < p.faceStart[f + 1]) {
                return k + 1 < p.faceStart[f + 1] ? k + 1 : p.faceStart[f];
            }
        }
        throw new IllegalStateException("loop index " + k);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private void ensure(int points) {
        // Each clip adds at most one vertex.
        int needed = (points + 2) * 3;
        if (polygon.length < needed) {
            polygon = Arrays.copyOf(polygon, needed);
            clipped = Arrays.copyOf(clipped, needed);
        }
    }
}
