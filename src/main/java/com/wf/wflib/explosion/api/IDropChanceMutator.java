package com.wf.wflib.explosion.api;

import net.minecraft.world.level.block.state.BlockState;

public interface IDropChanceMutator {

    /** Chance to use, {@code [0,1]}. */
    float mutateDropChance(ExplosionAEF explosion, BlockState state, int x, int y, int z, float chance);
}
