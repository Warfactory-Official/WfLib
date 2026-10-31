package com.wf.wflib.physics.rigid;

import com.wf.wflib.physics.api.ConvexHull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/** Convex solid posed relative to a solver origin, float: vertices, face planes + loops, SAT axes. Reused. */
public final class Polytope {

    /** Vertex i: bit 0 x, bit 1 y, bit 2 z, set = +extent. Loops counter-clockwise from outside. */
    private static final int[] BOX_START = {0, 4, 8, 12, 16, 20, 24};
    private static final int[] BOX_LOOPS = {0, 4, 6, 2, 1, 3, 7, 5, 0, 1, 5, 4, 2, 6, 7, 3, 0, 2, 3, 1, 4, 5, 7, 6};
    private static final float[] BOX_NORMALS = {-1, 0, 0, 1, 0, 0, 0, -1, 0, 0, 1, 0, 0, 0, -1, 0, 0, 1};
    private static final float[] UNIT_AXES = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private static final int[] BOX_EDGE_FACES = new int[36];
    /** Unit box face planes (offsets unused): Gauss map of every world box. */
    private static final float[] BOX_PLANES = new float[24];

    static {
        float[] corners = new float[24];
        for (int i = 0; i < 8; i++) {
            corners[i * 3] = i & 1;
            corners[i * 3 + 1] = (i >> 1) & 1;
            corners[i * 3 + 2] = (i >> 2) & 1;
        }
        ConvexHull.edgeFaces(corners, BOX_START, BOX_LOOPS, 6, UNIT_AXES, 3, 0.5f, 0.5f, BOX_EDGE_FACES);
        for (int f = 0; f < 6; f++) {
            System.arraycopy(BOX_NORMALS, f * 3, BOX_PLANES, f * 4, 3);
        }
    }

    static int[] boxEdgeFaces() {
        return BOX_EDGE_FACES;
    }

    float[] points = new float[24];
    int vertexCount;
    /** Normal xyz, offset (n . p = d). */
    float[] planes = new float[24];
    int faceCount;
    int[] faceStart = BOX_START;
    int[] loops = BOX_LOOPS;
    private int[] hullStart = new int[8];
    float[] axes = new float[9];
    int axisCount;
    float[] edges = new float[9];
    int edgeCount;
    /** Per undirected edge: face, twin face, {@link #edges} index (Gauss-map arcs for {@link ConvexContacts}). */
    int[] edgeFaces = BOX_EDGE_FACES;
    int edgeFaceCount;
    float minX, minY, minZ, maxX, maxY, maxZ;
    /** Caller's tag (piece index). */
    public int tag;
    /** World axis-aligned box: {@link ConvexContacts} projects it from its bounds. */
    boolean aligned;
    /** Per pose: ranges on own face axes; (unit axis i x own edge j) axes + ranges, NaN x = parallel. */
    private boolean facesProjected, projected, restProjected, arcsPosed;
    /** Per pose, per {@link #edgeFaces} edge: negated face normals c, d and d x c ({@link ConvexContacts} as b). */
    float[] arcs = new float[108];
    boolean[] alignedMinkowski = new boolean[9];
    float[] faceRanges = new float[6];
    float[] crossAxes = new float[27];
    float[] crossRanges = new float[18];
    private final float[] range = new float[2];
    private final Vector3f scratch = new Vector3f();

