package com.wf.wflib.physics.api;

/** {@link SurfaceBake} output for one section. Immutable: read by island tasks. */
public final class SurfaceSection {

    /** Triangle kinds: which half of its cell (fx, fz in [0, 1]). Diagonal 0-3: fz >= fx, fx >= fz; diagonal 1-2: fx + fz <= 1, >= 1. */
    static final int Z_OVER_X = 0, X_OVER_Z = 1, NEAR = 2, FAR = 3;
    /** Per kind: vertices (fx, fz), wound up. */
    private static final int[][] VERTICES = {{0, 0, 0, 1, 1, 1}, {0, 0, 1, 1, 1, 0}, {1, 0, 0, 0, 0, 1}, {1, 0, 0, 1, 1, 1}};
    private static final char[] NONE = new char[257];
    private static final double SLACK = 0.05, EPS = 1.0e-6;
    /** Hull point under the hill by more than this => hull beneath it (cave ceiling underside), blocks. */
    private static final double BENEATH = 0.25;

    public final int sx, sy, sz;
    /**
     * Per triangle, stride 4: plane y = a + b fx + c fz over its cell (section-local y; world floats lose the sub-block
     * at 1e7), crest cut (column floor minus lowest corner). Fields read together => one 16-byte record.
     */
    private final float[] planes;
    /** Per triangle: cell (x * 16 + z) << 2 | kind. */
    private final short[] meta;
    /** Cell k's triangles: [cells[k], cells[k + 1]). */
    private final char[] cells;

    SurfaceSection(int sx, int sy, int sz, float[] planes, short[] meta, char[] cells) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.planes = planes;
        this.meta = meta;
        this.cells = cells;
    }

    static SurfaceSection empty(int sx, int sy, int sz) {
        return new SurfaceSection(sx, sy, sz, new float[0], new short[0], NONE);
    }

    public int triangleCount() {
        return meta.length;
    }

    /** First triangle over block column (x, z), world; {@link #end} == first outside this section. */
    public int first(int x, int z) {
        int lx = x - sx * 16, lz = z - sz * 16;
        return lx < 0 || lz < 0 || lx > 15 || lz > 15 ? 0 : cells[lx * 16 + lz];
    }

    public int end(int x, int z) {
        int lx = x - sx * 16, lz = z - sz * 16;
        return lx < 0 || lz < 0 || lx > 15 || lz > 15 ? 0 : cells[lx * 16 + lz + 1];
    }

    /** Triangle t's vertices into out[0..9), section-local, wound up. */
    public void vertices(int t, float[] out) {
        int m = meta[t], cell = m >> 2;
        int[] v = VERTICES[m & 3];
        float a = planes[t * 4], b = planes[t * 4 + 1], c = planes[t * 4 + 2];
        for (int k = 0; k < 3; k++) {
            int fx = v[k * 2], fz = v[k * 2 + 1];
            out[k * 3] = (cell >> 4) + fx;
            out[k * 3 + 1] = a + b * fx + c * fz;
            out[k * 3 + 2] = (cell & 15) + fz;
        }
    }

    /**
     * World block point inside a triangle's cover band (under it by <= CLIMB: toe fill, buried step face; over it by <=
     * its cut: crest corner) touched from above (hull point >= hill - BENEATH). Blocks there are the hill's.
     */
    public boolean covers(double x, double y, double z, double hullY) {
        int lx = (int) Math.floor(x) - sx * 16, lz = (int) Math.floor(z) - sz * 16;
        if (lx < 0 || lz < 0 || lx > 15 || lz > 15) {
            return false;
        }
        for (int t = cells[lx * 16 + lz], end = cells[lx * 16 + lz + 1]; t < end; t++) {
            double h = height(t, x, z);
            if (y >= h - SurfaceBake.CLIMB - SLACK && y <= h + planes[t * 4 + 3] + SLACK && hullY >= h - BENEATH) {
                return true;
            }
        }
        return false;
    }

    /** Triangle t's world height over world (px, pz) inside its xz projection, NaN outside. */
    public double height(int t, double px, double pz) {
        int m = meta[t], cell = m >> 2;
        double fx = px - sx * 16 - (cell >> 4), fz = pz - sz * 16 - (cell & 15);
        if (fx < -EPS || fz < -EPS || fx > 1 + EPS || fz > 1 + EPS) {
            return Double.NaN;
        }
        boolean inside = switch (m & 3) {
            case Z_OVER_X -> fz >= fx - EPS;
            case X_OVER_Z -> fx >= fz - EPS;
            case NEAR -> fx + fz <= 1 + EPS;
            default -> fx + fz >= 1 - EPS;
        };
        return inside ? sy * 16 + planes[t * 4] + planes[t * 4 + 1] * fx + planes[t * 4 + 2] * fz : Double.NaN;
    }
}
