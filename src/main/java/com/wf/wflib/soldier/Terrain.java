package com.wf.wflib.soldier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a route search asks of the world. {@link LevelTerrain} reads the live level (tick thread only);
 * {@link SnapshotTerrain} reads copies of its blocks and is safe on a worker.
 */
interface Terrain {
    /** The live world (true) or a copy read by a worker: only live searches count against the tick's budget. */
    default boolean live() {
        return true;
    }

    /** Feet cell standable: support below, feet low, head clear. */
    boolean walkable(BlockPos feet);

    /** Nothing solid in the cell. */
    boolean clear(BlockPos pos);

    /** Some threat eye sees a body standing at {@code cell} at height {@code h}. */
    boolean exposed(BlockPos cell, List<Vec3> threats, double h);
}
