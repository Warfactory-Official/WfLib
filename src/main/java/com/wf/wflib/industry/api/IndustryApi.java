package com.wf.wflib.industry.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import com.wf.wflib.industry.IndustryTracker;
import com.wf.wflib.industry.IndustryValues;

/** The public face of industry tracking, and the seam other mods in the stack integrate through. */
public final class IndustryApi {

    private IndustryApi() {
    }

    /** Declare what a block is worth as a provocation; the config whitelist overrides. Before registries freeze. */
    static void registerValue(ResourceLocation blockId, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("industry value must be positive: " + blockId);
        }
        IndustryValues.CODE_ROWS.register(blockId, value);
    }

    /**
     * @return accumulated provocation in the region cell containing this position.
     */
    static int pressureAt(ServerLevel level, int blockX, int blockZ) {
        return IndustryData.get(level).pressureAt(blockX, blockZ);
    }

    /**
     * @return every base found by the last scan, worst offender first. Empty until the first scan lands.
     */
    public static List<IndustryCluster> clusters(ServerLevel level) {
        return IndustryClusters.clusters(level);
    }

    /**
     * @return the base nearest a position, or null if none is known yet.
     */
    public static @Nullable IndustryCluster nearestCluster(ServerLevel level, double x, double z) {
        return IndustryClusters.nearest(level, x, z);
    }

    /**
     * @return the machine most worth attacking near a position in the loaded world, or null. Block-precise
     *      and ranked by provocation, unlike {@link #nearestCluster}, which answers in 512-block region cells.
     */
    public static @Nullable BlockPos pressingMachine(ServerLevel level, double x, double z, double radius) {
        return IndustryTracker.pressingMachine(level, x, z, radius, null, 0.0);
    }

    /**
     * As above, ignoring anything within {@code separation} of a machine already spoken for.
     */
    public static @Nullable BlockPos pressingMachine(ServerLevel level, double x, double z, double radius,
                                                     @Nullable BlockPos awayFrom, double separation) {
        return IndustryTracker.pressingMachine(level, x, z, radius, awayFrom, separation);
    }

    /**
     * @return total tracked provocation in this level.
     */
    public static int totalPressure(ServerLevel level) {
        return IndustryData.get(level).totalValue();
    }
}
