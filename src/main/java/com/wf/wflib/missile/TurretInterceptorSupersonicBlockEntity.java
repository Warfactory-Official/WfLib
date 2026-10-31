package com.wf.wflib.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;


public class TurretInterceptorSupersonicBlockEntity extends TurretInterceptorBlockEntity {
    public TurretInterceptorSupersonicBlockEntity(BlockPos pos, BlockState state) {
        super(MissileSystem.TURRET_INTERCEPTOR_SUPERSONIC_BE.get(), pos, state, "interceptor_supersonic");
    }
}
