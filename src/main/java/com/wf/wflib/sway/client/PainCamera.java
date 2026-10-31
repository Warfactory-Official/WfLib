package com.wf.wflib.sway.client;

import com.norwood.ahf.hook.AhfHooks;
import com.wf.wflib.core.api.Seeded;
import com.wf.wflib.sway.api.PainSway;
import com.wf.wflib.sway.api.client.CameraSway;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** Pain wobble on the camera for players no {@link PainSway#YIELDS} entry claims. */
final class PainCamera implements CameraSway.Source {
    /** Wobble RMS matched to the noise shape it replaced (yaw RMS 0.510 vs 0.707). */
    static final float SCALE = 0.72F;
    private static final float ADS_STEADINESS = 0.35F;

    @Override
    public void offset(LocalPlayer p, double time, float[] out) {
        if (PainSway.yielded(p)) {
            return;
        }
        PainSway.Source src = PainSway.SOURCE.get();
        add(src, src.intensity(p), AhfHooks.gunAimProgress(p), PainSway.aimingRanged(p) ? src.aimFloor(p) : 0,
                time, Seeded.playerSeed(p.getUUID()), out);
    }

    static void add(PainSway.Source src, float intensity, float ads, float aimFloor, double time, long seed,
                    float[] out) {
        if (intensity > 0 && ads > 0) {
            intensity *= Mth.lerp(ads, 1.0F, ADS_STEADINESS);
        }
        intensity = Math.max(intensity, aimFloor);
        if (intensity > 0) {
            PainSway.wobble(SCALE * intensity * src.amplitudeDeg(), time, seed, out);
        }
    }

    @Override
    public void reset() {
    }
}
