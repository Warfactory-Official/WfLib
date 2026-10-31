package com.wf.wflib.physics.api;

import com.wf.wflib.geom.api.OBB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A convex polyhedron with flat faces: the collision shape a box is one instance of.
 *
 * <p>A box carries three face normals and three edge directions, which is why a hull made of boxes
 * can only ever be a stack of boxes. This carries as many of each as the shape has, so a wedge, a
 * trapezoid or any other flat-faced solid describes itself exactly. Curved surfaces are out of scope
 * by construction: a sphere or a cone has to be approximated by facets like anything else.
 *
 * <p>Immutable and shared between every vehicle of a type, in the vehicle-local frame in blocks.
 * Posing is the caller's job, exactly as it is for {@link OBB}.
 *
 * <p>Separating-axis tests against this shape need the face normals and the edge directions, and both
 * are deduplicated at construction because an axis tested twice costs the same as one tested once and
 * proves nothing new. A box built through {@link #box} therefore reports the same fifteen axes the
 * hand-written box path uses, which is what lets the two agree.
 */
public final class ConvexHull {

    /** Planarity and convexity tolerance in blocks. A pack author works in millimetres at worst. */
    private static final float EPS = 1.0e-4f;

    /** Two directions closer than this are the same separating axis. */
    private static final float AXIS_EPS = 1.0e-5f;
    private static final float WELD = 1.0e-4f;

    /** Covariance of the canonical tetrahedron (origin, e1, e2, e3), for the inertia sum. */
    private static final double[] TET_COVARIANCE = {
            2 / 120.0, 1 / 120.0, 1 / 120.0,
            1 / 120.0, 2 / 120.0, 1 / 120.0,
            1 / 120.0, 1 / 120.0, 2 / 120.0,
    };

    private final float[] points;
    private final float[] planes;
    private final int[] faceStart;
    private final int[] faceIndex;
    private final float[] axes;
    private final float[] edges;
    private final int[] edgeFaces;

    private final Vector3f centroid;
    private final float volume;
    private final Matrix3f inertia;

    private final float minX, minY, minZ, maxX, maxY, maxZ;

    private ConvexHull(float[] points, float[] planes, int[] faceStart, int[] faceIndex,
                       float[] axes, float[] edges, Vector3f centroid, float volume, Matrix3f inertia,
                       float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.points = points;
        this.planes = planes;
        this.faceStart = faceStart;
        this.faceIndex = faceIndex;
        this.axes = axes;
        this.edges = edges;
        this.edgeFaces = edgeFaces(points, faceStart, faceIndex, faceStart.length - 1, edges, EPS, 1 - AXIS_EPS);
        this.centroid = centroid;
        this.volume = volume;
        this.inertia = inertia;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * Builds a hull from vertices and face loops.
     *
     * <p>The faces are supplied rather than computed. The exporter that wrote them already knew them,
     * and a hull algorithm in here would only be recovering what was thrown away, with every
     * degenerate case to handle and no author present to fix the model when it fails.
     *
     * @param points vertex positions in blocks, three floats each
     * @param faces  vertex indices per face, wound counter-clockwise seen from outside
     * @throws IllegalArgumentException if the shape is not a closed convex solid
     */
    public static ConvexHull of(float[] points, int[][] faces) {
        if (points.length < 12 || points.length % 3 != 0) {
            throw new IllegalArgumentException("a hull needs at least four vertices, got "
                    + points.length / 3);
        }
        if (faces.length < 4) {
            throw new IllegalArgumentException("a hull needs at least four faces, got " + faces.length);
        }
        int vertices = points.length / 3;
        Vector3f interior = mean(points);

        float[] planes = new float[faces.length * 4];
        int[] faceStart = new int[faces.length + 1];
        int total = 0;
        for (int[] face : faces) {
            total += face.length;
        }
        int[] faceIndex = new int[total];

        int cursor = 0;
        int[][] wound = new int[faces.length][];
        for (int f = 0; f < faces.length; f++) {
            int[] face = faces[f];
            if (face.length < 3) {
                throw new IllegalArgumentException("face " + f + " has " + face.length + " vertices");
            }
            for (int index : face) {
                if (index < 0 || index >= vertices) {
                    throw new IllegalArgumentException("face " + f + " names vertex " + index
                            + " of " + vertices);
                }
            }
            normalOf(points, face, f, planes, f * 4);
            float d = planes[f * 4] * points[face[0] * 3]
                    + planes[f * 4 + 1] * points[face[0] * 3 + 1]
                    + planes[f * 4 + 2] * points[face[0] * 3 + 2];
            // Outward by construction: the mean of the vertices is inside any convex solid, so a
            // plane it sits in front of was wound the wrong way round. Reverse the loop as well as
            // the normal, because the volume integral and the debug draw both read the winding.
            if (planes[f * 4] * interior.x + planes[f * 4 + 1] * interior.y
                    + planes[f * 4 + 2] * interior.z > d) {
                planes[f * 4] = -planes[f * 4];
                planes[f * 4 + 1] = -planes[f * 4 + 1];
                planes[f * 4 + 2] = -planes[f * 4 + 2];
                d = -d;
                face = reverse(face);
            }
            planes[f * 4 + 3] = d;

            wound[f] = face;
            faceStart[f] = cursor;
            for (int index : face) {
                faceIndex[cursor++] = index;
            }
        }
        faceStart[faces.length] = cursor;

        checkPlanar(points, wound, planes);
        checkConvex(points, wound, planes);

        return finish(points, planes, faceStart, faceIndex, wound);
    }

    /** An axis-aligned box as a hull, so a pack can mix the shorthand with the general form. */
    public static ConvexHull box(float minX, float minY, float minZ,
                                 float maxX, float maxY, float maxZ) {
        float[] points = {
                minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ,
        };
        int[][] faces = {
                {0, 3, 2, 1}, {4, 5, 6, 7},
                {0, 1, 5, 4}, {3, 7, 6, 2},
                {0, 4, 7, 3}, {1, 2, 6, 5},
        };
        return of(points, faces);
    }

    private static ConvexHull finish(float[] points, float[] planes, int[] faceStart, int[] faceIndex,
                                     int[][] faces) {
        float[] axes = uniqueAxes(planes, faces.length);
        float[] edges = uniqueEdges(points, faces);

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < points.length; i += 3) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxY = Math.max(maxY, points[i + 1]);
            minZ = Math.min(minZ, points[i + 2]);
            maxZ = Math.max(maxZ, points[i + 2]);
        }

        Vector3f centroid = new Vector3f();
        Matrix3f inertia = new Matrix3f();
        float volume = massProperties(points, faces, centroid, inertia);
        if (volume <= EPS * EPS * EPS) {
            throw new IllegalArgumentException("hull encloses no volume (" + volume + ")");
        }

        return new ConvexHull(points, planes, faceStart, faceIndex, axes, edges,
                centroid, volume, inertia, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int[] reverse(int[] face) {
        int[] out = new int[face.length];
        for (int i = 0; i < face.length; i++) {
            out[i] = face[face.length - 1 - i];
        }
        return out;
    }

    private static Vector3f mean(float[] points) {
        Vector3f sum = new Vector3f();
        for (int i = 0; i < points.length; i += 3) {
            sum.add(points[i], points[i + 1], points[i + 2]);
        }
        return sum.div(points.length / 3f);
    }

    /** Newell's normal, which uses the whole loop and so survives a face with a sliver triangle in it. */
    private static void normalOf(float[] points, int[] face, int faceNumber, float[] out, int offset) {
        float nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < face.length; i++) {
            int a = face[i] * 3;
            int b = face[(i + 1) % face.length] * 3;
            nx += (points[a + 1] - points[b + 1]) * (points[a + 2] + points[b + 2]);
            ny += (points[a + 2] - points[b + 2]) * (points[a] + points[b]);
            nz += (points[a] - points[b]) * (points[a + 1] + points[b + 1]);
        }
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < EPS) {
            throw new IllegalArgumentException("face " + faceNumber + " is degenerate");
        }
        out[offset] = nx / length;
        out[offset + 1] = ny / length;
        out[offset + 2] = nz / length;
    }

    private static void checkPlanar(float[] points, int[][] faces, float[] planes) {
        for (int f = 0; f < faces.length; f++) {
            for (int index : faces[f]) {
                float distance = planes[f * 4] * points[index * 3]
                        + planes[f * 4 + 1] * points[index * 3 + 1]
                        + planes[f * 4 + 2] * points[index * 3 + 2] - planes[f * 4 + 3];
                if (Math.abs(distance) > EPS) {
                    throw new IllegalArgumentException("face " + f + " is not flat: vertex " + index
                            + " sits " + distance + " blocks off its plane");
                }
            }
        }
    }

    private static void checkConvex(float[] points, int[][] faces, float[] planes) {
        for (int f = 0; f < faces.length; f++) {
            for (int i = 0; i < points.length; i += 3) {
                float distance = planes[f * 4] * points[i] + planes[f * 4 + 1] * points[i + 1]
                        + planes[f * 4 + 2] * points[i + 2] - planes[f * 4 + 3];
                if (distance > EPS) {
                    throw new IllegalArgumentException("hull is not convex: vertex " + i / 3
                            + " sits " + distance + " blocks outside face " + f);
                }
            }
        }
    }

    /** Face normals with antiparallel duplicates folded together, since an axis has no sign. */
    private static float[] uniqueAxes(float[] planes, int faceCount) {
        List<float[]> kept = new ArrayList<>(faceCount);
        for (int f = 0; f < faceCount; f++) {
            float[] axis = {planes[f * 4], planes[f * 4 + 1], planes[f * 4 + 2]};
            if (!seen(kept, axis)) {
                kept.add(axis);
            }
        }
        return flatten(kept);
    }

    private static float[] uniqueEdges(float[] points, int[][] faces) {
        List<float[]> kept = new ArrayList<>();
        for (int[] face : faces) {
            for (int i = 0; i < face.length; i++) {
                int a = face[i] * 3;
                int b = face[(i + 1) % face.length] * 3;
                float dx = points[b] - points[a];
                float dy = points[b + 1] - points[a + 1];
                float dz = points[b + 2] - points[a + 2];
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < EPS) {
                    continue;
                }
                float[] edge = {dx / length, dy / length, dz / length};
                if (!seen(kept, edge)) {
                    kept.add(edge);
                }
            }
        }
        return flatten(kept);
    }

    private static boolean seen(List<float[]> kept, float[] candidate) {
        for (float[] existing : kept) {
            float dot = existing[0] * candidate[0] + existing[1] * candidate[1] + existing[2] * candidate[2];
            if (Math.abs(dot) > 1 - AXIS_EPS) {
                return true;
            }
        }
        return false;
    }

    private static float[] flatten(List<float[]> values) {
        float[] out = new float[values.size() * 3];
        for (int i = 0; i < values.size(); i++) {
            System.arraycopy(values.get(i), 0, out, i * 3, 3);
        }
        return out;
    }

    /**
     * Volume, centroid and the inertia tensor about that centroid, per unit mass.
     *
     * <p>Every face is fanned into triangles and every triangle into a tetrahedron against a
     * reference point. The signed volumes cancel over the parts of space outside the solid, so the
     * reference point can be anywhere; it is the mean of the vertices so that the integration runs
     * in hull-sized numbers. A hull twenty blocks up a carrier would otherwise spend the whole
     * mantissa on a magnitude that cancels out, and the inertia would come back visibly wrong.
     *
     * <p>Accumulated in double for the same reason, and narrowed once at the end.
     */
    private static float massProperties(float[] points, int[][] faces, Vector3f centroid, Matrix3f inertia) {
        Vector3f origin = mean(points);
        double volume = 0;
        double cx = 0, cy = 0, cz = 0;
        double[] covariance = new double[9];
        double[] tet = new double[9];

        for (int[] face : faces) {
            for (int i = 1; i + 1 < face.length; i++) {
                int a = face[0] * 3;
                int b = face[i] * 3;
                int c = face[i + 1] * 3;
                double ax = points[a] - origin.x, ay = points[a + 1] - origin.y, az = points[a + 2] - origin.z;
                double bx = points[b] - origin.x, by = points[b + 1] - origin.y, bz = points[b + 2] - origin.z;
                double dx = points[c] - origin.x, dy = points[c + 1] - origin.y, dz = points[c + 2] - origin.z;

                double determinant = ax * (by * dz - bz * dy)
                        - ay * (bx * dz - bz * dx)
                        + az * (bx * dy - by * dx);
                double tetVolume = determinant / 6.0;
                volume += tetVolume;
                cx += tetVolume * (ax + bx + dx) / 4.0;
                cy += tetVolume * (ay + by + dy) / 4.0;
                cz += tetVolume * (az + bz + dz) / 4.0;

                // A * C_canonical * A^T scaled by the determinant, with A the tet's edge matrix.
                tet[0] = ax; tet[1] = bx; tet[2] = dx;
                tet[3] = ay; tet[4] = by; tet[5] = dy;
                tet[6] = az; tet[7] = bz; tet[8] = dz;
                accumulate(covariance, tet, determinant);
            }
        }

        if (Math.abs(volume) < EPS * EPS * EPS) {
            return 0;
        }
        cx /= volume;
        cy /= volume;
        cz /= volume;

        // Shift the covariance to the centroid, then turn it into an inertia tensor per unit mass.
        double[] c = {cx, cy, cz};
        double[] shifted = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                shifted[row * 3 + column] = covariance[row * 3 + column] - volume * c[row] * c[column];
            }
        }
        double trace = shifted[0] + shifted[4] + shifted[8];
        inertia.set(
                (float) ((trace - shifted[0]) / volume), (float) (-shifted[3] / volume), (float) (-shifted[6] / volume),
                (float) (-shifted[1] / volume), (float) ((trace - shifted[4]) / volume), (float) (-shifted[7] / volume),
                (float) (-shifted[2] / volume), (float) (-shifted[5] / volume), (float) ((trace - shifted[8]) / volume));

        centroid.set((float) (cx + origin.x), (float) (cy + origin.y), (float) (cz + origin.z));
        return (float) volume;
    }

    private static void accumulate(double[] covariance, double[] a, double determinant) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                double sum = 0;
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        sum += a[row * 3 + i] * TET_COVARIANCE[i * 3 + j] * a[column * 3 + j];
                    }
                }
                covariance[row * 3 + column] += determinant * sum;
            }
        }
    }

    /** Vertex positions in blocks, three floats each. Do not write into this. */
    public float[] points() {
        return points;
    }

    public int vertexCount() {
        return points.length / 3;
    }

    public int faceCount() {
        return faceStart.length - 1;
    }

    /** Face planes as normal xyz then the plane offset, four floats each. */
    public float[] planes() {
        return planes;
    }

    /** Vertex indices of one face, as a range into {@link #faceVertices()}. */
    public int faceStart(int face) {
        return faceStart[face];
    }

    public int faceEnd(int face) {
        return faceStart[face + 1];
    }

    public int[] faceVertices() {
        return faceIndex;
    }

    /** Deduplicated face normals: the first group of separating axes. */
    public float[] axes() {
        return axes;
    }

    /** Deduplicated edge directions, to be crossed with the other shape's for the remaining axes. */
    public float[] edgeDirections() {
        return edges;
    }

    /** Per undirected edge: face, twin face, {@link #edgeDirections()} index. */
    public int[] edgeFaces() {
        return edgeFaces;
    }

    private static int[] edgeFaces(float[] points, int[] faceStart, int[] loops, int faceCount, float[] directions,
                                   float minLength, float parallel) {
        // Non-manifold authored hulls (edge on 3+ faces) emit per directed edge: size for all.
        int[] out = new int[(faceStart[faceCount] - faceStart[0]) * 3];
        int count = edgeFaces(points, faceStart, loops, faceCount, directions, directions.length / 3, minLength, parallel, out);
        return Arrays.copyOf(out, count * 3);
    }

    /**
     * Undirected edges of a closed polyhedron into {@code out} as (face, twin face, direction index) triples; twins by
     * position (faces may own vertex copies: clipped hulls); direction =
     * first of {@code directions[0, directionCount)} with |dot| > {@code parallel}. Shorter than {@code minLength} or no
     * direction (degenerate) skipped. Allocation-free. @return edges written
     */
    public static int edgeFaces(float[] points, int[] faceStart, int[] loops, int faceCount, float[] directions,
                                int directionCount, float minLength, float parallel, int[] out) {
        int count = 0;
        for (int f = 0; f < faceCount; f++) {
            for (int k = faceStart[f], end = faceStart[f + 1]; k < end; k++) {
                int from = loops[k], to = loops[k + 1 < end ? k + 1 : faceStart[f]];
                int twin = -1;
                for (int g = f + 1; g < faceCount && twin < 0; g++) {
                    for (int m = faceStart[g], gEnd = faceStart[g + 1]; m < gEnd; m++) {
                        if (near(points, loops[m], to) && near(points, loops[m + 1 < gEnd ? m + 1 : faceStart[g]], from)) {
                            twin = g;
                            break;
                        }
                    }
                }
                if (twin < 0) {
                    continue;
                }
                int a = from * 3, b = to * 3;
                float dx = points[b] - points[a], dy = points[b + 1] - points[a + 1], dz = points[b + 2] - points[a + 2];
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (!(length >= minLength)) {
                    continue;
                }
                dx /= length;
                dy /= length;
                dz /= length;
                int direction = -1;
                for (int d = 0; d < directionCount && direction < 0; d++) {
                    if (Math.abs(directions[d * 3] * dx + directions[d * 3 + 1] * dy + directions[d * 3 + 2] * dz) > parallel) {
                        direction = d;
                    }
                }
                if (direction < 0) {
                    continue;
                }
                out[count * 3] = f;
                out[count * 3 + 1] = twin;
                out[count * 3 + 2] = direction;
                count++;
            }
        }
        return count;
    }

    private static boolean near(float[] points, int i, int j) {
        float dx = points[i * 3] - points[j * 3], dy = points[i * 3 + 1] - points[j * 3 + 1], dz = points[i * 3 + 2] - points[j * 3 + 2];
        return dx * dx + dy * dy + dz * dz < WELD * WELD;
    }

    public Vector3f centroid() {
        return centroid;
    }

    public float volume() {
        return volume;
    }

    /** Inertia about the centroid for a solid of unit mass, in body axes. */
    public Matrix3f inertia() {
        return inertia;
    }

    public float minX() { return minX; }

    public float minY() { return minY; }

    public float minZ() { return minZ; }

    public float maxX() { return maxX; }

    public float maxY() { return maxY; }

    public float maxZ() { return maxZ; }

    /** The tightest axis-aligned box holding this hull, in its own frame. */
    public Vector3f extents(Vector3f dest) {
        return dest.set((maxX - minX) * 0.5f, (maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f);
    }

    /** The furthest point along a direction, which is what a separating-axis test projects. */
    public Vector3f support(float dx, float dy, float dz, Vector3f dest) {
        int best = 0;
        float bestDot = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < points.length; i += 3) {
            float dot = points[i] * dx + points[i + 1] * dy + points[i + 2] * dz;
            if (dot > bestDot) {
                bestDot = dot;
                best = i;
            }
        }
        return dest.set(points[best], points[best + 1], points[best + 2]);
    }

    /** This hull's shadow on an axis, as min then max. */
    public void project(float dx, float dy, float dz, float[] out) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < points.length; i += 3) {
            float dot = points[i] * dx + points[i + 1] * dy + points[i + 2] * dz;
            min = Math.min(min, dot);
            max = Math.max(max, dot);
        }
        out[0] = min;
        out[1] = max;
    }

    /**
     * The part of this hull on the inner side of a plane, or null if the plane cuts all of it away.
     *
     * <p>Used to build the shape a sweep casts: everything below the climb skirt is trimmed off, so a
     * kerb the vehicle is going to drive over does not read as a wall. Clipping a convex solid with a
     * plane leaves a convex solid, which is the property that makes this cheap and the result usable
     * by the same separating-axis test as the original.
     *
     * @param nx,ny,nz the plane's normal; the half-space kept is where {@code n . p <= offset}
     * @param offset   the plane's offset along that normal
     * @param inset    how far every face is drawn back afterwards.
     *                 A resting surface is exactly touching, and a cast shape that includes its own
     *                 skin reports every rest as a blocked step
     */
    @Nullable
    public ConvexHull clipped(float nx, float ny, float nz, float offset, float inset) {
        int count = vertexCount();
        float[] distance = new float[count];
        boolean anyOutside = false;
        boolean anyInside = false;
        for (int i = 0; i < count; i++) {
            distance[i] = points[i * 3] * nx + points[i * 3 + 1] * ny + points[i * 3 + 2] * nz - offset;
            anyOutside |= distance[i] > EPS;
            anyInside |= distance[i] < -EPS;
        }
        if (!anyInside) {
            return null;
        }
        ConvexHull kept = anyOutside ? cut(distance, inset == 0) : this;
        if (kept == null || inset <= 0) {
            return kept;
        }
        return kept.shrunk(inset);
    }

    /**
     * Sutherland-Hodgman over every face, plus the polygon the plane itself leaves behind.
     *
     * <p>The cut face's vertices are whatever the crossing edges produced, and they all lie in the
     * plane, so they are sorted by angle about the plane's own frame to make a loop. Without that they
     * are in edge order, which zigzags, and the solid would fail its own flatness check.
     */
    @Nullable
    private ConvexHull cut(float[] distance, boolean exact) {
        List<float[]> vertices = new ArrayList<>(vertexCount() + 8);
        int[] moved = new int[vertexCount()];
        Arrays.fill(moved, -1);
        List<int[]> faces = new ArrayList<>(faceCount() + 1);
        List<Integer> capVertices = new ArrayList<>();

        for (int face = 0; face < faceCount(); face++) {
            int from = faceStart[face];
            int to = faceStart[face + 1];
            List<Integer> loop = new ArrayList<>(to - from + 2);
            for (int i = from; i < to; i++) {
                int a = faceIndex[i];
                int b = faceIndex[i + 1 == to ? from : i + 1];
                if (distance[a] <= EPS) {
                    if (moved[a] < 0) {
                        moved[a] = vertices.size();
                        vertices.add(new float[]{points[a * 3], points[a * 3 + 1], points[a * 3 + 2]});
                        if (distance[a] >= -EPS) {
                            capVertices.add(moved[a]);
                        }
                    }
                    loop.add(moved[a]);
                }
                boolean crosses = (distance[a] > EPS && distance[b] < -EPS)
                        || (distance[a] < -EPS && distance[b] > EPS);
                if (crosses) {
                    float t = distance[a] / (distance[a] - distance[b]);
                    int index = vertices.size();
                    vertices.add(new float[]{
                            points[a * 3] + (points[b * 3] - points[a * 3]) * t,
                            points[a * 3 + 1] + (points[b * 3 + 1] - points[a * 3 + 1]) * t,
                            points[a * 3 + 2] + (points[b * 3 + 2] - points[a * 3 + 2]) * t});
                    loop.add(index);
                    capVertices.add(index);
                }
            }
            if (loop.size() >= 3) {
                faces.add(toArray(loop));
            }
        }
        if (capVertices.size() >= 3) {
            int[] cap = orderAroundCut(vertices, capVertices);
            if (cap != null) {
                faces.add(cap);
            }
        }
        if (vertices.size() < 4 || faces.size() < 4) {
            return null;
        }

        float[] flat = new float[vertices.size() * 3];
        for (int i = 0; i < vertices.size(); i++) {
            System.arraycopy(vertices.get(i), 0, flat, i * 3, 3);
        }
        try {
            return of(flat, faces.toArray(new int[0][]));
        } catch (IllegalArgumentException degenerate) {
            // A clip that grazes a face leaves slivers a hair off their own plane. The caller wants a
            // shape to cast, not an exception, and the uncut hull is the safe answer: larger than the
            // truth, so it stops early rather than passing through something.
            return exact ? null : this;
        }
    }

    @Nullable
    private static int[] orderAroundCut(List<float[]> vertices, List<Integer> onPlane) {
        List<Integer> unique = new ArrayList<>(onPlane.size());
        for (int index : onPlane) {
            boolean seen = false;
            for (int kept : unique) {
                float[] a = vertices.get(kept);
                float[] b = vertices.get(index);
                if (Math.abs(a[0] - b[0]) < EPS && Math.abs(a[1] - b[1]) < EPS
                        && Math.abs(a[2] - b[2]) < EPS) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                unique.add(index);
            }
        }
        if (unique.size() < 3) {
            return null;
        }
        Vector3f centre = new Vector3f();
        for (int index : unique) {
            float[] p = vertices.get(index);
            centre.add(p[0], p[1], p[2]);
        }
        centre.div(unique.size());

        float[] first = vertices.get(unique.get(0));
        Vector3f u = new Vector3f(first[0], first[1], first[2]).sub(centre);
        if (u.lengthSquared() < EPS * EPS) {
            return null;
        }
        u.normalize();
        Vector3f w = new Vector3f();
        for (int i = 1; i < unique.size() && w.lengthSquared() < EPS; i++) {
            float[] p = vertices.get(unique.get(i));
            new Vector3f(p[0], p[1], p[2]).sub(centre).cross(u, w);
        }
        if (w.lengthSquared() < EPS * EPS) {
            return null;
        }
        w.normalize();
        Vector3f v = new Vector3f(w).cross(u).normalize();

        List<Integer> sorted = new ArrayList<>(unique);
        sorted.sort((a, b) -> Float.compare(angle(vertices.get(a), centre, u, v),
                angle(vertices.get(b), centre, u, v)));
        return toArray(sorted);
    }

    private static float angle(float[] point, Vector3f centre, Vector3f u, Vector3f v) {
        float dx = point[0] - centre.x, dy = point[1] - centre.y, dz = point[2] - centre.z;
        return (float) Math.atan2(dx * v.x + dy * v.y + dz * v.z, dx * u.x + dy * u.y + dz * u.z);
    }

    private static int[] toArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    /**
     * A copy drawn back from its own surface, so a shape resting against something does not report
     * itself as blocked by it. Every face plane offset inward by exactly inset (clip by each).
     * Centroid scale FORBIDDEN: moves a face by distance*inset/nearest, ~10% of a low piece.
     */
    private ConvexHull shrunk(float inset) {
        ConvexHull result = this;
        for (int face = 0; face < faceCount() && result != null; face++) {
            result = result.clipped(planes[face * 4], planes[face * 4 + 1], planes[face * 4 + 2],
                    planes[face * 4 + 3] - inset, 0);
        }
        // Thinner than 2*inset or a degenerate sliver: the uncut shape is larger than the truth => stops early.
        return result != null ? result : this;
    }

    /** A copy with every vertex rotated and then shifted, for baking a piece into a bone's frame. */
    public ConvexHull transformed(Quaternionf rotation, Vector3f offset) {
        float[] moved = new float[points.length];
        Vector3f scratch = new Vector3f();
        for (int i = 0; i < points.length; i += 3) {
            scratch.set(points[i], points[i + 1], points[i + 2]);
            rotation.transform(scratch).add(offset);
            moved[i] = scratch.x;
            moved[i + 1] = scratch.y;
            moved[i + 2] = scratch.z;
        }
        int[][] faces = new int[faceCount()][];
        for (int f = 0; f < faces.length; f++) {
            faces[f] = Arrays.copyOfRange(faceIndex, faceStart[f], faceStart[f + 1]);
        }
        return of(moved, faces);
    }

}
