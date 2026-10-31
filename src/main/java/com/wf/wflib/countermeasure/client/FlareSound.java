package com.wf.wflib.countermeasure.client;

import com.wf.wflib.audio.api.AudioCategory;
import com.wf.wflib.audio.api.SoundSpec;
import com.wf.wflib.audio.api.client.Emitter;
import com.wf.wflib.countermeasure.api.FlareEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.countermeasure.CountermeasureSystem;

public final class FlareSound {
    private static final SoundSpec BURN = new SoundSpec(CountermeasureSystem.FLARE_BURN.getId(), 1, 1, 0.1F, 96, 0);

    private FlareSound() {
    }

    public static void play(FlareEntity flare) {
        Emitter e = Emitter.following(BURN, AudioCategory.WEAPONS, flare, Vec3.ZERO, false);
        if (e.refresh()) {
            Minecraft.getInstance().getSoundManager().play(e);
        }
    }
}
