package com.wf.wflib.explosion.standard;

import com.wf.wflib.explosion.api.ExplosionAEF;
import com.wf.wflib.explosion.api.IDropChanceMutator;
import net.minecraft.world.level.block.state.BlockState;

public class DropChanceMutatorStandard implements IDropChanceMutator {

    private final float chance;

    public DropChanceMutatorStandard(float chance) {
        this.chance = chance;
    }

    @Override
    public float mutateDropChance(ExplosionAEF explosion, BlockState state, int x, int y, int z, float chance) {
        return this.chance;
    }
}
