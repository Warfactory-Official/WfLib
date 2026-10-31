package com.wf.wflib.anomaly.client;

import com.wf.wflib.anomaly.AnomalyBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** An anomaly's idle_sound, looped at it, as loud as it is present (gone while spent); stops with it. */
final class AnomalyIdleSound extends AbstractTickableSoundInstance {
    private final AnomalyBlockEntity be;

    AnomalyIdleSound(AnomalyBlockEntity be) {
        super(be.type().sounds().idle().get(), SoundSource.BLOCKS, RandomSource.create());
        this.be = be;
        Vec3 c = be.center();
        x = c.x;
        y = c.y + 1.0D;
        z = c.z;
        looping = true;
        delay = 0;
        volume = 0.01F;
    }

    @Override
    public void tick() {
        if (be.isRemoved()) {
            stop();
            return;
        }
        // A wandering anomaly's sound goes with it.
        Vec3 c = be.center();
        x = c.x;
        y = c.y + 1.0D;
        z = c.z;
        volume = Math.max(0.01F, be.presence(0.0F));
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
