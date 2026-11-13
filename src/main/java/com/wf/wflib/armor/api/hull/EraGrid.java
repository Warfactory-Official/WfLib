package com.wf.wflib.armor.api.hull;

import org.joml.Vector3f;

/**
 * Cell of an integral ERA array under a rest-frame point. The plate's brick grid tiles its surface plane:
 * {@code CELL_WIDTH x CELL_HEIGHT} metres, {@code columns x rows} cells, wrapping, so nearby points are neighbouring
 * cells and one array of {@code n} cells covers a plate of any size.
 */
public final class EraGrid {

    public static final float CELL_WIDTH = 0.40f;
    public static final float CELL_HEIGHT = 0.25f;

    private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(() -> new float[2]);

    private EraGrid() {
    }

    public static int columns(int cells) {
        return (int) Math.ceil(Math.sqrt(cells));
    }

    public static int rows(int cells) {
        return (cells + columns(cells) - 1) / columns(cells);
    }

    /** Surface-plane coordinates of {@code p} in cell sides: {@code out[0]} across, {@code out[1]} up the plate. */
    private static void plane(Vector3f p, Vector3f n, float[] out) {
        float ux, uy, uz;
        if (Math.abs(n.y) > 0.9f) {
            ux = -n.y;
            uy = n.x;
            uz = 0;
        } else {
            ux = n.z;
            uy = 0;
            uz = -n.x;
        }
        float len = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= len;
        uy /= len;
        uz /= len;
        float vx = n.y * uz - n.z * uy, vy = n.z * ux - n.x * uz, vz = n.x * uy - n.y * ux;
        out[0] = (p.x * ux + p.y * uy + p.z * uz) / CELL_WIDTH;
        out[1] = (p.x * vx + p.y * vy + p.z * vz) / CELL_HEIGHT;
    }

    /** Cell index {@code 0..cells-1} at rest point {@code p} on a plate of rest normal {@code n}. */
    public static int cell(Vector3f p, Vector3f n, int cells) {
        float[] uv = SCRATCH.get();
        plane(p, n, uv);
        int columns = columns(cells), rows = rows(cells);
        int cu = Math.floorMod((int) Math.floor(uv[0]), columns);
        int cv = Math.floorMod((int) Math.floor(uv[1]), rows);
        return (cv * columns + cu) % cells;
    }

    /** Distance of {@code p} to the nearest edge of its cell, as a share of the cell side (0 edge .. 0.5 centre). */
    public static float edge(Vector3f p, Vector3f n) {
        float[] uv = SCRATCH.get();
        plane(p, n, uv);
        float fu = uv[0] - (float) Math.floor(uv[0]), fv = uv[1] - (float) Math.floor(uv[1]);
        return Math.min(Math.min(fu, 1 - fu), Math.min(fv, 1 - fv));
    }

    /**
     * Cells touching {@code cell} (8-neighbourhood, wrapping), without it, without repeats.
     *
     * @param out room for 8
     * @return count written
     */
    public static int neighbours(int cell, int cells, int[] out) {
        int columns = columns(cells), rows = rows(cells);
        int cu = cell % columns, cv = cell / columns;
        int n = 0;
        for (int dv = -1; dv <= 1; dv++) {
            for (int du = -1; du <= 1; du++) {
                if (du == 0 && dv == 0) {
                    continue;
                }
                int idx = (Math.floorMod(cv + dv, rows) * columns + Math.floorMod(cu + du, columns)) % cells;
                if (idx == cell) {
                    continue;
                }
                boolean seen = false;
                for (int i = 0; i < n; i++) {
                    seen |= out[i] == idx;
                }
                if (!seen) {
                    out[n++] = idx;
                }
            }
        }
        return n;
    }
}
