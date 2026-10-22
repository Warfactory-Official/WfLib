package com.wf.wflib.client.view;

/**
 * One arm's hand roll split into fist roll + arm turn. Arm boxes = square section => hand roll counts mod a quarter
 * turn (TACZ / MW hand frames: roll arbitrary in 90 deg steps). {@link #roll}: continuous quarter-turn equivalent of the
 * hand roll; fist drawn at it exactly. {@link #base}: whole-arm turn (upper + forearm rigid, shoulder off screen), a
 * multiple of 90 deg at rest; forearm corkscrew = roll - base. Base follows the roll at {@link #RELAX} when the hand
 * is still past {@link #REST} or anywhere past 90 deg. Relaxing the fist instead FORBIDDEN: still gun, fist spins 90
 * deg (TACZ glock_17 reload / inspect hold the hand at +110..+120 deg).
 */
public final class ForearmTwist {
    static final float QUARTER = (float) (Math.PI / 2);
    /** rad/s: a quarter turn in 0.3 s. */
    static final float RELAX = QUARTER / 0.3F;
    /** rad/s of hand roll below which the hand counts as still. */
    static final float STILL = 1F;
    /** Still corkscrew kept up to this: ADS moves the TACZ hip -> ADS left twist -133 -> -145 deg (-43 -> -55 mod 90). */
    static final float REST = (float) Math.toRadians(60);
    /** Base rate gain per quarter turn of corkscrew past {@link #QUARTER}. */
    static final float WOUND_GAIN = 4;

    private float roll;
    private float base;
    private boolean started;
    /** Latched until the base reaches its quarter turn. */
    private boolean turning;

    public void reset() {
        started = turning = false;
        roll = base = 0;
    }

    /** {@code twist} rad (any quarter-turn equivalent), {@code dt} s since the last update. */
    public void update(float twist, float dt) {
        if (!started) {
            started = true;
            roll = (float) Math.IEEEremainder(twist, QUARTER);
            base = 0;
            return;
        }
        float next = roll + (float) Math.IEEEremainder(twist - roll, QUARTER);
        boolean still = Math.abs(next - roll) < STILL * dt;
        roll = next;
        float corkscrew = Math.abs(roll - base);
        turning |= still && corkscrew > REST || corkscrew > QUARTER;
        float target = turning ? Math.round(roll / QUARTER) * QUARTER : Math.round(base / QUARTER) * QUARTER;
        float step = RELAX * dt * (1 + WOUND_GAIN * Math.max(0, corkscrew - QUARTER) / QUARTER);
        base += Math.max(-step, Math.min(step, target - base));
        turning &= base != target;
    }

    /** Fist roll, rad. */
    public float roll() {
        return roll;
    }

    /** Whole-arm turn, rad. */
    public float base() {
        return base;
    }
}
