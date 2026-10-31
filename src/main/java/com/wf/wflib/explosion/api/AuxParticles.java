package com.wf.wflib.explosion.api;

import com.wf.wflib.explosion.AuxParticlePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Named client particle effect at a point; send with {@code WFNetwork}. */
public final class AuxParticles {
    private AuxParticles() {
    }

    public static CustomPacketPayload packet(String effect, double x, double y, double z, CompoundTag data) {
        return new AuxParticlePacket(effect, x, y, z, data);
    }
}
