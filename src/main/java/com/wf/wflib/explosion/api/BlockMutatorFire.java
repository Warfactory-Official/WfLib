package com.wf.wflib.explosion.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BlockMutatorFire implements IBlockMutator {

    @Override
    public void mutatePre(ExplosionAEF explosion, BlockState state, BlockPos pos) {
    }

    @Override
    public void mutatePost(ExplosionAEF explosion, BlockPos pos) {
        Level level = explosion.level;
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);

        if (level.getBlockState(pos).isAir()
                && belowState.isFaceSturdy(level, below, Direction.UP)
                && level.random.nextInt(3) == 0) {
            level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
        }
    }
}
