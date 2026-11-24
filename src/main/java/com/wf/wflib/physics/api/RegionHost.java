package com.wf.wflib.physics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Region-threaded tick host: each region ticks its entities on its own thread, then calls
 * {@link PhysicsStep#regionTickEnd} there, inside its region scope. Installed by {@link PhysicsStep#installRegionHost}.
 */
public interface RegionHost {

    /** Region bound to the calling thread, -1 outside a region scope (main thread, serial fallback). */
    int currentRegion();

    /** Calling thread's region ticks {@code body} (owns its position). */
    boolean owns(PhysicsBody body);

    /** Calling thread's region owns {@code pos}. */
    boolean owns(ServerLevel level, BlockPos pos);

    /** Runs {@code work} inline when the caller owns {@code pos}, else queued to the owner's next tick start (unowned: main). */
    void runOnOwner(ServerLevel level, BlockPos pos, Runnable work);

    /**
     * Main thread: runs {@code work} at a main-thread boundary after the region owning {@code pos} is idle (async loops can
     * outlive the tick; unleased main-thread entity/block reads race its writers). Default inline.
     */
    default void runLeased(ServerLevel level, BlockPos pos, Runnable work) {
        work.run();
    }
}
