package com.wf.wflib.rail;

import com.wf.wflib.rail.align.AlignmentProgress;
import com.wf.wflib.rail.align.AlignmentService;
import com.wf.wflib.rail.build.RailWorks;
import com.wf.wflib.rail.excavate.ExcavationService;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Server lifecycle of the rail package: excavation, alignments, bore trains. */
public final class RailFeatures {

    private RailFeatures() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Not gated on Immersive Railroading: digging a hole is not one of the things it provides.
        ExcavationService.startup();
    }

    public static void shutdown() {
        ExcavationService.shutdown();
        // A bore train is a machine somebody is watching, and there is nobody left to watch it.
        RailWorks.clear();
        com.wf.wflib.rail.demo.FivePointsFixture.clearAll();
        // The projections are keyed by route id, which the next world will reuse.
        AlignmentProgress.clearCache();
    }

    /** A chunk has loaded, so any worker rewriting its stored copy is now holding stale data. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ExcavationService.onChunkLoad(level, event.getChunk().getPos());
        }
    }

    public static void tick(ServerLevel level) {
        // Alignments are the planning layer and are deliberately not gated on Immersive Railroading: a
        // route is worth surveying, sharing and arguing about before there is any track to lay on it,
        // and the editor that draws them is not gated either.
        AlignmentService.tick(level);
        ExcavationService.tickIfPresent(level);
        // After the excavator, so a machine that queued a carve this tick is not also waiting a tick
        // for it to be picked up.
        RailWorks.tick(level);
        com.wf.wflib.rail.demo.FivePointsFixture.tick(level);
    }
}
