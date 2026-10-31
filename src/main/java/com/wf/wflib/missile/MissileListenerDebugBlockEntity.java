package com.wf.wflib.missile;

import com.wf.wflib.missile.api.IMissileListener;
import com.wf.wflib.missile.api.MissileListenerData;
import com.wf.wflib.missile.api.MissileSimConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;


public class MissileListenerDebugBlockEntity extends BlockEntity implements IMissileListener {
    public MissileListenerDebugBlockEntity(BlockPos pos, BlockState state) {
        super(MissileSystem.MISSILE_LISTENER_DEBUG_BE.get(), pos, state);
    }

    public void serverTick() {
        if (this.level instanceof ServerLevel sl) {
            MissileListenerData.get(sl).register(this.worldPosition, this);
        }
    }

    @Override
    public Vec3 listenerCenter() {
        return Vec3.atCenterOf(this.worldPosition);
    }

    @Override
    public double listenerRange() {
        return MissileSimConfig.DEBUG_LISTENER_RANGE;
    }

    @Override
    public boolean listenerValid() {
        return !this.isRemoved() && this.level instanceof ServerLevel sl && sl.isLoaded(this.worldPosition);
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel sl) {
            MissileListenerData.get(sl).deregister(this.worldPosition);
        }
        super.setRemoved();
    }
}
