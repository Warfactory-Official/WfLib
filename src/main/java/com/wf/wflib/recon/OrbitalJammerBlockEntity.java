package com.wf.wflib.recon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import com.wf.wflib.recon.api.OrbitalNodeBlockEntity;

public class OrbitalJammerBlockEntity extends OrbitalNodeBlockEntity {

    public static final String LABEL = "jammer";

    public OrbitalJammerBlockEntity(BlockPos pos, BlockState state) {
        super(ReconSystem.ORBITAL_JAMMER_BE.get(), pos, state);
    }

    public boolean powered() {
        return this.level != null && this.level.hasNeighborSignal(this.worldPosition);
    }

    @Override
    protected String nodeLabel() {
        return LABEL;
    }

    @Override
    protected boolean nodeActive() {
        return powered();
    }
}
