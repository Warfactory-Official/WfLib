package com.wf.wflib.industry;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Industry tracking tunables; immutable snapshot swapped on load. */
public final class IndustryConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> MACHINES;
    private static final ModConfigSpec.IntValue CELL_CHUNKS;
    private static final ModConfigSpec.IntValue CLUSTER_GAP;
    private static final ModConfigSpec.IntValue CLUSTER_MIN_VALUE;
    private static final ModConfigSpec.IntValue RECOMPUTE_DELAY;
    private static final ModConfigSpec.BooleanValue BACKFILL;

    /**
     * @param cellChunks region cell side in chunks
     * @param gapCells empty cells allowed inside one base
     * @param minValue combined value that makes a group of cells a base
     * @param delayTicks quiet ticks before bases are recomputed
     * @param backfill sweep each chunk once on first load
     */
    public record Values(int cellChunks, int gapCells, int minValue, int delayTicks, boolean backfill) {
    }

    private static volatile Values values = new Values(32, 1, 50, 100, true);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Industry tracking: what provokes the glyphids, and how bases are detected.",
                "Machines register on placement and on the one-off sweep each chunk gets when it first",
                "loads, so a base built before this feature existed still counts.");
        MACHINES = b
                .comment("Blocks that provoke, and by how much.",
                        "Format: \"registryId=value\" (value defaults to 1 if omitted).",
                        "Dirty industry is meant to score far higher than clean: a diesel farm should draw",
                        "attention long before an equivalent electric base does.",
                        "Code rows (IndustryApi.registerValue) are overridden by these.")
                .defineList("machines", IndustryValues.DEFAULTS, () -> "", o -> o instanceof String);
        CELL_CHUNKS = b
                .comment("Region cell size in chunks. Provocation is accumulated per cell, and bases are",
                        "detected by grouping neighbouring occupied cells. Larger cells are cheaper and",
                        "coarser; changing this rebuilds the index on next load.")
                .defineInRange("regionCellChunks", 32, 1, 512);
        CLUSTER_GAP = b
                .comment("How many empty cells may sit between two occupied ones before they count as",
                        "separate bases. The cell-space equivalent of DBSCAN's eps.")
                .defineInRange("clusterGapCells", 1, 0, 16);
        CLUSTER_MIN_VALUE = b
                .comment("Combined value a group of cells needs before it counts as a base rather than noise.",
                        "The equivalent of DBSCAN's minPts, but measured in provocation, so one dirty plant",
                        "outweighs a scattering of furnaces.")
                .defineInRange("clusterMinValue", 50, 1, 1_000_000);
        RECOMPUTE_DELAY = b
                .comment("Ticks of quiet after the last change before bases are re-detected (off-thread).",
                        "Debouncing matters: laying out a factory is hundreds of placements in a few seconds.")
                .defineInRange("recomputeDelayTicks", 100, 1, 72_000);
        BACKFILL = b
                .comment("Sweep each chunk once, on its first load, for machines no placement event reported",
                        "(worldgen, structures, KubeJS, AE2, /setblock, anything pre-existing).",
                        "Only block entities are examined, so this is cheap.")
                .define("chunkBackfill", true);
        SPEC = b.build();
    }

    private IndustryConfig() {
    }

    public static Values get() {
        return values;
    }

    /** Region cell size in chunks. */
    public static int cellChunks() {
        return values.cellChunks();
    }

    /** How many empty cells may sit between two occupied ones before they count as separate bases. */
    public static int clusterGapCells() {
        return values.gapCells();
    }

    /** Combined value a group of cells needs before it is a base rather than noise. */
    public static int clusterMinValue() {
        return values.minValue();
    }

    /** Ticks of quiet after the last change before clusters are recomputed. */
    public static int recomputeDelayTicks() {
        return values.delayTicks();
    }

    /** Whether chunks are swept once on load for machines that no event ever reported. */
    public static boolean backfillEnabled() {
        return values.backfill();
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        values = new Values(Math.max(1, CELL_CHUNKS.get()), Math.max(0, CLUSTER_GAP.get()),
                Math.max(1, CLUSTER_MIN_VALUE.get()), Math.max(1, RECOMPUTE_DELAY.get()), BACKFILL.get());
        IndustryValues.rebake(MACHINES.get());
    }
}
