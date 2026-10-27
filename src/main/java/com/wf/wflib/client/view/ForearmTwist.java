package com.wf.wflib.client.view;

/**
 * One arm's hand roll about the forearm ({@link ArmViewPose#solve} twist) split into forearm corkscrew + whole-arm
 * roll. {@link #roll}: hand roll unwrapped across frames; fist drawn at it (fist relax FORBIDDEN: still gun,
 * spinning fist). Corkscrew = {@code roll - winding} clamped to the twist limit; only the excess rolls the whole arm
 * (upper + forearm rigid). Winding = whole turns, unwound at {@link #UNWIND} once the roll is {@link #WIND_HOLD} off it.
 * Roll follows the twist by swing confidence: full to {@link #SWING_SURE}, none from {@link #SWING_LOST} (hand +Y near
 * -forearm => twist undefined; Strela / Sten / RPG-7 clips stepped 36-120 deg per frame at swing 100-172 => wound full
 * spins).
 * Quarter-turn equivalence (arm turned in 90 deg steps) FORBIDDEN: skin faces differ, every pistol inspect turned the
 * whole arm 8 times (720 deg).
 */
public final class ForearmTwist {
    static final float TURN = (float) (2 * Math.PI);
    /** rad: roll off the winding before it moves a whole turn (no flapping at a half turn). */
    static final float WIND_HOLD = (float) Math.toRadians(210);
    /** rad/s: a whole turn in 0.6 s. */
    static final float UNWIND = TURN / 0.6F;
    static final float SWING_SURE = (float) Math.toRadians(90), SWING_LOST = (float) Math.toRadians(150);

    private float roll;
    private float winding;
    private float target;
    private float base;
    private boolean started;

    public void reset() {
        started = false;
        roll = winding = target = base = 0;
    }

    /**
     * {@code twist} rad (any whole-turn equivalent), {@code swing} rad ({@link ArmViewPose#swing}), {@code limit} rad
     * corkscrew either way, {@code dt} s since the last update.
     */
    public void update(float twist, float swing, float limit, float dt) {
        if (!started) {
            started = true;
            roll = (float) Math.IEEEremainder(twist, TURN);
        } else {
            float sure = Math.clamp((SWING_LOST - swing) / (SWING_LOST - SWING_SURE), 0, 1);
            roll += sure * sure * (3 - 2 * sure) * (float) Math.IEEEremainder(twist - roll, TURN);
        }
        if (Math.abs(roll - target) > WIND_HOLD) target = Math.round(roll / TURN) * TURN;
        float step = UNWIND * dt;
        winding += Math.clamp(target - winding, -step, step);
        float off = roll - winding;
        base = winding + off - Math.clamp(off, -limit, limit);
    }

    /** Fist roll, rad. */
    public float roll() {
        return roll;
    }

    /** Whole-arm roll, rad. */
    public float base() {
        return base;
    }
}
