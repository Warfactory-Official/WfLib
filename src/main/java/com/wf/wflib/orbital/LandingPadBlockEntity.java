package com.wf.wflib.orbital;

import com.wf.wflib.orbital.payload.LandingPads;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import com.wf.wflib.recon.api.OrbitalNodeBlockEntity;
import com.wf.wflib.orbital.api.OrbitalContent;

public class LandingPadBlockEntity extends OrbitalNodeBlockEntity {

    public LandingPadBlockEntity(BlockPos pos, BlockState state) {
        super(OrbitalContent.LANDING_PAD_BE.get(), pos, state);
    }

    @Override
    protected String nodeLabel() {
        return LandingPads.LABEL;
    }
}
