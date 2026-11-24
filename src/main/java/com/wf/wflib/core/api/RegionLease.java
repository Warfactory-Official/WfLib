package com.wf.wflib.core.api;

import com.wf.wflib.physics.api.RegionHost;
import com.wf.wflib.physics.step.RigidStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Main-thread world scans near {@code pos}: no {@link RegionHost} => inline; else after the owner region is idle. */
public final class RegionLease {

    private RegionLease() {
    }

    public static void onMain(ServerLevel level, BlockPos pos, Runnable work) {
        RegionHost host = RigidStep.regionHost();
        if (host == null) {
            work.run();
        } else {
            host.runLeased(level, pos, work);
        }
    }
}
