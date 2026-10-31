package com.wf.wflib.physics.api;


import java.util.Arrays;

/**
 * One section's hill surface: column floors (box tops at the column centre with >= {@link #CLEARANCE} above), each
 * blurred 1-2-1 over its 3x3 neighbours, block corners = mean of their 4 columns, clamped to {@link #REACH} of each
 * column's floor. Neighbour = connected floor: within {@link #CLIMB}, air gaps overlapping >= CLEARANCE; none => the
 * floor itself (walls, cliffs, cave ceilings stay sharp). Triangles only where a corner leaves the floor.
 */
public final class SurfaceBake {

    public static final double CLIMB = 1.0;
    /** Blocks: blur + corner mean reach 2 columns; neighbour floors within CLIMB. */
    public static final int PAD = 2, PAD_Y = 2;
    /** Max cut / fill at a corner, blocks. */
    static final float REACH = 0.5f;
    /** Gap above a box top that makes it a floor; min air overlap joining neighbour floors, blocks. */
    static final float CLEARANCE = 0.5f;
    private static final int SPAN = 16 + 2 * PAD, COLUMNS = SPAN * SPAN;
    private static final float FLAT = 1.0e-3f, EPS = 1.0e-6f;
    /** n.y above: support (ground, skirt never drops it). */
    public static final float SUPPORT_NORMAL = 0.7f;
    /** Support <=> b^2 + c^2 < STEEP for plane y = a + b x + c z. */
    private static final double STEEP = 1 / (SUPPORT_NORMAL * SUPPORT_NORMAL) - 1;
    /** Neighbour d: (DX[d], DZ[d]); d = (dx + 1) * 3 + dz + 1, minus 1 past the centre. */
    private static final int[] DX = {-1, -1, -1, 0, 0, 1, 1, 1}, DZ = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] WEIGHT = {1, 2, 1, 2, 2, 1, 2, 1};
    /** Corner c = (c & 1, c >> 1): the 3 other columns sharing it. */
    private static final int[][] CORNER = {{0, 1, 3}, {3, 5, 6}, {1, 2, 4}, {4, 6, 7}};

    private SurfaceBake() {
    }

    /** Section (sx, sy, sz); boxes: every collision box meeting the padded section, world coordinates. */
    public static SurfaceSection bake(int sx, int sy, int sz, BoxBuffer boxes) {
        int[] first = new int[COLUMNS + 1];
        float[][] floors = floors(sx * 16 - PAD, sy * 16, sz * 16 - PAD, boxes, first);
        float[] y = floors[0], ceiling = floors[1];
        int[] near = neighbours(first, y, ceiling);
        float[] smooth = smooth(first, y, near);
        return surface(sx, sy, sz, first, y, near, smooth);
    }

    /**
     * Floors {top, ceiling} (section-local y; ceiling = next box bottom, +inf none) for every padded column
     * (x * SPAN + z), ascending; column k's floors = [first[k], first[k + 1]).
     */
    private static float[][] floors(int ox, int base, int oz, BoxBuffer boxes, int[] first) {
        int size = boxes.size();
        int[] range = new int[size * 4];
        int[] start = new int[COLUMNS + 1];
        for (int i = 0; i < size; i++) {
            // Column centres inside the box: thin posts and panes still hold their column.
            int x0 = Math.max(0, (int) Math.ceil(boxes.minX(i) - ox - 0.5));
            int x1 = Math.min(SPAN - 1, (int) Math.ceil(boxes.maxX(i) - ox - 0.5) - 1);
            int z0 = Math.max(0, (int) Math.ceil(boxes.minZ(i) - oz - 0.5));
            int z1 = Math.min(SPAN - 1, (int) Math.ceil(boxes.maxZ(i) - oz - 0.5) - 1);
            range[i * 4] = x0;
            range[i * 4 + 1] = x1;
            range[i * 4 + 2] = z0;
            range[i * 4 + 3] = z1;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    start[x * SPAN + z + 1]++;
                }
            }
        }
        for (int k = 0; k < COLUMNS; k++) {
            start[k + 1] += start[k];
        }
        int total = start[COLUMNS];
        float[] low = new float[total], high = new float[total];
        int[] cursor = Arrays.copyOf(start, COLUMNS);
        for (int i = 0; i < size; i++) {
            float l = (float) (boxes.minY(i) - base), h = (float) (boxes.maxY(i) - base);
            for (int x = range[i * 4]; x <= range[i * 4 + 1]; x++) {
                for (int z = range[i * 4 + 2]; z <= range[i * 4 + 3]; z++) {
                    int c = cursor[x * SPAN + z]++;
                    low[c] = l;
                    high[c] = h;
                }
            }
        }
        float[] y = new float[total], ceiling = new float[total];
        int n = 0;
        for (int k = 0; k < COLUMNS; k++) {
            first[k] = n;
            int s = start[k], e = start[k + 1];
            if (s == e) {
                continue;
            }
            // Insertion sort by bottom: a column holds few boxes.
            for (int i = s + 1; i < e; i++) {
                float l = low[i], h = high[i];
                int j = i - 1;
                for (; j >= s && low[j] > l; j--) {
                    low[j + 1] = low[j];
                    high[j + 1] = high[j];
                }
                low[j + 1] = l;
                high[j + 1] = h;
            }
            float top = high[s];
            for (int i = s + 1; i <= e; i++) {
                float next = i < e ? low[i] : Float.POSITIVE_INFINITY;
                if (i < e && next <= top + CLEARANCE - EPS) {
                    top = Math.max(top, high[i]);
                    continue;
                }
                if (top >= -PAD_Y && top < 16 + PAD_Y) {
                    y[n] = top;
                    ceiling[n] = next;
                    n++;
                }
                if (i < e) {
                    top = high[i];
                }
            }
        }
        first[COLUMNS] = n;
        return new float[][]{y, ceiling};
    }

    /** near[i * 8 + d]: floor of column i + d nearest floor i, connected; -1 none. Columns 1..SPAN-2 only. */
    private static int[] neighbours(int[] first, float[] y, float[] ceiling) {
        int[] near = new int[first[COLUMNS] * 8];
        Arrays.fill(near, -1);
        for (int x = 1; x < SPAN - 1; x++) {
            for (int z = 1; z < SPAN - 1; z++) {
                for (int i = first[x * SPAN + z], end = first[x * SPAN + z + 1]; i < end; i++) {
                    float f = y[i], roof = ceiling[i];
                    for (int d = 0; d < 8; d++) {
                        int column = (x + DX[d]) * SPAN + z + DZ[d];
                        float best = Float.MAX_VALUE;
                        for (int j = first[column], to = first[column + 1]; j < to; j++) {
                            float gap = Math.abs(y[j] - f);
                            if (gap <= CLIMB + EPS && gap < best
                                    && Math.min(roof, ceiling[j]) - Math.max(f, y[j]) >= CLEARANCE - EPS) {
                                best = gap;
                                near[i * 8 + d] = j;
                            }
                        }
                    }
                }
            }
        }
        return near;
    }

    /** 1-2-1 blur of each floor over its 3x3; an unconnected neighbour counts as the floor. Columns 1..SPAN-2. */
    private static float[] smooth(int[] first, float[] y, int[] near) {
        float[] smooth = new float[first[COLUMNS]];
        for (int x = 1; x < SPAN - 1; x++) {
            for (int i = first[x * SPAN + 1], end = first[x * SPAN + SPAN - 1]; i < end; i++) {
                float own = y[i], sum = 4 * own;
                for (int d = 0; d < 8; d++) {
                    int j = near[i * 8 + d];
                    sum += WEIGHT[d] * (j < 0 ? own : y[j]);
                }
                smooth[i] = sum / 16;
            }
        }
        return smooth;
    }

    private static SurfaceSection surface(int sx, int sy, int sz, int[] first, float[] y, int[] near, float[] smooth) {
        int capacity = 2 * (first[(PAD + 16) * SPAN] - first[PAD * SPAN]);
        float[] planes = new float[capacity * 4];
        short[] meta = new short[capacity];
        char[] cells = new char[257];
        float[] corner = new float[4];
        int count = 0;
        for (int x = PAD; x < PAD + 16; x++) {
            for (int z = PAD; z < PAD + 16; z++) {
                int cell = (x - PAD) * 16 + z - PAD;
                cells[cell] = (char) count;
                for (int i = first[x * SPAN + z], end = first[x * SPAN + z + 1]; i < end; i++) {
                    float floor = y[i];
                    if (floor < 0 || floor >= 16) {
                        continue;
                    }
                    float own = smooth[i], low = floor;
                    boolean flat = true;
                    for (int c = 0; c < 4; c++) {
                        float sum = own, highest = floor, lowest = floor;
                        for (int d : CORNER[c]) {
                            int j = near[i * 8 + d];
                            if (j < 0) {
                                sum += own;
                            } else {
                                sum += smooth[j];
                                highest = Math.max(highest, y[j]);
                                lowest = Math.min(lowest, y[j]);
                            }
                        }
                        corner[c] = Math.max(highest - REACH, Math.min(lowest + REACH, sum / 4));
                        low = Math.min(low, corner[c]);
                        flat &= Math.abs(corner[c] - floor) <= FLAT;
                    }
                    if (flat) {
                        continue;
                    }
                    float y0 = corner[0], y1 = corner[1], y2 = corner[2], y3 = corner[3], cut = floor - low;
                    // Diagonal along the smaller height difference.
                    if (Math.abs(y0 - y3) <= Math.abs(y1 - y2)) {
                        count = triangle(planes, meta, count, cell, SurfaceSection.Z_OVER_X, y0, y3 - y2, y2 - y0, cut);
                        count = triangle(planes, meta, count, cell, SurfaceSection.X_OVER_Z, y0, y1 - y0, y3 - y1, cut);
                    } else {
                        count = triangle(planes, meta, count, cell, SurfaceSection.NEAR, y0, y1 - y0, y2 - y0, cut);
                        count = triangle(planes, meta, count, cell, SurfaceSection.FAR, y1 + y2 - y3, y3 - y2, y3 - y1, cut);
                    }
                }
            }
        }
        cells[256] = (char) count;
        return count == 0 ? SurfaceSection.empty(sx, sy, sz)
                : new SurfaceSection(sx, sy, sz, Arrays.copyOf(planes, count * 4), Arrays.copyOf(meta, count), cells);
    }

    /** Support triangles only (normal y > {@link #SUPPORT_NORMAL}); steeper never makes a contact. */
    private static int triangle(float[] planes, short[] meta, int count, int cell, int kind, float a, float b, float c, float cut) {
        if (b * b + c * c >= STEEP) {
            return count;
        }
        planes[count * 4] = a;
        planes[count * 4 + 1] = b;
        planes[count * 4 + 2] = c;
        planes[count * 4 + 3] = cut;
        meta[count] = (short) (cell << 2 | kind);
        return count + 1;
    }
}
