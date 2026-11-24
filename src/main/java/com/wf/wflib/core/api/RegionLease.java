package com.wf.wflib.core.api;

import com.wf.wflib.physics.api.RegionHost;
import com.wf.wflib.physics.step.RigidStep;
import com.wf.wflib.physics.step.TessellateLease;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    /** {@code ChunkMap.move} iterates {@code entityMap}, written only on main: region worker => queued to main, else inline. */
    public static void moveOnMain(ServerLevel level, ServerPlayer player) {
        RegionHost host = RigidStep.regionHost();
        if (host == null || host.currentRegion() < 0) {
            level.getChunkSource().move(player);
        } else {
            level.getServer().execute(() -> {
                if (!player.isRemoved()) level.getChunkSource().move(player);
            });
        }
    }

    /**
     * Server thread, before scanning {@code box} for entities: waits until every region overlapping it is idle
     * (typed {@code getEntities} creates class buckets in region-owned sections). Samples every 2nd chunk: region
     * cells are >= 2 chunks ({@code sectionShift >= 1}). No Tessellate or not the server thread => no-op.
     */
    public static void leaseArea(ServerLevel level, AABB box) {
        TessellateLease lease = TessellateLease.INSTANCE;
        if (lease == null || !level.getServer().isSameThread()) {
            return;
        }
        int x0 = Mth.floor(box.minX) >> 4;
        int x1 = Mth.floor(box.maxX) >> 4;
        int z0 = Mth.floor(box.minZ) >> 4;
        int z1 = Mth.floor(box.maxZ) >> 4;
        for (int cx = x0; cx <= x1 + 1; cx += 2) {
            for (int cz = z0; cz <= z1 + 1; cz += 2) {
                lease.lease(level, ChunkPos.asLong(Math.min(cx, x1), Math.min(cz, z1)));
            }
        }
    }
}
