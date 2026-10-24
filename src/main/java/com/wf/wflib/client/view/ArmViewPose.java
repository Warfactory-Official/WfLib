package com.wf.wflib.client.view;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * First-person player arm solve ({@link PlayerArms} model): shoulder = {@link #SHOULDER} from the eye; joints upper (shoulder) -> lower (elbow, forearm + fist rigid) -> fist end = wrist goal. Joint frames: +Y down the bone;
 * hanging rest = Rz(pi) in view space. Not thread-safe (scratch).
 */
public final class ArmViewPose {
    /**
     * Right shoulder joint from the eye, view space, m (left: x mirrored). Vanilla arm pivot (0.29, -0.33, 0) FORBIDDEN:
     * wide + low => grip 0.30 m off, arm line inward-up, elbow either across the chest (hand axis) or under the fist,
     * forearm vertical (pole). Set back = shoulder rolled behind the cheek weld.
     */
    public static final Vector3fc SHOULDER = new Vector3f(0.18F, -0.20F, 0.13F);
    /** Fraction of full reach the solver bends to; past it the shoulder slides toward the wrist (arm keeps length). */
    public static final float MAX_REACH = 0.995F;
    /** Natural elbow direction, view space: seeds the elbow and leads it where the hand axis is inconclusive. */
    public static final Vector3fc RIGHT_POLE = new Vector3f(0.6F, -1, 0.2F);
    public static final Vector3fc LEFT_POLE = new Vector3f(-0.6F, -1, 0.2F);
    /** s: {@link Bend} time constant. */
    static final float BEND_TAU = 0.05F;
    /** rad/s: {@link Bend} turn cap; uncapped => elbow whips round where the hand axis sweeps through the arm line. */
    static final float BEND_SPEED = (float) Math.toRadians(600);
    /**
     * rad: elbow range about the arm line from {@code pole}, toward the body / away from it. TACZ forearms run down the
     * bore => hand axis puts the right elbow across the chest (p90 ~70 deg inward of straight down at {@link #SHOULDER}).
     */
    public static final float ELBOW_IN = (float) Math.toRadians(60), ELBOW_OUT = (float) Math.toRadians(150);
    /** rad: either side of the out-of-range midpoint where the elbow keeps its bound (no flapping across it). */
    static final float ELBOW_HOLD = (float) Math.toRadians(45);
    private static final float FAR = (ELBOW_OUT + 2 * (float) Math.PI - ELBOW_IN) / 2;
    private static final Vector3fc ARM_AXIS = new Vector3f(0, 1, 0);
    private static final Quaternionfc REST = new Quaternionf().rotationZ((float) Math.PI);

    private static final Vector3f DIR = new Vector3f();
    private static final Vector3f PERP = new Vector3f();
    private static final Vector3f T = new Vector3f();
    private static final Vector3f FOREARM = new Vector3f();
    private static final Vector3f UPPER = new Vector3f();
    private static final Vector3f OUT = new Vector3f();
    private static final Vector3f HINGE = new Vector3f();
    private static final Vector3f PREV = new Vector3f();
    private static final Vector3f LAT = new Vector3f();
    private static final Quaternionf Q = new Quaternionf();
    private static final Quaternionf LOWER_Q = new Quaternionf();

    private ArmViewPose() { }

    /**
     * One arm's elbow direction across frames: turns about the arm line toward the hand-axis elbow at
     * {@code c (1 - exp(-dt / }{@link #BEND_TAU}{@code ))}, c = hand-axis confidence, at most {@link #BEND_SPEED}.
     * Inconclusive hand axis => elbow held, never a one-frame side switch.
     */
    public static final class Bend {
        private final Vector3f dir = new Vector3f();
        private boolean set;

        public void reset() {
            set = false;
        }
    }

    /** {@link #SHOULDER} of one side. */
    public static Vector3f shoulder(boolean left, Vector3f out) {
        return out.set(SHOULDER).mul(left ? -1 : 1, 1, 1);
    }

    /** Hand frame without authored orientation: arm +Y from the shoulder toward the wrist, minimal swing from rest. */
    public static Quaternionf towardWrist(Vector3fc wrist, Vector3fc shoulder, Quaternionf out) {
        DIR.set(wrist).sub(shoulder);
        if (DIR.lengthSquared() < 1e-10F) return out.set(REST);
        return out.set(REST).premul(Q.rotationTo(REST.transform(T.set(ARM_AXIS)), DIR.normalize()));
    }

    /**
     * Solves one arm to the fist end {@code wrist}. {@code hand}: hand frame (+Y forearm -> fist) or null. Elbow toward
     * where the forearm would lie along the hand's +Y, angle about the arm line from {@code pole} clamped to
     * [-{@link #ELBOW_IN}, {@link #ELBOW_OUT}]: from {@code pole} (or the held {@link Bend}) turned that way by
     * c x the angle between, c = |guess off the arm line| / elbow offset, clamped to 1; c = 1 toward {@code pole} without
     * {@code hand}. Both bones = hinge frames: +Y along the bone, +Z elbow-out, +X = bend-plane normal shared across
     * the elbow (pack hand roll never reaches the upper arm). {@code shoulder} is rewritten when the wrist is out of
     * reach, or nearer than {@code minReach} x arm length (shoulder slides back along the arm line instead of the elbow
     * folding). Joint frames out: {@code upper} at the
     * shoulder, {@code lower} at {@code elbow}. Returns forearm twist, rad: {@code hand} swung minimally onto the forearm,
     * about +Y from {@code lower}; 0 without {@code hand} ({@link PlayerArms#pose}).
     */
    public static float solve(Vector3fc wrist, @Nullable Quaternionfc hand, Vector3f shoulder, Vector3fc pole,
                      PlayerArms.Lengths lengths, Matrix4f upper, Matrix4f lower, Vector3f elbow) {
        return solve(wrist, hand, shoulder, pole, lengths, 0, upper, lower, elbow);
    }

    public static float solve(Vector3fc wrist, @Nullable Quaternionfc hand, Vector3f shoulder, Vector3fc pole,
                      PlayerArms.Lengths lengths, float minReach, Matrix4f upper, Matrix4f lower, Vector3f elbow) {
        return solve(wrist, hand, shoulder, pole, lengths, minReach, null, 0, upper, lower, elbow);
    }

    /** {@code bend}: this arm's elbow direction state, {@code dt} s since its last solve; null => unsmoothed. */
    public static float solve(Vector3fc wrist, @Nullable Quaternionfc hand, Vector3f shoulder, Vector3fc pole,
                      PlayerArms.Lengths lengths, float minReach, @Nullable Bend state, float dt, Matrix4f upper,
                      Matrix4f lower, Vector3f elbow) {
        float l1 = lengths.upper(), l2 = lengths.forearm();
        DIR.set(wrist).sub(shoulder);
        float d = DIR.length();
        if (d < 1e-6F) DIR.set(0, 0, -1); else DIR.div(d);
        float reach = Math.clamp(d, Math.max(Math.abs(l1 - l2) + 1e-4F, (l1 + l2) * minReach),
                (l1 + l2) * MAX_REACH);
        if (reach != d) shoulder.set(wrist).fma(-reach, DIR);
        float along = (l1 * l1 - l2 * l2 + reach * reach) / (2 * reach);
        float bend = (float) Math.sqrt(Math.max(l1 * l1 - along * along, 0));
        PERP.set(pole).fma(-pole.dot(DIR), DIR);
        if (PERP.lengthSquared() < 1e-10F) PERP.set(DIR.y, -DIR.x, 0);
        PERP.normalize();
        LAT.set(PERP).cross(DIR).mul(pole.x() < 0 ? -1 : 1);
        // guess/pole blend FORBIDDEN: opposed => cancels through the arm line => one-frame flip
        float rate = 1;
        boolean held = state != null && state.set;
        float from = 0;
        if (held) {
            PREV.set(state.dir).fma(-state.dir.dot(DIR), DIR);
            if (PREV.lengthSquared() > 1e-10F) from = (float) Math.atan2(PREV.dot(LAT), PREV.dot(PERP));
            rate *= 1 - (float) Math.exp(-dt / BEND_TAU);
        }
        float to = 0;
        if (hand != null) {
            hand.transform(T.set(ARM_AXIS)).mul(-l2).add(wrist).sub(shoulder);
            T.fma(-T.dot(DIR), DIR);
            float guess = T.length();
            rate *= guess > 1e-6F ? Math.min(1, guess / Math.max(bend, 1e-6F)) : 0;
            to = guess > 1e-6F ? (float) Math.atan2(T.dot(LAT), T.dot(PERP)) : from;
        }
        // outside the range => nearer bound, held side within ELBOW_HOLD of the far point; elbow goes round through
        // the range, never across the body. Held side alone => parks on a bound the guess has left (hk416 inspect)
        if (to < -ELBOW_IN || to > ELBOW_OUT) {
            float past = (to < -ELBOW_IN ? to + 2 * (float) Math.PI : to) - FAR;
            to = Math.abs(past) < ELBOW_HOLD ? (Math.abs(from + ELBOW_IN) < Math.abs(from - ELBOW_OUT) ? -ELBOW_IN
                    : ELBOW_OUT) : past > 0 ? -ELBOW_IN : ELBOW_OUT;
        }
        float turn = (to - from) * rate;
        if (held) turn = Math.clamp(turn, -BEND_SPEED * dt, BEND_SPEED * dt);
        float angle = from + turn;
        PERP.mul((float) Math.cos(angle)).fma((float) Math.sin(angle), LAT);
        if (state != null) {
            state.dir.set(PERP);
            state.set = true;
        }
        elbow.set(shoulder).fma(along, DIR).fma(bend, PERP);
        FOREARM.set(wrist).sub(elbow).normalize();
        UPPER.set(elbow).sub(shoulder).normalize();
        OUT.set(PERP).mul(along / l1).fma(-bend / l1, DIR);
        HINGE.set(UPPER).cross(OUT);
        upper.set(HINGE.x, HINGE.y, HINGE.z, 0, UPPER.x, UPPER.y, UPPER.z, 0, OUT.x, OUT.y, OUT.z, 0,
                shoulder.x(), shoulder.y(), shoulder.z(), 1);
        OUT.set(HINGE).cross(FOREARM);
        lower.set(HINGE.x, HINGE.y, HINGE.z, 0, FOREARM.x, FOREARM.y, FOREARM.z, 0, OUT.x, OUT.y, OUT.z, 0,
                elbow.x, elbow.y, elbow.z, 1);
        if (hand == null) return 0;
        LOWER_Q.set(hand).premul(Q.rotationTo(hand.transform(T.set(ARM_AXIS)), FOREARM));
        LOWER_Q.transform(T.set(1, 0, 0));
        return (float) Math.atan2(OUT.set(HINGE).cross(T).dot(FOREARM), HINGE.dot(T));
    }
}
