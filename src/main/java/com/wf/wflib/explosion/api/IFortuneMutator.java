package com.wf.wflib.explosion.api;

import net.minecraft.world.level.block.state.BlockState;

/** Fortune level applied to the block's drops (bonus-ore charges). */
public interface IFortuneMutator {

    int mutateFortune(ExplosionAEF explosion, BlockState state, int x, int y, int z);
}
