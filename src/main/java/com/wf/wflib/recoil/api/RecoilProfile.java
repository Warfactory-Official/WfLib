package com.wf.wflib.recoil.api;

import java.util.List;

/**
 * One weapon's recoil shape. {@code pitch} / {@code yaw}: per-shot offset curves, deg, before the shot's factors.
 * {@code recoveryDelay} s, {@code recoveryRate} 1/s: decay of the accumulated camera offset after the last shot.
 * {@code wander}: yaw draws smooth-interpolated over this many shots (<= 1 independent). {@code pullFraction}: share of
 * the camera offset the recovery returns.
 */
public record RecoilProfile(List<CurveKey> pitch, List<CurveKey> yaw, float recoveryDelay, float recoveryRate,
                            float wander, float pullFraction, Flip flip) {
    public RecoilProfile {
        if (pitch.isEmpty() || yaw.isEmpty()) {
            throw new IllegalArgumentException("recoil curve without keys");
        }
        pitch = List.copyOf(pitch);
        yaw = List.copyOf(yaw);
    }

    /** time s; offset deg drawn uniformly from {@code [min, max]}. */
    public record CurveKey(float time, float min, float max) {
    }

    /**
     * Weapon-only muzzle flip per shot, added to the weapon's lead (camera untouched): muzzle {@code pitch} deg up at
     * {@code attack} s (smoothstep), then exponential fall with time constant {@code decay} s; {@code yaw} deg signed by
     * the shot's yaw draw. Same factors as the camera kick.
     */
    public record Flip(float pitch, float yaw, float attack, float decay) {
        public static final Flip NONE = new Flip(0, 0, 0.03F, 0.06F);
    }
}
