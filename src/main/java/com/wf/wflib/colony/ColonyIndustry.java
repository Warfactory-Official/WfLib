package com.wf.wflib.colony;

import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.industry.api.IndustryApi;
import com.wf.wflib.industry.api.IndustryCluster;
import com.wf.wflib.industry.api.IndustryClusters;
import com.wf.wflib.industry.api.IndustryData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The only place colony reads the industry model. With industry off there is no provocation, no base and no
 * machine: colonies grow and expand but never strike.
 */
final class ColonyIndustry {
    private ColonyIndustry() {
    }

    private static boolean on() {
        return WFSystems.enabled("industry");
    }

    /** A base worth a warband: where it is centred. */
    record Base(int centerX, int centerZ) {
    }

    static int pressureWithin(ServerLevel level, int x, int z, int radius) {
        return on() ? IndustryData.get(level).pressureWithin(x, z, radius) : 0;
    }

    static int totalPressure(ServerLevel level) {
        return on() ? IndustryApi.totalPressure(level) : 0;
    }

    @Nullable
    static Base nearestBase(ServerLevel level, double x, double z) {
        IndustryCluster cluster = on() ? IndustryClusters.nearest(level, x, z) : null;
        return cluster == null ? null : new Base(cluster.centerX(), cluster.centerZ());
    }

    /** Like {@link #nearestBase} but only within {@code range} blocks. */
    @Nullable
    static Base baseWithin(ServerLevel level, double x, double z, double range) {
        IndustryCluster cluster = on() ? IndustryApi.nearestCluster(level, x, z) : null;
        if (cluster == null || cluster.distanceSqTo(x, z) > range * range) {
            return null;
        }
        return new Base(cluster.centerX(), cluster.centerZ());
    }

    @Nullable
    static BlockPos pressingMachine(ServerLevel level, double x, double z, double radius,
                                    @Nullable BlockPos awayFrom, double separation) {
        return on() ? IndustryApi.pressingMachine(level, x, z, radius, awayFrom, separation) : null;
    }
}
