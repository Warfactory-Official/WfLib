package com.wf.wflib.smoking;

import org.joml.Vector3f;

/** One cigarette, ticks from the start of use. GAMMA {@code cigar1}: {@code tm} 10.5 s, light 4.0 s, exhale 7.0-8.1 / 10.0-11.1 s. */
public final class SmokingTimeline {
    public static final int TICKS = 210;
    public static final int LIT = 80;
    private static final int DRAG = 24;
    private static final int[][] EXHALE = {{140, 162}, {200, TICKS}};

    /** AHF model space (blocks, +Y down, -Z forward), right arm: hanging fist / at the mouth. */
    private static final Vector3f HANGING = new Vector3f(-0.3125f, 0.875f, 0.0f);
    private static final Vector3f MOUTH = new Vector3f(-0.04f, -0.1f, -0.3f);
    private static final float RAISE_FROM = 0.3f, RAISE_TO = 1.6f, LOWER_FROM = 8.9f, LOWER_TO = 9.9f;

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
