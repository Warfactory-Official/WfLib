package com.wf.wflib.physics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Top of the tallest collision box in a block cell; NaN when it holds none. */
@FunctionalInterface
public interface CellTops {

    double top(int x, int y, int z);

    /** Drivable surface over floor cell (x, y, z) at (px, pz): its top (cell above empty), NaN otherwise. */
    default double surface(int x, int y, int z, double px, double pz) {
        double top = top(x, y, z);
        return !Double.isNaN(top) && Double.isNaN(top(x, y + 1, z)) ? top : Double.NaN;
    }

    /** Pinned snapshot: what a solve may read off-thread. */
    static CellTops snapshot(ChunkCollisionCache.Cursor cursor) {
        return (x, y, z) -> {
            double top = cursor.collisionTop(x, y, z);
            return top > y ? top : Double.NaN;
        };
    }

    /** Live level: tick or client thread only. */
    static CellTops live(Level level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            pos.set(x, y, z);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            return shape.isEmpty() ? Double.NaN : y + shape.max(Direction.Axis.Y);
        };
    }

}
