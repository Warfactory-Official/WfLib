package com.wf.wflib.anomaly.client;

import com.wf.wflib.anomaly.AnomalyBlockEntity;
import com.wf.wflib.anomaly.AnomalyConfig;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** An anomaly's blowout_sound, played once at it; {@link #fadeOut} winds it down over half a second. */
final class AnomalyBlowoutSound extends AbstractTickableSoundInstance {
    private static final float VOLUME = 2.0F;
    private static final int FADE_TICKS = 10;

    private final AnomalyBlockEntity be;
    private boolean fading;

    AnomalyBlowoutSound(AnomalyBlockEntity be, RandomSource random) {
        super(be.type().sounds().blowout().get(), SoundSource.BLOCKS, random);
        this.be = be;
        Vec3 c = be.center();
        x = c.x;
        y = c.y + 1.0D;
        z = c.z;
        volume = VOLUME;
    }

    void fadeOut() {
        fading = true;
    }

    @Override
    public void tick() {
        if (be.isRemoved() || !AnomalyConfig.enabled) {
            stop();
        } else if (fading && (volume -= VOLUME / FADE_TICKS) <= 0.0F) {
            stop();
        }
    }
}
