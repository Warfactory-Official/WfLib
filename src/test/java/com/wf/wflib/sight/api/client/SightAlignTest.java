package com.wf.wflib.sight.api.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SightAlignTest {
    private static final float EPS = 1e-5F;

    private static Matrix4f frame(Random r) {
        return new Matrix4f().translate(r.nextFloat(), r.nextFloat(), r.nextFloat())
                .rotateXYZ(r.nextFloat() * 6, r.nextFloat() * 6, r.nextFloat() * 6);
    }

    @Test
    void eyeFrameLandsOnTheCameraUpright() {
        Random r = new Random(7);
        SightAlign a = new SightAlign();
        for (int n = 0; n < 200; n++) {
            Matrix4f f = frame(r);
            Matrix4f out = a.solve(f, 30).apply(1, new Matrix4f()).mul(f);
            Vector3f o = out.getTranslation(new Vector3f());
            assertEquals(0, o.length(), EPS);
            Vector3f fwd = out.transformDirection(new Vector3f(0, 0, -1)).normalize();
            Vector3f up = out.transformDirection(new Vector3f(0, 1, 0)).normalize();
            assertEquals(-1, fwd.z, EPS);
            assertEquals(1, up.y, EPS);
        }
    }

    @Test
    void noFrameRollsByCantAboutTheOrigin() {
        Matrix4f out = new SightAlign().solve(null, 45).apply(1, new Matrix4f());
        Matrix4f want = new Matrix4f().rotateZ((float) Math.toRadians(45));
        for (int i = 0; i < 16; i++) {
            assertEquals(want.get(i / 4, i % 4), out.get(i / 4, i % 4), EPS);
        }
    }

    @Test
    void adsZeroIsIdentity() {
        Random r = new Random(3);
        Matrix4f out = new SightAlign().solve(frame(r), 0).apply(0, new Matrix4f());
        for (int i = 0; i < 16; i++) {
            assertEquals(i % 5 == 0 ? 1 : 0, out.get(i / 4, i % 4), EPS);
        }
    }

    @Test
    void blendEndsAreTheSingleSolves() {
        Random r = new Random(11);
        SightAlign a = new SightAlign();
        SightAlign b = new SightAlign();
        Matrix4f fa = frame(r);
        Matrix4f fb = frame(r);
        a.blend(fa, 0, fb, 0, 1);
        b.solve(fa, 0);
        assertQuat(b.q, a.q);
        a.blend(fa, 0, fb, 0, 0);
        b.solve(fb, 0);
        assertQuat(b.q, a.q);
        assertEquals(b.c.distance(a.c), 0, EPS);
    }

    @Test
    void blendMidwayIsBetween() {
        SightAlign a = new SightAlign();
        Matrix4f fa = new Matrix4f().translate(0.1F, 0, 0);
        Matrix4f fb = new Matrix4f().translate(-0.1F, 0, 0).rotateZ(1);
        a.blend(fa, 0, fb, 0, 0.5F);
        assertEquals(0.5, a.q.angle(), 1e-4);
    }

    private static void assertQuat(Quaternionf want, Quaternionf got) {
        assertEquals(1, Math.abs(want.dot(got)), EPS);
    }
}
