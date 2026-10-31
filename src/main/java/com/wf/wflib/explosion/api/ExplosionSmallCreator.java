package com.wf.wflib.explosion.api;

import com.wf.wflib.audio.api.AudioCategory;
import com.wf.wflib.audio.api.Blasts;
import com.wf.wflib.audio.api.WfAudio;
import com.wf.wflib.explosion.AuxParticlePacket;
import com.wf.wflib.core.api.WFNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;


public final class ExplosionSmallCreator {

    private static final int DEBRIS = 15;

    private ExplosionSmallCreator() {
    }

    /**
     * @param cloudCount     number of fireball puffs
     * @param cloudScale     puff size
     * @param cloudSpeedMult puff velocity multiplier
     */
    public static void composeEffect(Level level, double x, double y, double z,
                                     int cloudCount, float cloudScale, float cloudSpeedMult) {
        if (level.isClientSide) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt("count", cloudCount);
        data.putFloat("scale", cloudScale);
        data.putFloat("speed", cloudSpeedMult);
        data.putInt("debris", DEBRIS);

        WFNetwork.sendToAllAround(level, x, y, z, com.wf.wflib.core.api.ViewRange.blocks(), new AuxParticlePacket("explosion_small", x, y, z, data));
        WfAudio.at((ServerLevel) level, new Vec3(x, y, z), AudioCategory.EXPLOSIONS, Blasts.small());
    }
}
