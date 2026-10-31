package com.wf.wflib.sight.api.client;

import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ScopeMathTest {
    private static final Vector3f X = new Vector3f(1, 0, 0);
    private static final Vector3f Y = new Vector3f(0, 1, 0);
    private static final Vector3f AXIS = new Vector3f(0, 0, -1);

    private static double tanHalf(float fovDeg) {
        return Math.tan(Math.toRadians(fovDeg) / 2);
    }

    @Test
    void scopePictureMagnifiesTheFieldBySurroundingsZoom() {
        float fieldTan = 0.2F;
        assertEquals(fieldTan, tanHalf(ScopeMath.fovDeg(fieldTan, 1)), 1e-6, "1x = what the field covers");
        assertEquals(fieldTan / 4.5, tanHalf(ScopeMath.fovDeg(fieldTan, 4.5F)), 1e-6);
    }

    @Test
    void turnSlowsByMagnificationOnTopOfWorldZoomWhileThePictureIsShown() {
        assertEquals(1.5F, ScopeMath.turnZoom(1.5F, 10, 1, false), "third person / no lens: world zoom");
        assertEquals(15F, ScopeMath.turnZoom(1.5F, 10, 1, true), 1e-6);
        assertEquals(8.25F, ScopeMath.turnZoom(1.5F, 10, 0.5F, true), 1e-6);
        assertEquals(1.5F, ScopeMath.turnZoom(1.5F, 10, 0, true), 1e-6);
    }

    @Test
    void shadowOpensAtThePupilAndClosesAway() {
        float[] out = new float[3];
        float r = 0.02F;
        ScopeMath.shadow(new Vector3f(), AXIS, X, Y, r, 0.1F, out);
        assertEquals(0, out[0], 1e-6);
        assertEquals(0, out[1], 1e-6);
        assertEquals(0.94F, out[2], 1e-6, "full picture, thin tube ring");
        // Pupil 0.02 in front of the eye: eye 20% too far back.
        ScopeMath.shadow(new Vector3f(0, 0, -0.02F), AXIS, X, Y, r, 0.1F, out);
        assertEquals(0.94F * (1 - 1.5F * 0.2F), out[2], 1e-5);
        ScopeMath.shadow(new Vector3f(0, 0, -0.1F), AXIS, X, Y, r, 0.1F, out);
        assertEquals(0, out[2], 1e-6, "twice the relief: black");
    }

    @Test
    void lateralEyeOffsetMovesTheHoleAway() {
        float[] out = new float[3];
        float r = 0.02F;
        // Pupil 0.35 r to the right of the eye => eye 0.35 r left of the axis => hole at the right rim.
        ScopeMath.shadow(new Vector3f(0.35F * r, 0, 0), AXIS, X, Y, r, 0.1F, out);
        assertEquals(1, out[0], 1e-5);
        assertEquals(0, out[1], 1e-6);
        // Picture axes rolled 90 deg: the same offset lands on picture y.
        ScopeMath.shadow(new Vector3f(0.35F * r, 0, 0), AXIS, Y, new Vector3f(-1, 0, 0), r, 0.1F, out);
        assertEquals(0, out[0], 1e-5);
        assertEquals(-1, out[1], 1e-5);
        // Axis tilted up, pupil on the eye: aligned; offset along the axis only changes relief.
        float a = 0.05F;
        Vector3f tilted = new Vector3f(0, (float) Math.sin(a), -(float) Math.cos(a));
        ScopeMath.shadow(new Vector3f(), tilted, X, new Vector3f(0, (float) Math.cos(a), (float) Math.sin(a)), r, 0.1F,
                out);
        assertEquals(0, out[1], 1e-6);
        ScopeMath.shadow(new Vector3f(tilted).mul(0.01F), tilted, X,
                new Vector3f(0, (float) Math.cos(a), (float) Math.sin(a)), r, 0.1F, out);
        assertEquals(0, out[1], 1e-5);
        assertEquals(0.94F * (1 - 1.5F * 0.1F), out[2], 1e-5);
    }

    @Test
    void zoomLevelsWrap() {
        assertEquals(1, ScopeMath.step(0, 2, 1));
        assertEquals(0, ScopeMath.step(1, 2, 1));
        assertEquals(1, ScopeMath.step(0, 2, -1));
        assertEquals(0, ScopeMath.step(0, 1, 1));
    }

    /**
     * PiP culling at the narrowest picture: vanilla's camera-cell fit ends (no bound in it: a narrower FOV spins the
     * render thread for good). Square picture, far = render distance x 64; camera anywhere in its cell (corners are the
     * worst case), any direction.
     */
    @Test
    void narrowestPictureStillCulls() {
        Random rnd = new Random(7);
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (int chunks : new int[]{2, 3, 4, 8, 12, 32}) {
                float far = chunks * 64;
                float fov = ScopeMath.minFovDeg(far);
                for (int i = 0; i < 4000; i++) {
                    // even i: camera hugging a cell corner (worst case)
                    double e = 1e-3;
                    double x = i % 2 == 0 ? rnd.nextInt(2) * (8 - 2 * e) + e : rnd.nextDouble() * 8;
                    double y = i % 2 == 0 ? rnd.nextInt(2) * (8 - 2 * e) + e : rnd.nextDouble() * 8;
                    double z = i % 2 == 0 ? rnd.nextInt(2) * (8 - 2 * e) + e : rnd.nextDouble() * 8;
                    float yaw = rnd.nextFloat() * 360, pitch = rnd.nextFloat() * 170 - 85;
                    Matrix4f view = new Matrix4f().rotateX((float) Math.toRadians(pitch))
                            .rotateY((float) Math.toRadians(yaw));
                    Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fov), 1, 0.05F, far);
                    Frustum f = new Frustum(view, proj);
                    f.prepare(x + 64, y - 64, z + 8);
                    f.offsetToFullyIncludeCameraCube(8);
                }
            }
        });
    }

    /** Pass widened to the floor, lens shows its centre: same angle across the lens => same magnification. */
    @Test
    void cropKeepsMagnification() {
        float far = 2 * 64;
        float fov = 1.4F;
        float pass = ScopeMath.passFovDeg(fov, far);
        assertEquals(ScopeMath.minFovDeg(far), pass);
        float k = ScopeMath.crop(fov, pass);
        assertEquals(Math.tan(Math.toRadians(fov) / 2), k * Math.tan(Math.toRadians(pass) / 2), 1e-7);
        assertEquals(1, ScopeMath.crop(20, ScopeMath.passFovDeg(20, far)), 0, "above the floor: whole picture");
    }

    /**
     * Absolute ADS FOV: at user FOV 90 and 110 the ADS world FOV is ref / zoom (sight picture identical); below ref
     * unchanged (70 / zoom); hold divides on top; PiP picture FOV identical above ref.
     */
    @Test
    void adsFovIsAbsoluteAboveTheReference() {
        float ref = 80, zoom = 1.3F;
        for (float user : new float[]{90, 110}) {
            float d = ScopeMath.worldDivisor(zoom, ScopeMath.refZoom(user, ref), 1, 1);
            assertEquals(ref / zoom, user / d, 1e-4F, "user " + user);
            assertEquals(user, user / ScopeMath.worldDivisor(zoom, ScopeMath.refZoom(user, ref), 0, 1), 1e-4F, "hip");
            float hold = ScopeMath.worldDivisor(zoom, ScopeMath.refZoom(user, ref), 1, 1.4F);
            assertEquals(ref / zoom / 1.4F, user / hold, 1e-4F, "hold on top");
        }
        assertEquals(70 / zoom, 70 / ScopeMath.worldDivisor(zoom, ScopeMath.refZoom(70, ref), 1, 1), 1e-4F);
        float mid = 110 / ScopeMath.worldDivisor(zoom, ScopeMath.refZoom(110, ref), 0.5F, 1);
        assertEquals(true, mid < 110 && mid > ref / zoom, "eased");
    }

    /** Ctrl + wheel: geometric per notch, clamped to the listed range in either list order. */
    @Test
    void wheelAdjustClampsToListedRange() {
        java.util.List<Float> vudu = java.util.List.of(6.5F, 1.35F);
        assertEquals(6.5F / (1.15F * 1.15F * 1.15F), ScopeMath.adjust(6.5F, vudu, -3), 1e-4F);
        assertEquals(1.35F, ScopeMath.adjust(2F, vudu, -20), 0);
        assertEquals(6.5F, ScopeMath.adjust(2F, vudu, 30), 0);
    }
}
