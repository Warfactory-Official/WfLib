package com.wf.wflib.smoking;

import org.joml.Vector3f;

/** One cigarette, ticks from the tap: the whole trimmed GAMMA sound (12.17 s, first 1.2 s out of the pack cut); clip stretched over it. */
public final class SmokingTimeline {
    public static final int TICKS = 244;
    public static final int LIT = 56;
    private static final int DRAG = 24;
    private static final int[][] EXHALE = {{144, 162}, {174, 200}, {210, 230}};

    /** AHF model space (blocks, +Y down, -Z forward), right arm: hanging fist / at the mouth. */
    private static final Vector3f HANGING = new Vector3f(-0.3125f, 0.875f, 0.0f);
    private static final Vector3f MOUTH = new Vector3f(-0.04f, -0.1f, -0.3f);
    private static final float RAISE_FROM = 0.0f, RAISE_TO = 0.6f, LOWER_FROM = 10.8f, LOWER_TO = 11.9f;

    private SmokingTimeline() {
    }

    public static boolean lit(int tick) {
        return tick >= LIT;
    }

    public static boolean exhaling(int tick) {
        for (int[] window : EXHALE) {
            if (tick >= window[0] && tick < window[1]) {
                return true;
            }
        }
        return false;
    }

    /** Last {@link #DRAG} ticks before each exhale: ember draws. */
    public static boolean dragging(int tick) {
        for (int[] window : EXHALE) {
            if (tick >= window[0] - DRAG && tick < window[0]) {
                return true;
            }
        }
        return false;
    }

    /** Right wrist for the third-person arm solve, {@code seconds} into the use. */
    public static Vector3f wrist(float seconds, Vector3f out) {
        float up = smooth((seconds - RAISE_FROM) / (RAISE_TO - RAISE_FROM)) * (1 - smooth((seconds - LOWER_FROM) / (LOWER_TO - LOWER_FROM)));
        out.set(HANGING).lerp(MOUTH, up);
        if (up >= 1.0f) {
            out.add(0.0f, 0.01f * (float) Math.sin(seconds * 2.1), 0.0f);
        }
        return out;
    }

    private static float smooth(float t) {
        t = Math.clamp(t, 0.0f, 1.0f);
        return t * t * (3 - 2 * t);
    }
}
