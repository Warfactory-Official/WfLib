package com.wf.wflib.anim;

/**
 * Arm joint limits: elbow bend direction about the arm line ({@code elbowIn} toward the body, {@code elbowOut} away,
 * rad from the pole), forearm corkscrew {@code twist} either way (rad), past it the whole arm rolls.
 */
public record ArmLimits(float elbowIn, float elbowOut, float twist) {
    /** twist 120: p1 / p99 of every default gun x motion (MotionTraceTest, 2026-10-26). */
    public static final ArmLimits DEFAULT = new ArmLimits((float) Math.toRadians(60), (float) Math.toRadians(150),
            (float) Math.toRadians(120));

    public ArmLimits {
        if (!(elbowIn > 0 && elbowOut > 0 && elbowIn + elbowOut < 2 * Math.PI && twist > 0 && twist <= Math.PI)) {
            throw new IllegalArgumentException("arm limits " + this);
        }
    }
}
