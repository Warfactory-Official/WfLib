package com.wf.wflib.recon.api;

import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.core.api.WorldThread;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The source registry. */
public final class ReconTargets {

    /** All priority 0: {@link #collect} dedups by source id, first wins, so registration order is the semantics. */
    public static final OrderedRegistry<TargetSource> SOURCES = OrderedRegistry.create("recon", "recon.targets");

    private ReconTargets() {
    }

    public static int sourceCount() {
        return SOURCES.values().size();
    }

    /** Everything inside this volume, from every source, deduplicated. */
    public static List<TargetSnapshot> collect(ServerLevel level, AABB volume) {
        WorldThread.assertOn("recon target collection");
        List<TargetSnapshot> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Interference.Area> areas = Interference.live(level);
        TargetSink sink = snapshot -> {
            if (seen.add(snapshot.sourceId())) {
                out.add(Interference.apply(areas, snapshot));
            }
        };
        for (TargetSource source : SOURCES.values()) {
            source.collect(level, volume, sink);
        }
        return out;
    }

    /** What each source has inside this volume, attributed to the source that produced it. */
    public static List<SourceCensus> census(ServerLevel level, AABB volume) {
        WorldThread.assertOn("recon target census");
        List<SourceCensus> out = new ArrayList<>();
        for (TargetSource source : SOURCES.values()) {
            List<TargetSnapshot> found = new ArrayList<>();
            source.collect(level, volume, found::add);
            out.add(new SourceCensus(source.getClass().getSimpleName(), found));
        }
        return out;
    }

    /**
     * One source's answer, with the source named so a report can say who produced what.
     */
    public record SourceCensus(String source, List<TargetSnapshot> found) {
    }
}
