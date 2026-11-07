package com.wf.wflib.soldier;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * The route search's view of the world as a copy ({@link SoldierSections}): safe on a worker. A cell whose section has no
 * copy reads as solid, so a search never walks through what it cannot see.
 */
final class SnapshotTerrain implements Terrain {
    /** Cell byte at (x, y, z) or -1; built over {@link Cells} so tests can feed a synthetic world. */
    interface Cells {
        int at(int x, int y, int z);
    }

    private final Cells cells;
    /** Set when a read found a missing section: the result is partial and should be treated as a hint. */
    boolean incomplete;

    SnapshotTerrain(Cells cells) {
        this.cells = cells;
    }

    private int cell(int x, int y, int z) {
        int c = cells.at(x, y, z);
        if (c < 0) {
            incomplete = true;
            return SoldierSections.HEIGHT_MASK & 16;         // unknown = a full block
        }
        return c;
    }

    private static double top(int cell) {
        return (cell & SoldierSections.HEIGHT_MASK) / 16.0;
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public boolean walkable(BlockPos feet) {
        int x = feet.getX(), y = feet.getY(), z = feet.getZ();
        if ((cell(x, y + 1, z) & SoldierSections.HEIGHT_MASK) != 0) return false;
        int here = cell(x, y, z);
        if ((here & SoldierSections.HEIGHT_MASK) != 0) {
            return top(here) <= 0.6 && (cell(x, y + 2, z) & SoldierSections.HEIGHT_MASK) == 0;
        }
        int floor = cell(x, y - 1, z);
        return (floor & SoldierSections.HEIGHT_MASK) != 0 && top(floor) >= 0.5;
    }

    @Override
    public boolean clear(BlockPos pos) {
        return (cell(pos.getX(), pos.getY(), pos.getZ()) & SoldierSections.HEIGHT_MASK) == 0;
    }

    @Override
    public boolean exposed(BlockPos cell, List<Vec3> threats, double h) {
        Vec3 at = Vec3.atBottomCenterOf(cell).add(0, h, 0);
        for (Vec3 t : threats) if (sees(t, at)) return true;
        return false;
    }

    /** A clear line between two points, ignoring thin panes and bars (smoke is not considered off-thread). */
    boolean sees(Vec3 from, Vec3 to) {
        return !blocked(from, to);
    }

    /**
     * Voxel traversal (Amanatides-Woo): the ray is blocked when it passes through a solid cell below that cell's collision
     * height. The cell holding {@code from} is skipped (an eye is inside its own cell).
     */
    boolean blocked(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) return false;
        int x = Mth.floor(from.x), y = Mth.floor(from.y), z = Mth.floor(from.z);
        int ex = Mth.floor(to.x), ey = Mth.floor(to.y), ez = Mth.floor(to.z);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx), tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy),
                tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tx = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? x + 1 - from.x : from.x - x)) * tdx;
        double ty = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? y + 1 - from.y : from.y - y)) * tdy;
        double tz = dz == 0 ? Double.MAX_VALUE : ((dz > 0 ? z + 1 - from.z : from.z - z)) * tdz;
        double enter = 0;
        for (int guard = 0; guard < 400; guard++) {
            double exit = Math.min(1, Math.min(tx, Math.min(ty, tz)));
            if (!(x == Mth.floor(from.x) && y == Mth.floor(from.y) && z == Mth.floor(from.z))) {
                int c = cells.at(x, y, z);
                if (c < 0) return true;                                     // unseen: assume a wall
                int height = c & SoldierSections.HEIGHT_MASK;
                if (height != 0 && (c & SoldierSections.THIN) == 0) {
                    double yIn = from.y + dy * enter, yOut = from.y + dy * exit;
                    if (Math.min(yIn, yOut) < y + height / 16.0) return true;
                }
            }
            if (x == ex && y == ey && z == ez) return false;
            enter = exit;
            if (exit >= 1) return false;
            if (tx <= ty && tx <= tz) {
                x += sx;
                tx += tdx;
            } else if (ty <= tz) {
                y += sy;
                ty += tdy;
            } else {
                z += sz;
                tz += tdz;
            }
        }
        return false;
    }
}
