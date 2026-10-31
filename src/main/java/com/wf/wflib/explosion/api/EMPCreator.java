package com.wf.wflib.explosion.api;

import com.wf.wflib.explosion.AuxParticlePacket;
import com.wf.wflib.core.api.WFNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public final class EMPCreator {

    private EMPCreator() {
    }

    public static void compose(Level level, double x, double y, double z, int radius) {
        if (level.isClientSide) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt("radius", radius);

        WFNetwork.sendToAllAround(level, x, y, z, com.wf.wflib.core.api.ViewRange.blocks(), new AuxParticlePacket("emp", x, y, z, data));
    }

    public static void composeStun(Level level, double x, double y, double z) {
        if (level.isClientSide) {
            return;
        }
        WFNetwork.sendToAllAround(level, x, y, z, 64.0, new AuxParticlePacket("emp_stun", x, y, z, new CompoundTag()));
    }
}
