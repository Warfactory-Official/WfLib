package com.wf.wflib.suppression.api;

import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.suppression.client.SuppressionFx;
import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;

/** Suppression level [0, 1] of the local player. Cosmetic; client thread. */
public final class Suppression {
    /** Any true => level held at 0, nothing drawn (e.g. replay playback). */
    public static final OrderedRegistry<BooleanSupplier> MUTES = OrderedRegistry.create("suppression", "suppression mutes");
    /** Near-miss radius, blocks, for a 900 m/s round; scaled by speed, clamped. */
    static final double RADIUS = 3.5;
    static final double REF_SPEED = 900.0 / 20.0;
    static final float GAIN = 0.45F;

    private Suppression() {
    }

    public static float level(float partialTick) {
        return SuppressionFx.level(partialTick);
    }

    public static void add(float impulse) {
        if (!muted()) {
            SuppressionFx.add(impulse);
        }
    }

    public static boolean muted() {
        for (BooleanSupplier m : MUTES.values()) {
            if (m.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /** Level added by a pass at distance {@code d}; 0 outside {@code radius}. */
    public static float impulse(double d, double radius) {
        if (d >= radius) {
            return 0;
        }
        float k = (float) (1 - d / radius);
        return GAIN * k * k;
    }

    public static double radius(double blocksPerTick) {
        return RADIUS * Mth.clamp(blocksPerTick / REF_SPEED, 0.4, 1.5);
    }

    /** Impulse of one round step passing {@code d} from the eye; own and resting rounds none. */
    public static float nearMiss(int localId, int shooterId, boolean resting, double d, double blocksPerTick) {
        return shooterId == localId || resting ? 0 : impulse(d, radius(blocksPerTick));
    }
}
