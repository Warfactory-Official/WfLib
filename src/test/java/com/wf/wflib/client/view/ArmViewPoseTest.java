package com.wf.wflib.client.view;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmViewPoseTest {
    private static final float PX = 0.9375F / 16F;
    private static final float PISTOL_REACH = 0.92F;

    /** Classic right arm: {@code PlayerModel} box (-3, -2, -2) 4 x 12 x 4 at pivot (-5, 2, 0). */
    private static ModelPart rightArm() {
        ModelPart arm = new ModelPart(List.of(new ModelPart.Cube(40, 16, -3, -2, -2, 4, 12, 4, 0, 0, 0, false, 64, 64,
                EnumSet.allOf(Direction.class))), Map.of());
        arm.setInitialPose(PartPose.offset(-5, 2, 0));
        return arm;
    }

    @Test
    void shoulderIsTheVanillaPivotBelowAndBesideAStandingEye() {
        Vector3f shoulder = ArmViewPose.shoulder(rightArm(), new Vector3f());
        assertEquals(5 * PX, shoulder.x, 1e-6F);
        assertEquals(0.9375F * (1.501F - 2F / 16F) - 1.62F, shoulder.y, 1e-6F);
        assertEquals(0, shoulder.z, 1e-6F);
        assertEquals(0, shoulder.distance(ArmViewPose.shoulder(false, new Vector3f())), 1e-6F);
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

    /** Hand turned through a full roll about the arm line: elbow stays on the pole side, never jumps between frames. */
    @Test
    void handRollNeverFlipsTheElbow() {
        Vector3f fist = new Vector3f(0.12F, -0.15F, -0.35F);
        Vector3f shoulder = ArmViewPose.shoulder(false, new Vector3f());
        Vector3f line = new Vector3f(fist).sub(shoulder).normalize();
        Vector3f elbow = new Vector3f(), last = null;
        Vector3f pole = new Vector3f(ArmViewPose.RIGHT_POLE);
        pole.fma(-pole.dot(line), line);
        for (int i = 0; i <= 720; i++) {
            Quaternionf hand = new Quaternionf().rotateAxis(i * (float) Math.PI / 360, line).rotateX(-1.3F);
            ArmViewPose.solve(fist, hand, ArmViewPose.shoulder(false, new Vector3f()), ArmViewPose.RIGHT_POLE,
                    LENGTHS, new Matrix4f(), new Matrix4f(), elbow);
            Vector3f off = new Vector3f(elbow).sub(shoulder);
            assertTrue(off.fma(-off.dot(line), line).dot(pole) > 0, "pole side at step " + i);
            if (last != null) assertTrue(elbow.distance(last) < 0.01F, "elbow step " + elbow.distance(last) + " at " + i);
            last = new Vector3f(elbow);
        }
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
