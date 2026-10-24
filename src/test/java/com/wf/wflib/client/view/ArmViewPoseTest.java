package com.wf.wflib.client.view;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmViewPoseTest {
    private static final float PISTOL_REACH = 0.92F;

    @Test
    void shouldersMirror() {
        Vector3f right = ArmViewPose.shoulder(false, new Vector3f()), left = ArmViewPose.shoulder(true, new Vector3f());
        assertEquals(0, right.distance(ArmViewPose.SHOULDER.x(), ArmViewPose.SHOULDER.y(), ArmViewPose.SHOULDER.z()), 0);
        assertEquals(0, left.distance(-right.x, right.y, right.z), 0);
    }

    private static final PlayerArms.Lengths LENGTHS = new PlayerArms.Lengths(0.26F, 0.33F);

    /** Pistol: fist near the eye => shoulder slides back along the arm line, arm near straight, fist kept. */
    @Test
    void pistolMinimumReachMovesTheShoulderNotTheElbow() {
        Vector3f shoulder = ArmViewPose.shoulder(false, new Vector3f());
        Vector3f fist = new Vector3f(0.08F, -0.12F, -0.25F);
        Vector3f line = new Vector3f(fist).sub(shoulder).normalize();
        Vector3f elbow = new Vector3f();
        Matrix4f lower = new Matrix4f();
        ArmViewPose.solve(fist, null, shoulder, ArmViewPose.RIGHT_POLE, LENGTHS, PISTOL_REACH,
                new Matrix4f(), lower, elbow);
        float arm = LENGTHS.upper() + LENGTHS.forearm();
        assertEquals(PISTOL_REACH * arm, shoulder.distance(fist), 1e-5F, "reach floor");
        assertEquals(0, new Vector3f(fist).sub(shoulder).normalize().distance(line), 1e-5F, "along the arm line");
        assertEquals(0, lower.transformPosition(new Vector3f(0, LENGTHS.forearm(), 0)).distance(fist), 1e-5F);
    }

    @Test
    void reachableFistKeepsSegmentLengthsAndHandRoll() {
        Vector3f shoulder = ArmViewPose.shoulder(false, new Vector3f());
        Vector3f start = new Vector3f(shoulder);
        Vector3f fist = new Vector3f(0.12F, -0.15F, -0.35F);
        Quaternionf hand = new Quaternionf().rotateX(-1.3F).rotateZ(0.4F);
        Matrix4f upper = new Matrix4f(), lower = new Matrix4f();
        Vector3f elbow = new Vector3f();
        float twist = ArmViewPose.solve(fist, hand, shoulder, ArmViewPose.RIGHT_POLE, LENGTHS, upper, lower, elbow);

        assertEquals(0, shoulder.distance(start), 1e-6F, "reachable => shoulder planted");
        assertEquals(LENGTHS.upper(), elbow.distance(shoulder), 1e-5F);
        assertEquals(LENGTHS.forearm(), fist.distance(elbow), 1e-5F);
        assertEquals(0, lower.transformPosition(new Vector3f(0, LENGTHS.forearm(), 0)).distance(fist), 1e-5F,
                "fist end on the goal");
        assertEquals(0, upper.transformPosition(new Vector3f()).distance(shoulder), 1e-6F);
        Vector3f forearm = new Vector3f(fist).sub(elbow).normalize();
        Vector3f handAxis = hand.transform(new Vector3f(0, 1, 0));
        Vector3f handSide = hand.transform(new Vector3f(1, 0, 0));
        Vector3f expectedSide = new Quaternionf().rotationTo(handAxis, forearm).transform(handSide);
        assertEquals(0, new Matrix4f(lower).rotateY(twist).transformDirection(new Vector3f(1, 0, 0))
                .distance(expectedSide), 1e-5F, "lower x Ry(twist) = hand frame swung onto the forearm");
        Vector3f hinge = upper.transformDirection(new Vector3f(1, 0, 0));
        assertEquals(0, lower.transformDirection(new Vector3f(1, 0, 0)).distance(hinge), 1e-5F, "one hinge axis");
        assertEquals(0, hinge.dot(forearm), 1e-5F, "hinge normal to the bend plane");
        Vector3f line = new Vector3f(fist).sub(shoulder).normalize();
        Vector3f off = new Vector3f(elbow).sub(shoulder);
        off.fma(-off.dot(line), line);
        assertTrue(upper.transformDirection(new Vector3f(0, 0, 1)).dot(off) > 0, "+Z elbow-out");
        assertTrue(elbow.y < fist.y, "elbow below the hand");
    }

    @Test
    void outOfReachSlidesTheShoulderAndKeepsTheArmLength() {
        Vector3f shoulder = ArmViewPose.shoulder(false, new Vector3f());
        Vector3f fist = new Vector3f(-0.1F, -0.1F, -0.9F);
        Vector3f elbow = new Vector3f();
        ArmViewPose.solve(fist, null, shoulder, ArmViewPose.RIGHT_POLE, LENGTHS, new Matrix4f(), new Matrix4f(), elbow);
        assertEquals((LENGTHS.upper() + LENGTHS.forearm()) * ArmViewPose.MAX_REACH, fist.distance(shoulder), 1e-4F);
        assertEquals(LENGTHS.upper(), elbow.distance(shoulder), 1e-5F);
    }

    /** Hand turned through a full roll about the arm line, held state: elbow at most BEND_SPEED, settles on the hand axis. */
    @Test
    void handRollTurnsTheElbowWithoutJumps() {
        Vector3f fist = new Vector3f(0.12F, -0.15F, -0.35F);
        Vector3f shoulder = ArmViewPose.shoulder(false, new Vector3f());
        Vector3f line = new Vector3f(fist).sub(shoulder).normalize();
        ArmViewPose.Bend bend = new ArmViewPose.Bend();
        Vector3f elbow = new Vector3f(), last = null;
        for (int i = -60; i <= 720; i++) {
            Quaternionf hand = new Quaternionf().rotateAxis(Math.max(i, 0) * (float) Math.PI / 360, line).rotateX(-1.3F);
            ArmViewPose.solve(fist, hand, ArmViewPose.shoulder(false, new Vector3f()), ArmViewPose.RIGHT_POLE,
                    LENGTHS, 0, bend, 1 / 60F, new Matrix4f(), new Matrix4f(), elbow);
            if (i > 0) assertTrue(elbow.distance(last) < ArmViewPose.BEND_SPEED / 60 * LENGTHS.upper() + 1e-4F,
                    "elbow step " + elbow.distance(last) + " at " + i);
            last = new Vector3f(elbow);
        }
        Vector3f pole = new Vector3f(ArmViewPose.RIGHT_POLE);
        Vector3f p = new Vector3f(pole).fma(-pole.dot(line), line).normalize();
        Vector3f out = new Vector3f(p).cross(line), target = new Vector3f();
        float d = fist.distance(shoulder), l1 = LENGTHS.upper(), l2 = LENGTHS.forearm();
        float along = (l1 * l1 - l2 * l2 + d * d) / (2 * d), within = (float) Math.toRadians(40);
        target.set(shoulder).fma(along, line).fma((float) Math.sqrt(l1 * l1 - along * along),
                p.mul((float) Math.cos(within)).fma((float) Math.sin(within), out));
        Quaternionf hand = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(fist).sub(target));
        for (int i = 0; i < 60; i++) {
            ArmViewPose.solve(fist, hand, ArmViewPose.shoulder(false, new Vector3f()), ArmViewPose.RIGHT_POLE,
                    LENGTHS, 0, bend, 1 / 60F, new Matrix4f(), new Matrix4f(), elbow);
        }
        assertEquals(within, elbowAngle(fist, shoulder, elbow, pole), 1e-3F, "settled on the hand axis");
    }

    /** Angle of the elbow about the arm line from {@code pole}, + = away from the body. */
    private static float elbowAngle(Vector3f fist, Vector3f shoulder, Vector3f elbow, Vector3f pole) {
        Vector3f line = new Vector3f(fist).sub(shoulder).normalize();
        Vector3f p = new Vector3f(pole).fma(-pole.dot(line), line).normalize();
        Vector3f lat = new Vector3f(p).cross(line).mul(pole.x < 0 ? -1 : 1);
        Vector3f off = new Vector3f(elbow).sub(shoulder);
        off.fma(-off.dot(line), line);
        return (float) Math.atan2(off.dot(lat), off.dot(p));
    }

    /** ma_bolt mosin cycle rest (traced): fist tucked before the shoulder => hand-axis side in range, elbow down. */
    @Test
    void tuckedHoldFollowsTheHandWithTheElbowDown() {
        Vector3f fist = new Vector3f(0.14F, -0.18F, -0.15F);
        Vector3f axis = new Vector3f(-0.11F, -0.06F, -0.99F).normalize();
        Quaternionf hand = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis);
        Vector3f elbow = new Vector3f(), shoulder = ArmViewPose.shoulder(false, new Vector3f());
        ArmViewPose.solve(fist, hand, shoulder, ArmViewPose.RIGHT_POLE, LENGTHS, new Matrix4f(), new Matrix4f(), elbow);
        float angle = elbowAngle(fist, shoulder, elbow, new Vector3f(ArmViewPose.RIGHT_POLE));
        assertTrue(angle > -ArmViewPose.ELBOW_IN + 0.1F && angle < ArmViewPose.ELBOW_OUT - 0.1F, "unclamped " + angle);
        assertTrue(elbow.y < shoulder.y - 0.1F, "elbow below the shoulder");
    }

    /** sr2m reloadEmpty rest (traced): right forearm down the bore; mirrored for the left arm. */
    @Test
    void forearmDownTheBoreHangsTheElbowAtTheInwardBound() {
        Quaternionf hand = new Quaternionf(0.34258142F, -0.5786857F, 0.5822223F, -0.45692235F);
        for (boolean left : new boolean[]{false, true}) {
            Vector3f fist = new Vector3f(0.1146611F, -0.16149883F, -0.19931823F);
            Quaternionf q = new Quaternionf(hand);
            Vector3f pole = new Vector3f(ArmViewPose.RIGHT_POLE);
            if (left) {
                fist.x = -fist.x;
                q.set(q.x, -q.y, -q.z, q.w);
                pole.set(ArmViewPose.LEFT_POLE);
            }
            Vector3f shoulder = ArmViewPose.shoulder(left, new Vector3f()), elbow = new Vector3f();
            ArmViewPose.Bend bend = new ArmViewPose.Bend();
            for (int i = 0; i < 60; i++) {
                ArmViewPose.solve(fist, q, shoulder, pole, LENGTHS, 0, bend, 1 / 60F, new Matrix4f(), new Matrix4f(),
                        elbow);
            }
            assertEquals(-ArmViewPose.ELBOW_IN, elbowAngle(fist, shoulder, elbow, pole), 1e-3F, "left " + left);
            assertTrue(elbow.y < shoulder.y, "elbow below the shoulder, left " + left);
        }
    }

    /** Hand axis swept round through the out-of-range side: elbow stays on its bound, crosses only through the range. */
    @Test
    void elbowNeverCrossesTheBody() {
        Vector3f fist = new Vector3f(0.12F, -0.15F, -0.35F);
        Vector3f shoulder = ArmViewPose.shoulder(false, new Vector3f());
        Vector3f line = new Vector3f(fist).sub(shoulder).normalize();
        Vector3f pole = new Vector3f(ArmViewPose.RIGHT_POLE);
        ArmViewPose.Bend bend = new ArmViewPose.Bend();
        Vector3f elbow = new Vector3f();
        for (int i = 0; i <= 720; i++) {
            Quaternionf hand = new Quaternionf().rotateAxis(-i * (float) Math.PI / 360, line).rotateX(-1.3F);
            ArmViewPose.solve(fist, hand, ArmViewPose.shoulder(false, new Vector3f()), pole, LENGTHS, 0, bend, 1 / 60F,
                    new Matrix4f(), new Matrix4f(), elbow);
            float angle = elbowAngle(fist, shoulder, elbow, pole);
            assertTrue(angle >= -ArmViewPose.ELBOW_IN - 1e-3F && angle <= ArmViewPose.ELBOW_OUT + 1e-3F,
                    "angle " + Math.toDegrees(angle) + " at " + i);
        }
    }

    /** Hand +Y along the arm line: no elbow side in it => held elbow stays. */
    @Test
    void inconclusiveHandAxisHoldsTheElbow() {
        Vector3f fist = new Vector3f(0.12F, -0.15F, -0.35F);
        Vector3f shoulder = ArmViewPose.shoulder(false, new Vector3f());
        ArmViewPose.Bend bend = new ArmViewPose.Bend();
        Vector3f first = new Vector3f(), elbow = new Vector3f();
        ArmViewPose.solve(fist, null, ArmViewPose.shoulder(false, new Vector3f()), ArmViewPose.RIGHT_POLE, LENGTHS, 0,
                bend, 1 / 60F, new Matrix4f(), new Matrix4f(), first);
        Quaternionf hand = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(fist).sub(shoulder).normalize());
        Vector3f flipped = new Vector3f(ArmViewPose.RIGHT_POLE).mul(-1, 1, 1);
        for (int i = 0; i < 60; i++) {
            ArmViewPose.solve(fist, hand, ArmViewPose.shoulder(false, new Vector3f()), flipped, LENGTHS, 0, bend,
                    1 / 60F, new Matrix4f(), new Matrix4f(), elbow);
        }
        assertEquals(0, elbow.distance(first), 1e-4F);
    }

    /** Target side switch (pole flipped across the arm line): held state sweeps over frames, settles on the target. */
    @Test
    void bendStateSweepsASideSwitch() {
        Vector3f fist = new Vector3f(0.12F, -0.15F, -0.35F);
        ArmViewPose.Bend bend = new ArmViewPose.Bend();
        Vector3f elbow = new Vector3f(), last = new Vector3f(), free = new Vector3f();
        ArmViewPose.solve(fist, null, ArmViewPose.shoulder(false, new Vector3f()), ArmViewPose.RIGHT_POLE, LENGTHS, 0,
                bend, 1 / 60F, new Matrix4f(), new Matrix4f(), last);
        Vector3f flipped = new Vector3f(ArmViewPose.RIGHT_POLE).mul(-1, 1, 1);
        ArmViewPose.solve(fist, null, ArmViewPose.shoulder(false, new Vector3f()), flipped, LENGTHS, new Matrix4f(),
                new Matrix4f(), free);
        float worst = 0, total = last.distance(free);
        for (int i = 0; i < 60; i++) {
            ArmViewPose.solve(fist, null, ArmViewPose.shoulder(false, new Vector3f()), flipped, LENGTHS, 0, bend,
                    1 / 60F, new Matrix4f(), new Matrix4f(), elbow);
            worst = Math.max(worst, elbow.distance(last));
            last.set(elbow);
        }
        assertTrue(worst < 0.4F * total, "per-frame step " + worst + " of " + total);
        assertEquals(0, elbow.distance(free), 1e-3F, "settled");
    }
}
