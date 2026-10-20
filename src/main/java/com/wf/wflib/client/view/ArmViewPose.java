package com.wf.wflib.client.view;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * First-person player arm solve ({@link PlayerArms} model): shoulder = vanilla arm part pivot against a standing eye;
 * joints upper (shoulder) -> lower (elbow, forearm + fist rigid) -> fist end = wrist goal. Joint frames: +Y down the bone;
 * hanging rest = Rz(pi) in view space. Not thread-safe (scratch).
 */
public final class ArmViewPose {
    /** Vanilla shoulder placement: model px, m (PlayerRenderer 0.9375 scale). */
    public static final float PX = 0.9375F / 16F;
    /** LivingEntityRenderer model origin offset, blocks; standing eye height, m. */
    private static final float MODEL_OFFSET = 1.501F, EYE_HEIGHT = 1.62F;
    /** Fraction of full reach the solver bends to; past it the shoulder slides toward the wrist (arm keeps length). */
    public static final float MAX_REACH = 0.995F;
    /** Natural elbow direction, view space; blended under the hand-axis preference ({@link #POLE_BIAS}). */
    public static final Vector3fc RIGHT_POLE = new Vector3f(0.6F, -1, 0.2F);
    public static final Vector3fc LEFT_POLE = new Vector3f(-0.6F, -1, 0.2F);
    public static final float POLE_BIAS = 0.5F;
    private static final Vector3fc ARM_AXIS = new Vector3f(0, 1, 0);
    private static final Quaternionfc REST = new Quaternionf().rotationZ((float) Math.PI);

    private static final Vector3f DIR = new Vector3f();
    private static final Vector3f PERP = new Vector3f();
    private static final Vector3f T = new Vector3f();
    private static final Vector3f FOREARM = new Vector3f();
    private static final Vector3f UPPER = new Vector3f();
    private static final Vector3f OUT = new Vector3f();
    private static final Vector3f HINGE = new Vector3f();
    private static final Quaternionf Q = new Quaternionf();
    private static final Quaternionf LOWER_Q = new Quaternionf();

    private ArmViewPose() { }

    /** Vanilla shoulder pivot of {@code arm} (initial pose) from a standing eye, view space, m. */
    public static Vector3f shoulder(ModelPart arm, Vector3f out) {
        PartPose pose = arm.getInitialPose();
        return out.set(-pose.x * PX, 0.9375F * (MODEL_OFFSET - pose.y / 16F) - EYE_HEIGHT, pose.z * PX);
    }

    /** Classic-model shoulder ({@code PlayerModel} arm offset (+-5, 2, 0) px) for callers without the skin's part. */
    public static Vector3f shoulder(boolean left, Vector3f out) {
        return out.set((left ? -5 : 5) * PX, 0.9375F * (MODEL_OFFSET - 2F / 16F) - EYE_HEIGHT, 0);
    }

    /** Hand frame without authored orientation: arm +Y from the shoulder toward the wrist, minimal swing from rest. */
    public static Quaternionf towardWrist(Vector3fc wrist, Vector3fc shoulder, Quaternionf out) {
        DIR.set(wrist).sub(shoulder);
        if (DIR.lengthSquared() < 1e-10F) return out.set(REST);
        return out.set(REST).premul(Q.rotationTo(REST.transform(T.set(ARM_AXIS)), DIR.normalize()));
    }

    /**
     * Solves one arm to the fist end {@code wrist}. {@code hand}: hand frame (+Y forearm -> fist) or null. Elbow pole =
     * where the forearm would lie along the hand's +Y, biased toward {@code pole}. Both bones = hinge frames: +Y along
     * the bone, +Z elbow-out, +X = bend-plane normal shared across the elbow (pack hand roll never reaches the upper
     * arm). {@code shoulder} is rewritten when the wrist is out of reach, or nearer than {@code minReach} x arm length
     * (shoulder slides back along the arm line instead of the elbow folding). Joint frames out: {@code upper} at the
     * shoulder, {@code lower} at {@code elbow}. Returns forearm twist, rad: {@code hand} swung minimally onto the forearm,
     * about +Y from {@code lower}; 0 without {@code hand} ({@link PlayerArms#pose}).
     */
    public static float solve(Vector3fc wrist, @Nullable Quaternionfc hand, Vector3f shoulder, Vector3fc pole,
                      PlayerArms.Lengths lengths, Matrix4f upper, Matrix4f lower, Vector3f elbow) {
        return solve(wrist, hand, shoulder, pole, lengths, 0, upper, lower, elbow);
    }

    public static float solve(Vector3fc wrist, @Nullable Quaternionfc hand, Vector3f shoulder, Vector3fc pole,
                      PlayerArms.Lengths lengths, float minReach, Matrix4f upper, Matrix4f lower, Vector3f elbow) {
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
        float poleLength = PERP.length();
        if (poleLength > 1e-6F) PERP.mul(POLE_BIAS / poleLength);
        if (hand != null) {
            hand.transform(T.set(ARM_AXIS)).mul(-l2).add(wrist).sub(shoulder);
            PERP.add(T.fma(-T.dot(DIR), DIR).div(l1));
        }
        if (PERP.lengthSquared() < 1e-10F) PERP.set(DIR.y, -DIR.x, 0);
        PERP.normalize();
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
