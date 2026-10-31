package com.wf.wflib.recon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Creative source: unlimited, for grid testing without a generator. */
public class GridPowerCellBlockEntity extends BlockEntity {

    /** FE/face/tick; exceeds heaviest probe draw, feeds a cluster. */
    private static final int OUTPUT = 512;

    public GridPowerCellBlockEntity(BlockPos pos, BlockState state) {
        super(ReconSystem.GRID_POWER_CELL_BE.get(), pos, state);
    }

    public void serverTick() {
        if (!(this.level instanceof ServerLevel sl)) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = this.worldPosition.relative(dir);
            IEnergyStorage sink = sl.getCapability(Capabilities.EnergyStorage.BLOCK, neighbour, dir.getOpposite());
            if (sink != null && sink.canReceive()) {
                sink.receiveEnergy(OUTPUT, false);
            }
        }
    }
}
