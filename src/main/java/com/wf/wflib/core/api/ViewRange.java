package com.wf.wflib.core.api;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Blocks from a player within which wflib keeps missiles and drones real entities and sends rounds. Distant entity
 * tracking past the server view distance = Boxy {@code boxy:distant} tag.
 */
public final class ViewRange {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue VIEW_RANGE;
    private static final ModConfigSpec.DoubleValue SIM_VIEW_RANGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        VIEW_RANGE = b
                .comment("Blocks from a player within which missiles and drones stay real (ticking, chunk-loading,",
                        "rendered) entities and kinetic rounds are sent to that player.")
                .defineInRange("viewRange", 1536.0, 16.0, 4096.0);
        SIM_VIEW_RANGE = b
                .comment("Blocks from a player within which off-world (simulated) missiles and drones are drawn as",
                        "render-only ghosts. 0 = never.")
                .defineInRange("simViewRange", 4096.0, 0.0, 16384.0);
        SPEC = b.build();
    }

    private record Snapshot(double blocks, double sim) {}

    private static volatile Snapshot snapshot = new Snapshot(1536.0, 4096.0);

    private ViewRange() {
    }

    public static double blocks() {
        return snapshot.blocks;
    }

    /** Past {@link #blocks()}: off-world missiles/drones drawn as client ghosts out to this. */
    public static double sim() {
        return snapshot.sim;
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            snapshot = new Snapshot(VIEW_RANGE.get(), SIM_VIEW_RANGE.get());
        }
    }
}