    /** World axis-aligned box. */
    public Polytope box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        aligned = true;
        facesProjected = projected = restProjected = arcsPosed = false;
        vertexCount = 8;
        for (int i = 0; i < 8; i++) {
            points[i * 3] = (i & 1) == 0 ? minX : maxX;
            points[i * 3 + 1] = (i & 2) == 0 ? minY : maxY;
            points[i * 3 + 2] = (i & 4) == 0 ? minZ : maxZ;
        }
        faceCount = 6;
        faceStart = BOX_START;
        loops = BOX_LOOPS;
        float[] offsets = {-minX, maxX, -minY, maxY, -minZ, maxZ};
        for (int f = 0; f < 6; f++) {
            planes[f * 4] = BOX_NORMALS[f * 3];
            planes[f * 4 + 1] = BOX_NORMALS[f * 3 + 1];
            planes[f * 4 + 2] = BOX_NORMALS[f * 3 + 2];
            planes[f * 4 + 3] = offsets[f];
        }
        System.arraycopy(UNIT_AXES, 0, axes, 0, 9);
        axisCount = 3;
        System.arraycopy(UNIT_AXES, 0, edges, 0, 9);
        edgeCount = 3;
        edgeFaces = BOX_EDGE_FACES;
        edgeFaceCount = 12;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        return this;
    }

    /** Oriented box, half extents. */
    public Polytope box(float cx, float cy, float cz, Quaternionf rotation, float ex, float ey, float ez) {
        ensure(8, 6, 3, 3);
        aligned = false;
        facesProjected = projected = restProjected = arcsPosed = false;
        vertexCount = 8;
        for (int i = 0; i < 8; i++) {
            rotation.transform(scratch.set((i & 1) == 0 ? -ex : ex, (i & 2) == 0 ? -ey : ey, (i & 4) == 0 ? -ez : ez));
            setPoint(i, cx + scratch.x, cy + scratch.y, cz + scratch.z);
        }
        faceCount = 6;
        faceStart = BOX_START;
        loops = BOX_LOOPS;
        for (int f = 0; f < 6; f++) {
            rotation.transform(scratch.set(BOX_NORMALS[f * 3], BOX_NORMALS[f * 3 + 1], BOX_NORMALS[f * 3 + 2]));
            int v = BOX_LOOPS[BOX_START[f]];
            setPlane(f, scratch, v);
        }
        for (int k = 0; k < 3; k++) {
            rotation.transform(scratch.set(UNIT_AXES[k * 3], UNIT_AXES[k * 3 + 1], UNIT_AXES[k * 3 + 2]));
            axes[k * 3] = scratch.x;
            axes[k * 3 + 1] = scratch.y;
            axes[k * 3 + 2] = scratch.z;
            edges[k * 3] = scratch.x;
            edges[k * 3 + 1] = scratch.y;
            edges[k * 3 + 2] = scratch.z;
        }
        axisCount = 3;
        edgeCount = 3;
        edgeFaces = BOX_EDGE_FACES;
        edgeFaceCount = 12;
        bound();
        return this;
    }

    /** Hull points are relative to (cx, cy, cz) before rotation. */
    public Polytope hull(ConvexHull hull, float cx, float cy, float cz, Quaternionf rotation) {
        int vertices = hull.vertexCount(), faces = hull.faceCount();
        float[] hullAxes = hull.axes(), hullEdges = hull.edgeDirections();
        ensure(vertices, faces, hullAxes.length / 3, hullEdges.length / 3);
        aligned = false;
        facesProjected = projected = restProjected = arcsPosed = false;
        vertexCount = vertices;
        float[] local = hull.points();
        for (int i = 0; i < vertices; i++) {
            rotation.transform(scratch.set(local[i * 3], local[i * 3 + 1], local[i * 3 + 2]));
            setPoint(i, cx + scratch.x, cy + scratch.y, cz + scratch.z);
        }
        faceCount = faces;
        loops = hull.faceVertices();
        if (hullStart.length < faces + 1) {
            hullStart = new int[faces + 1];
        }
        faceStart = hullStart;
        float[] hullPlanes = hull.planes();
        for (int f = 0; f < faces; f++) {
            faceStart[f] = hull.faceStart(f);
            rotation.transform(scratch.set(hullPlanes[f * 4], hullPlanes[f * 4 + 1], hullPlanes[f * 4 + 2]));
            setPlane(f, scratch, loops[faceStart[f]]);
        }
        faceStart[faces] = hull.faceEnd(faces - 1);
        axisCount = rotate(hullAxes, axes, rotation);
        edgeCount = rotate(hullEdges, edges, rotation);
        edgeFaces = hull.edgeFaces();
        edgeFaceCount = edgeFaces.length / 3;
        bound();
        return this;
    }

    /** Triangle prism: faces top, bottom, three sides. */
    private static final int[][] PRISM_FACES = {{0, 1, 2}, {3, 5, 4}, {0, 3, 4, 1}, {1, 4, 5, 2}, {2, 5, 3, 0}};
    /** Per undirected prism edge: face, loop slot in it (as listed), twin face. */
    private static final int[] PRISM_EDGES = new int[27];

    static {
        int n = 0;
        for (int f = 0; f < PRISM_FACES.length; f++) {
            int[] face = PRISM_FACES[f];
            for (int k = 0; k < face.length; k++) {
                int a = face[k], b = face[(k + 1) % face.length];
                for (int g = f + 1; g < PRISM_FACES.length; g++) {
                    int[] other = PRISM_FACES[g];
                    for (int m = 0; m < other.length; m++) {
                        int c = other[m], d = other[(m + 1) % other.length];
                        if (c == a && d == b || c == b && d == a) {
                            PRISM_EDGES[n * 3] = f;
                            PRISM_EDGES[n * 3 + 1] = k;
                            PRISM_EDGES[n * 3 + 2] = g;
                            n++;
                        }
                    }
                }
            }
        }
    }
    private int[] ownLoops = new int[18];
    private final int[] ownStart = new int[6];
    private final int[] ownEdgeFaces = new int[27];
    /** faces(): direction index of face f's k-th listed edge at [f * 4 + k]. */
    private final int[] edgeSlotDirection = new int[20];

    /** Triangle t of a 9-float array, extruded depth along -normal (thin solid behind a surface triangle). */
    public Polytope prism(float[] triangles, int t, float ox, float oy, float oz, float depth) {
        // Edges: top/bottom copies of a short edge can round apart => 3 + 3 + 3 extrusions.
        ensure(6, 5, 5, 9);
        aligned = false;
        facesProjected = projected = restProjected = arcsPosed = false;
        vertexCount = 6;
        int o = t * 9;
        for (int c = 0; c < 3; c++) {
            setPoint(c, triangles[o + c * 3] - ox, triangles[o + c * 3 + 1] - oy, triangles[o + c * 3 + 2] - oz);
        }
        float ax = points[3] - points[0], ay = points[4] - points[1], az = points[5] - points[2];
        float bx = points[6] - points[0], by = points[7] - points[1], bz = points[8] - points[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float scale = depth / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        for (int c = 0; c < 3; c++) {
            setPoint(c + 3, points[c * 3] - nx * scale, points[c * 3 + 1] - ny * scale, points[c * 3 + 2] - nz * scale);
        }
        faces(PRISM_FACES);
        bound();
        return this;
    }

    /** Bounds only (never collided): the union of {@code shapes[0, count)}, for one-pass culls. */
    public Polytope enclose(List<Polytope> shapes, int count) {
        minX = minY = minZ = Float.MAX_VALUE;
        maxX = maxY = maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            Polytope s = shapes.get(i);
            minX = Math.min(minX, s.minX);
            minY = Math.min(minY, s.minY);
            minZ = Math.min(minZ, s.minZ);
            maxX = Math.max(maxX, s.maxX);
            maxY = Math.max(maxY, s.maxY);
            maxZ = Math.max(maxZ, s.maxZ);
        }
        vertexCount = 0;
        faceCount = 0;
        aligned = false;
        facesProjected = projected = restProjected = arcsPosed = false;
        return this;
    }

    /** {@link #boundsMeet} for a box not yet built. */
    public static boolean boundsMeet(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                     Polytope other, float margin) {
        return minX <= other.maxX + margin && other.minX <= maxX + margin
                && minY <= other.maxY + margin && other.minY <= maxY + margin
                && minZ <= other.maxZ + margin && other.minZ <= maxZ + margin;
    }

    /**
     * {@link #prism} then {@link #boundsMeet}, without the face build when the bounds miss: same floats as the build.
     * Meets => this holds the prism.
     */
    public boolean prismMeets(float[] triangles, int t, float ox, float oy, float oz, float depth, Polytope other,
                              float margin) {
        int o = t * 9;
        float x0 = triangles[o] - ox, y0 = triangles[o + 1] - oy, z0 = triangles[o + 2] - oz;
        float x1 = triangles[o + 3] - ox, y1 = triangles[o + 4] - oy, z1 = triangles[o + 5] - oz;
        float x2 = triangles[o + 6] - ox, y2 = triangles[o + 7] - oy, z2 = triangles[o + 8] - oz;
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float scale = depth / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float dx = nx * scale, dy = ny * scale, dz = nz * scale;
        float lx = Math.min(Math.min(x0, x1), x2), hx = Math.max(Math.max(x0, x1), x2);
        float ly = Math.min(Math.min(y0, y1), y2), hy = Math.max(Math.max(y0, y1), y2);
        float lz = Math.min(Math.min(z0, z1), z2), hz = Math.max(Math.max(z0, z1), z2);
        float minX = Math.min(lx, Math.min(Math.min(x0 - dx, x1 - dx), x2 - dx));
        float maxX = Math.max(hx, Math.max(Math.max(x0 - dx, x1 - dx), x2 - dx));
        float minY = Math.min(ly, Math.min(Math.min(y0 - dy, y1 - dy), y2 - dy));
        float maxY = Math.max(hy, Math.max(Math.max(y0 - dy, y1 - dy), y2 - dy));
        float minZ = Math.min(lz, Math.min(Math.min(z0 - dz, z1 - dz), z2 - dz));
        float maxZ = Math.max(hz, Math.max(Math.max(z0 - dz, z1 - dz), z2 - dz));
        if (!(minX <= other.maxX + margin && other.minX <= maxX + margin
                && minY <= other.maxY + margin && other.minY <= maxY + margin
                && minZ <= other.maxZ + margin && other.minZ <= maxZ + margin)) {
            return false;
        }
        prism(triangles, t, ox, oy, oz, depth);
        return true;
    }

    /** Planes wound outward (centroid test), unique face normals as axes, unique edge directions. */
    private void faces(int[][] faces) {
        float mx = 0, my = 0, mz = 0;
        for (int i = 0; i < vertexCount; i++) {
            mx += points[i * 3];
            my += points[i * 3 + 1];
            mz += points[i * 3 + 2];
        }
        mx /= vertexCount;
        my /= vertexCount;
        mz /= vertexCount;
        faceCount = faces.length;
        loops = ownLoops;
        faceStart = ownStart;
        axisCount = 0;
        edgeCount = 0;
        int cursor = 0;
        for (int f = 0; f < faces.length; f++) {
            int[] face = faces[f];
            float nx = 0, ny = 0, nz = 0;
            for (int k = 0; k < face.length; k++) {
                int a = face[k] * 3, b = face[(k + 1) % face.length] * 3;
                nx += (points[a + 1] - points[b + 1]) * (points[a + 2] + points[b + 2]);
                ny += (points[a + 2] - points[b + 2]) * (points[a] + points[b]);
                nz += (points[a] - points[b]) * (points[a + 1] + points[b + 1]);
            }
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= length;
            ny /= length;
            nz /= length;
            int first = face[0] * 3;
            boolean inward = nx * (mx - points[first]) + ny * (my - points[first + 1]) + nz * (mz - points[first + 2]) > 0;
            if (inward) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            faceStart[f] = cursor;
            for (int k = 0; k < face.length; k++) {
                loops[cursor++] = inward ? face[face.length - 1 - k] : face[k];
            }
            setPlane(f, scratch.set(nx, ny, nz), face[0]);
            axisCount = unique(axes, axisCount, nx, ny, nz);
            for (int k = 0; k < face.length; k++) {
                int a = face[k] * 3, b = face[(k + 1) % face.length] * 3;
                float ex = points[b] - points[a], ey = points[b + 1] - points[a + 1], ez = points[b + 2] - points[a + 2];
                float el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                int direction = uniqueIndex(edges, edgeCount, ex / el, ey / el, ez / el);
                edgeSlotDirection[f * 4 + k] = direction;
                edgeCount = Math.max(edgeCount, direction + 1);
            }
        }
        faceStart[faces.length] = cursor;
        edgeFaces = ownEdgeFaces;
        edgeFaceCount = 9;
        for (int e = 0; e < 9; e++) {
            ownEdgeFaces[e * 3] = PRISM_EDGES[e * 3];
            ownEdgeFaces[e * 3 + 1] = PRISM_EDGES[e * 3 + 2];
            ownEdgeFaces[e * 3 + 2] = edgeSlotDirection[PRISM_EDGES[e * 3] * 4 + PRISM_EDGES[e * 3 + 1]];
        }
    }

    /** {@link #unique}'s slot: first kept within 0.9999, else appended at {@code count}. */
    private static int uniqueIndex(float[] into, int count, float x, float y, float z) {
        for (int k = 0; k < count; k++) {
            if (Math.abs(into[k * 3] * x + into[k * 3 + 1] * y + into[k * 3 + 2] * z) > 0.9999f) {
                return k;
            }
        }
        into[count * 3] = x;
        into[count * 3 + 1] = y;
        into[count * 3 + 2] = z;
        return count;
    }

    private static int unique(float[] into, int count, float x, float y, float z) {
        for (int k = 0; k < count; k++) {
            if (Math.abs(into[k * 3] * x + into[k * 3 + 1] * y + into[k * 3 + 2] * z) > 0.9999f) {
                return count;
            }
        }
        into[count * 3] = x;
        into[count * 3 + 1] = y;
        into[count * 3 + 2] = z;
        return count + 1;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public float[] vertex(int i) {
        return new float[]{points[i * 3], points[i * 3 + 1], points[i * 3 + 2]};
    }

    public int faceCount() {
        return faceCount;
    }

    /** Face f's plane: normal, offset (n . p = d). */
    public void plane(int f, float[] out) {
        System.arraycopy(planes, f * 4, out, 0, 4);
    }

    /** Face f's loop: vertex indices [faceStart(f), faceStart(f + 1)) of {@link #loopVertex}. */
    public int faceStart(int f) {
        return faceStart[f];
    }

    public int loopVertex(int k) {
        return loops[k];
    }

    public Polytope translate(float dx, float dy, float dz) {
        facesProjected = projected = restProjected = arcsPosed = false;
        for (int i = 0; i < vertexCount; i++) {
            points[i * 3] += dx;
            points[i * 3 + 1] += dy;
            points[i * 3 + 2] += dz;
        }
        for (int f = 0; f < faceCount; f++) {
            planes[f * 4 + 3] += planes[f * 4] * dx + planes[f * 4 + 1] * dy + planes[f * 4 + 2] * dz;
        }
        minX += dx;
        maxX += dx;
        minY += dy;
        maxY += dy;
        minZ += dz;
        maxZ += dz;
        return this;
    }

    public boolean boundsMeet(Polytope other, float margin) {
        return minX <= other.maxX + margin && other.minX <= maxX + margin
                && minY <= other.maxY + margin && other.minY <= maxY + margin
                && minZ <= other.maxZ + margin && other.minZ <= maxZ + margin;
    }

    /** min, max of vertices along (x, y, z) into out[0..1]. */
    void project(float x, float y, float z, float[] out) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        float[] p = points;
        for (int i = 0, end = vertexCount * 3; i < end; i += 3) {
            float d = p[i] * x + p[i + 1] * y + p[i + 2] * z;
            // Not Math.min/max: NaN/-0 handling, ~2x slower; differs only in the sign of a zero.
            if (d < min) {
                min = d;
            }
            if (d > max) {
                max = d;
            }
        }
        out[0] = min;
        out[1] = max;
    }

    /** Fills {@link #faceRanges} once per pose. */
    void projectFaces() {
        if (facesProjected) {
            return;
        }
        facesProjected = true;
        if (faceRanges.length < axisCount * 2) {
            faceRanges = new float[axisCount * 2];
        }
        for (int k = 0; k < axisCount; k++) {
            project(axes[k * 3], axes[k * 3 + 1], axes[k * 3 + 2], range);
            faceRanges[k * 2] = range[0];
            faceRanges[k * 2 + 1] = range[1];
        }
    }

    /** Fills {@link #arcs} once per pose. */
    void poseArcs() {
        if (arcsPosed) {
            return;
        }
        arcsPosed = true;
        if (arcs.length < edgeFaceCount * 9) {
            arcs = new float[edgeFaceCount * 9];
        }
        for (int e = 0; e < edgeFaceCount; e++) {
            int g0 = edgeFaces[e * 3] * 4, g1 = edgeFaces[e * 3 + 1] * 4, o = e * 9;
            float cx = -planes[g0], cy = -planes[g0 + 1], cz = -planes[g0 + 2];
            float dx = -planes[g1], dy = -planes[g1 + 1], dz = -planes[g1 + 2];
            arcs[o] = cx;
            arcs[o + 1] = cy;
            arcs[o + 2] = cz;
            arcs[o + 3] = dx;
            arcs[o + 4] = dy;
            arcs[o + 5] = dz;
            arcs[o + 6] = dy * cz - dz * cy;
            arcs[o + 7] = dz * cx - dx * cz;
            arcs[o + 8] = dx * cy - dy * cx;
        }
    }

    /**
     * {@link #projectFaces}, then {@link #crossAxes} (NaN x = parallel) once per pose; {@link #crossRanges} only for
     * (unit axis, own edge) pairs that are a Minkowski face against a world box (Gauss map independent of its size
     * and place), rest by {@link #projectRest}.
     */
    void projectAxes() {
        projectFaces();
        if (projected) {
            return;
        }
        projected = true;
        if (alignedMinkowski.length < 3 * edgeCount) {
            alignedMinkowski = new boolean[3 * edgeCount];
        }
        ConvexContacts.markMinkowski(BOX_PLANES, BOX_EDGE_FACES, 12, 3, this, alignedMinkowski);
        if (crossRanges.length < edgeCount * 6) {
            crossAxes = new float[edgeCount * 9];
            crossRanges = new float[edgeCount * 6];
        }
        for (int i = 0; i < 3; i++) {
            float ax = UNIT_AXES[i * 3], ay = UNIT_AXES[i * 3 + 1], az = UNIT_AXES[i * 3 + 2];
            for (int j = 0; j < edgeCount; j++) {
                float bx = edges[j * 3], by = edges[j * 3 + 1], bz = edges[j * 3 + 2];
                float x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
                float length = (float) Math.sqrt(x * x + y * y + z * z);
                int c = i * edgeCount + j;
                if (length < ConvexContacts.PARALLEL) {
                    crossAxes[c * 3] = Float.NaN;
                    continue;
                }
                x /= length;
                y /= length;
                z /= length;
                crossAxes[c * 3] = x;
                crossAxes[c * 3 + 1] = y;
                crossAxes[c * 3 + 2] = z;
                if (alignedMinkowski[c]) {
                    project(x, y, z, range);
                    crossRanges[c * 2] = range[0];
                    crossRanges[c * 2 + 1] = range[1];
                }
            }
        }
    }

    /** {@link #crossRanges} of the pairs {@link #projectAxes} skipped; once per pose. */
    void projectRest() {
        if (restProjected) {
            return;
        }
        restProjected = true;
        for (int c = 0, count = 3 * edgeCount; c < count; c++) {
            if (!alignedMinkowski[c] && !Float.isNaN(crossAxes[c * 3])) {
                project(crossAxes[c * 3], crossAxes[c * 3 + 1], crossAxes[c * 3 + 2], range);
                crossRanges[c * 2] = range[0];
                crossRanges[c * 2 + 1] = range[1];
            }
        }
    }

    private void setPoint(int i, float x, float y, float z) {
        points[i * 3] = x;
        points[i * 3 + 1] = y;
        points[i * 3 + 2] = z;
    }

    private void setPlane(int f, Vector3f normal, int vertex) {
        planes[f * 4] = normal.x;
        planes[f * 4 + 1] = normal.y;
        planes[f * 4 + 2] = normal.z;
        planes[f * 4 + 3] = normal.x * points[vertex * 3] + normal.y * points[vertex * 3 + 1]
                + normal.z * points[vertex * 3 + 2];
    }

    private int rotate(float[] source, float[] dest, Quaternionf rotation) {
        int count = source.length / 3;
        for (int k = 0; k < count; k++) {
            rotation.transform(scratch.set(source[k * 3], source[k * 3 + 1], source[k * 3 + 2]));
            dest[k * 3] = scratch.x;
            dest[k * 3 + 1] = scratch.y;
            dest[k * 3 + 2] = scratch.z;
        }
        return count;
    }

    private void bound() {
        minX = minY = minZ = Float.MAX_VALUE;
        maxX = maxY = maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vertexCount; i++) {
            float x = points[i * 3], y = points[i * 3 + 1], z = points[i * 3 + 2];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
    }

    private void ensure(int vertices, int faces, int axisCount, int edgeCount) {
        if (points.length < vertices * 3) {
            points = Arrays.copyOf(points, vertices * 3);
        }
        if (planes.length < faces * 4) {
            planes = Arrays.copyOf(planes, faces * 4);
        }
        if (axes.length < axisCount * 3) {
            axes = Arrays.copyOf(axes, axisCount * 3);
        }
        if (edges.length < edgeCount * 3) {
            edges = Arrays.copyOf(edges, edgeCount * 3);
        }
    }
}
