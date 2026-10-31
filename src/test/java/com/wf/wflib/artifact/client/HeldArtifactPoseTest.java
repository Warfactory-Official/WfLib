package com.wf.wflib.artifact.client;

import com.wf.wflib.view.api.ArmViewPose;
import com.wf.wflib.view.api.ForearmTwist;
import com.wf.wflib.view.api.PlayerArms;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held artefact's arm, solved and skinned as {@link PlayerArms} does it: the fist frame read back, so "palm up"
 * means the right fist's +X (its palm face in {@code player_arms.glb}) really points up, not that a roll looked right.
 */
class HeldArtifactPoseTest {
    private static final PlayerArms.Lengths LENGTHS = new PlayerArms.Lengths(0.26F, 0.33F);
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    /** Forearm twist left after the pose, rad: roll - base (the corkscrew down the twist joints). */
    private static float corkscrew;

    /** The right arm posed for {@code pose}: [upper, lower, fist] globals, view space. */
    private static Matrix4f[] solve(HeldArtifactPose pose) {
        Matrix4f upper = new Matrix4f();
        Matrix4f lower = new Matrix4f();
        Vector3f shoulder = new Vector3f(pose.shoulder);
        float twist = ArmViewPose.solve(pose.fist, pose.hand, shoulder, ArmViewPose.RIGHT_POLE, LENGTHS, 0,
                ArmViewPose.LOCKED, null, 0, upper, lower, new Vector3f());
        ForearmTwist forearm = new ForearmTwist();
        forearm.update(twist, ArmViewPose.swing(pose.hand, lower), HeldArtifactPose.TWIST_LIMIT, 0);
        corkscrew = forearm.roll() - forearm.base();
        Matrix4f upperOut = new Matrix4f();
        Matrix4f lowerOut = new Matrix4f();
        Matrix4f fist = new Matrix4f();
        Matrix4f[] twists = new Matrix4f[PlayerArms.TWISTS - 1];
        for (int k = 0; k < twists.length; k++) {
            twists[k] = new Matrix4f();
        }
        PlayerArms.joints(upper, lower, forearm.roll(), forearm.base(), LENGTHS.forearm(), upperOut, lowerOut,
                twists, fist);
        return new Matrix4f[]{upperOut, lowerOut, fist};
    }

    private static Vector3f axis(Matrix4f m, int column) {
        return m.getColumn(column, new Vector3f()).normalize();
    }

    private static double degrees(Vector3f a, Vector3f b) {
        return Math.toDegrees(a.angle(b));
    }

    @Test
    void heldOutTheArmIsStraightThePalmFacesUpAndTheArtefactIsClearAboveIt() {
        HeldArtifactPose pose = new HeldArtifactPose().pose(1, 0, LENGTHS);
        Matrix4f[] arm = solve(pose);
        double bend = degrees(axis(arm[0], 1), axis(arm[1], 1));
        assertTrue(bend < 2.0, "elbow bent " + bend + " deg");
        // No corkscrew: the skin's forearm box stays square, the whole arm turns.
        assertTrue(Math.abs(Math.toDegrees(corkscrew)) < 1.0, "forearm twisted " + Math.toDegrees(corkscrew) + " deg");
        double palm = degrees(axis(arm[2], 0), UP);
        assertTrue(palm < 10.0, "palm (+X) is " + palm + " deg off up");
        Vector3f fistAt = arm[2].getTranslation(new Vector3f());
        assertTrue(fistAt.distance(pose.fist) < 0.002F, "fist " + fistAt + " missed " + pose.fist);
        // Underside of the artefact over the top of the hand, at the lowest of its bob.
        float clear = (pose.artifact.y - HeldArtifactPose.HALF_HEIGHT) - (pose.palm.y + HeldArtifactPose.HAND_TOP);
        assertTrue(clear >= HeldArtifactPose.GAP - HeldArtifactPose.BOB - 1e-4F, "only " + clear + " m clear");
        Vector3f off = new Vector3f(pose.artifact).sub(pose.palm);
        assertTrue(Math.hypot(off.x, off.z) < 0.02, "not over the palm: " + off);
    }

    @Test
    void liftingItTheArmStaysStraightThePalmTurnsUpAndItNeverSinksIntoTheHand() {
        for (int k = 0; k <= 10; k++) {
            float lift = k / 10.0F;
            HeldArtifactPose pose = new HeldArtifactPose().pose(lift, 1.7F * k, LENGTHS);
            Matrix4f[] arm = solve(pose);
            double bend = degrees(axis(arm[0], 1), axis(arm[1], 1));
            assertTrue(bend < 2.0, "lift " + lift + ": elbow bent " + bend + " deg");
            assertTrue(Math.abs(Math.toDegrees(corkscrew)) < 1.0, "lift " + lift + ": forearm twisted "
                    + Math.toDegrees(corkscrew) + " deg");
            // The palm faces the artefact, whatever the arm's angle.
            Vector3f toArtifact = new Vector3f(pose.artifact).sub(pose.palm).normalize();
            double palm = degrees(axis(arm[2], 0), toArtifact);
            assertTrue(palm < 10.0, "lift " + lift + ": palm " + palm + " deg off the artefact");
            float reach = new Vector3f(pose.artifact).sub(pose.palm).length();
            assertTrue(reach >= HeldArtifactPose.HAND_TOP + HeldArtifactPose.HALF_HEIGHT - 1e-4F,
                    "lift " + lift + ": artefact into the hand");
        }
    }

    @Test
    void putAwayTheHandAndTheArtefactAreBelowTheView() {
        HeldArtifactPose pose = new HeldArtifactPose().pose(0, 0, LENGTHS);
        // Vanilla's hand pass: 70 degrees vertical, so below y/-z = -tan(35 deg) is out of sight.
        double edge = -Math.tan(Math.toRadians(35));
        Vector3f top = new Vector3f(pose.artifact).add(0, HeldArtifactPose.HALF_HEIGHT, 0);
        assertTrue(pose.fist.z >= 0 || pose.fist.y / -pose.fist.z < edge, "fist in view: " + pose.fist);
        assertTrue(top.z >= 0 || top.y / -top.z < edge, "artefact in view: " + top);
    }
}
