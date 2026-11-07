package com.wf.wflib.round.api;

import com.wf.wflib.round.RoundConfig;
import com.wf.wflib.round.pen.PenMaterial;
import com.wf.wflib.round.pen.PenTable;
import net.minecraft.world.level.block.state.BlockState;

/** What the round system reads for a block: data map value, else its {@link PenMaterial} class. */
public final class PenValues {
    private PenValues() {
    }

    /** mm steel-eq per metre. */
    public static float resistance(BlockState state) {
        return PenTable.resistance(state);
    }

    /** kg m/s that destroys the block, before {@link #toughnessScaled}. */
    public static float toughness(BlockState state) {
        return PenTable.toughness(state);
    }

    /** Toughness the destruction check uses (config scale applied). */
    public static float toughnessScaled(BlockState state) {
        return PenTable.toughness(state) * RoundConfig.snapshot().blockToughnessScale();
    }

    /** Critical grazing angle, deg. */
    public static float ricochet(BlockState state) {
        return PenTable.ricochet(state);
    }

    /** Lower-case {@link PenMaterial} class name. */
    public static String materialClass(BlockState state) {
        return PenMaterial.resolve(state).name().toLowerCase(java.util.Locale.ROOT);
    }
}
