package com.wf.wflib.build;

import com.wf.wflib.missile.api.DetonationChunkGuard;
import com.wf.wflib.compat.api.TerritoryVerdict;
import com.wf.wflib.work.api.WorkJob;
import com.wf.wflib.work.api.WorkQueue;
import com.wf.wflib.work.api.WorkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.build.api.BuildJobs;

/** Territory + chunk-load housekeeping the generic {@code work} package must not know about. */
public final class BuildManager {

    private static final int CLAIM_CHUNK_RADIUS = 1;

    private BuildManager() {
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        boolean survey = now % SiteRules.RECHECK_TICKS == 0;
        for (WorkJob job : WorkData.get(level).all()) {
            if (!job.dimension().equals(level.dimension()) || job.over()) {
                continue;
            }
            if (survey) {
                enforce(level, job);
            }
            if (job.workable()) {
                holdClaims(level, job);
            }
        }
    }

    private static void enforce(ServerLevel level, WorkJob job) {
        TerritoryVerdict verdict = SiteRules.survey(level, BuildJobs.factionOf(job), job.bounds());
        String reason = verdict.allowed() ? null : verdict.reason();
        if (job.suspend(reason)) {
            WorkData.get(level).setDirty();
        }
    }

    private static void holdClaims(ServerLevel level, WorkJob job) {
        WorkQueue queue = job.queue();
        for (int id : queue.claimedIds()) {
            BlockPos at = queue.order(id).at();
            DetonationChunkGuard.hold(level, Vec3.atCenterOf(at), CLAIM_CHUNK_RADIUS);
        }
    }
}
