package com.wf.wflib.door;

import com.wf.wflib.probe.api.ProbeCapabilities;
import com.wf.wflib.probe.api.ProbeDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.wf.wflib.door.api.DoorBlockEntity;

/** The one thing about a door the client is not told: how many of its blocks are being powered. */
public final class DoorProbeData {

    public static final String POWERED = "PoweredBlocks";

    private DoorProbeData() {
    }

    private static final ProbeDataProvider.Blocks DATA = (data, player, level, pos, state, blockEntity) -> {
        net.minecraft.core.BlockPos core = DoorFrame.findCore(level, pos, state.getBlock());
        if (core != null && level.getBlockEntity(core) instanceof DoorBlockEntity door) {
            data.putInt(POWERED, door.poweredBlocks());
        }
    };

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(ProbeCapabilities.BLOCK_DATA, (level, pos, state, be, ctx) -> DATA,
                DoorContent.DOORS.values().stream().map(holder -> (Block) holder.get()).toArray(Block[]::new));
    }

    public static int powered(CompoundTag data) {
        return data.getInt(POWERED);
    }
}
