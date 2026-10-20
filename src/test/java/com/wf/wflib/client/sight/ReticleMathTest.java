package com.wf.wflib.client.sight;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReticleMathTest {
    private static final float LENS_Z = -0.25F, LENS_R = 0.012F;

    /** Drawn sight: F (axis -Z through the eye, window at LENS_Z) moved by sway {@code yaw / pitch} deg + {@code t} m. */
    private static final class Sight {
        final Vector3f c = new Vector3f(), u = new Vector3f(), v = new Vector3f();
        final Vector3f a = new Vector3f(), x = new Vector3f(), y = new Vector3f();

        Sight(float yawDeg, float pitchDeg, Vector3f t) {
            Matrix4f g = new Matrix4f().translate(t).rotateY((float) Math.toRadians(yawDeg))
                    .rotateX((float) Math.toRadians(pitchDeg));
            g.transformPosition(c.set(0, 0, LENS_Z));
            g.transformDirection(u.set(LENS_R, 0, 0));
            g.transformDirection(v.set(0, LENS_R, 0));
            g.transformDirection(a.set(0, 0, -1));
            g.transformDirection(x.set(1, 0, 0));
            g.transformDirection(y.set(0, 1, 0));
        }

        /** Where the eye ray along direction {@code d} crosses the window plane. */
        Vector3f hit(Vector3f d) {
            return new Vector3f(d).mul(c.dot(a) / d.dot(a));
        }
    }

    @Test
    void angularSizeToPixels() {
        float t120 = ReticleMath.halfTan(120, 70);
        assertEquals(Math.tan(Math.toRadians(1)), t120, 1e-7, "120 MOA = 2 deg wide");
        assertEquals(Math.tan(Math.toRadians(1)) / Math.tan(Math.toRadians(35)) * 1080,
                ReticleMath.imagePx(t120, 70, 1080), 1e-3);
        // Same reticle through 3x world zoom (FOV / 3): px x tan(35) / tan(35 / 3).
        float z3 = 70F / 3;
        assertEquals(Math.tan(Math.toRadians(35)) / Math.tan(Math.toRadians(35F / 3)),
                ReticleMath.imagePx(ReticleMath.halfTan(120, z3), z3, 1080) / ReticleMath.imagePx(t120, 70, 1080),
                1e-4);
        // 8 MOA dot image = 2 px at 1x => raised to the floor, scaled with the window.
        assertEquals(ReticleMath.MIN_PX_1080, ReticleMath.imagePx(ReticleMath.halfTan(8, 70), 70, 1080), 1e-3);
        assertEquals(2 * ReticleMath.MIN_PX_1080, ReticleMath.imagePx(ReticleMath.halfTan(8, 70), 70, 2160), 1e-3);
        assertTrue(ReticleMath.imagePx(ReticleMath.halfTan(400, z3), z3, 1080) > 300, "ACOG image unclamped");
    }

    @Test
    void reticleStaysOnTheSightLineUnderSwayAndParallax() {
        float half = ReticleMath.halfTan(8, 70);
        float[] uv = new float[2];
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9, 0.05F, 1000);
        Vector3f[] offsets = {new Vector3f(), new Vector3f(0.004F, -0.003F, 0.01F), new Vector3f(-0.006F, 0.002F, 0)};
        for (Vector3f t : offsets) {
            Sight s = new Sight(2.5F, -1.5F, t);
            Vector3f centre = s.hit(s.a);
            ReticleMath.uv(centre, s.a, s.x, s.y, half, uv);
            assertEquals(0.5F, uv[0], 1e-6F, "image centre on the axis ray, offset " + t);
            assertEquals(0.5F, uv[1], 1e-6F);
            // Drawn centre and the sight line's vanishing point land on the same pixel.
            Vector4f p = proj.transform(new Vector4f(centre, 1));
            Vector4f q = proj.transform(new Vector4f(s.a, 0));
            assertEquals(q.x / q.w, p.x / p.w, 1e-6F);
            assertEquals(q.y / q.w, p.y / p.w, 1e-6F);
        }
        // Target 1.5 MOA right of the sight line: same image point whatever the eye offset (collimated).
        Sight s0 = new Sight(2.5F, -1.5F, offsets[0]);
        Sight s1 = new Sight(2.5F, -1.5F, offsets[1]);
        double off = 1.5 * ReticleMath.MOA_RAD;
        Vector3f d = new Vector3f(s0.a).mul((float) Math.cos(off)).fma((float) Math.sin(off), s0.x);
        ReticleMath.uv(s0.hit(d), s0.a, s0.x, s0.y, half, uv);
        float u0 = uv[0];
        ReticleMath.uv(s1.hit(d), s1.a, s1.x, s1.y, half, uv);
        assertEquals(u0, uv[0], 1e-6F, "parallax-free");
        assertEquals(0.5 + Math.tan(off) / (2 * half), u0, 1e-5);
    }

    @Test
    void interpolationOverTheWindowIsExact() {
        Sight s = new Sight(-3, 4, new Vector3f(0.01F, 0.02F, -0.03F));
        float half = ReticleMath.halfTan(65, 70);
        float[] a = new float[2], b = new float[2], m = new float[2];
        Vector3f p = new Vector3f(s.c).add(s.u), q = new Vector3f(s.c).sub(s.v).fma(-0.3F, s.u);
        ReticleMath.uv(p, s.a, s.x, s.y, half, a);
        ReticleMath.uv(q, s.a, s.x, s.y, half, b);
        ReticleMath.uv(p.add(q, new Vector3f()).mul(0.5F), s.a, s.x, s.y, half, m);
        assertEquals((a[0] + b[0]) / 2, m[0], 1e-6F, "uv linear on a plane normal to the axis");
        assertEquals((a[1] + b[1]) / 2, m[1], 1e-6F);
    }

    @Test
    void windowClipsAnOffAxisEye() {
        Vector3f tmp = new Vector3f();
        Sight on = new Sight(1, 1, new Vector3f(0.003F, 0.002F, 0));
        assertTrue(ReticleMath.centreInWindow(on.c, on.u, on.v, on.a, tmp));
        Sight off = new Sight(1, 1, new Vector3f(0.015F, 0, 0));
        assertFalse(ReticleMath.centreInWindow(off.c, off.u, off.v, off.a, tmp), "eye 15 mm off a 12 mm window");
        Sight tilted = new Sight(4, 0, new Vector3f());
        assertTrue(ReticleMath.centreInWindow(tilted.c, tilted.u, tilted.v, tilted.a, tmp),
                "rotation about the eye keeps the axis through the window");
        assertEquals(0.01F, ReticleMath.inscribed(new Vector3f(-0.02F, -0.03F, -0.3F), new Vector3f(0.01F, 0.015F, -0.1F)));
        assertTrue(ReticleMath.inscribed(new Vector3f(0.01F, -0.03F, -0.3F), new Vector3f(0.02F, 0.015F, -0.1F)) < 0,
                "axis outside the bounds");
    }

    @Test
    void fadesInWithAds() {
        assertEquals(0, ReticleMath.fade(0));
        assertEquals(0, ReticleMath.fade(ReticleMath.FADE_FROM));
        assertEquals(1, ReticleMath.fade(ReticleMath.FADE_TO));
        assertEquals(1, ReticleMath.fade(1));
        float prev = 0;
        for (int i = 0; i <= 100; i++) {
            float f = ReticleMath.fade(i / 100F);
            assertTrue(f >= prev);
            prev = f;
        }
    }
}
