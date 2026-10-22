package com.wf.wflib.belt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeltChainTest {
    private static final Vector3f FEED = new Vector3f(), LEFT = new Vector3f(-1, 0, 0), BACK = new Vector3f(0, 0, 1),
            G = new Vector3f(0, -9.81F, 0);
    private static final float PITCH = 0.0127F, E = 1e-4F;

    private static void assertChain(BeltChain b) {
        Matrix4f m = new Matrix4f(), n = new Matrix4f();
        for (int k = 1; k < b.links(); k++) {
            assertEquals(b.pitch(), b.position(k).distance(b.position(k - 1)), E, "pitch at " + k);
            b.link(k - 1, m);
            b.link(k, n);
            Vector3f a = m.getColumn(0, new Vector3f()), c = n.getColumn(0, new Vector3f());
            assertTrue(a.angle(c) <= BeltChain.BEND + E, "bend " + Math.toDegrees(a.angle(c)) + " at " + k);
            Vector3f za = m.getColumn(2, new Vector3f()), zc = n.getColumn(2, new Vector3f());
            assertTrue(za.angle(zc) <= BeltChain.TWIST + E, "twist at " + k);
            assertEquals(0, c.dot(zc), E, "link normal to its cartridge at " + k);
        }
    }

    @Test
    void freeBeltHangsDownAtTheTail() {
        BeltChain b = new BeltChain(PITCH);
        b.update(FEED, LEFT, BACK, null, null, 30, G, 1 / 60F);
        assertChain(b);
        Vector3f tail = new Vector3f(b.position(29)).sub(b.position(28)).normalize();
        assertTrue(tail.y < -0.99F, "tail " + tail);
        assertEquals(0, b.position(29).z(), E, "planar: hinges keep the cartridge axis");
    }

    @Test
    void boxBeltLeavesAlongTheTrayAndEntersTheMouth() {
        BeltChain b = new BeltChain(PITCH);
        Vector3f box = new Vector3f(-0.058F, -0.035F, 0), down = new Vector3f(0, -1, 0);
        for (int i = 0; i < 60; i++) b.update(FEED, LEFT, BACK, box, down, 200, G, 1 / 60F);
        assertChain(b);
        int n = b.links();
        assertTrue(n >= 7 && n <= 10, "links " + n);
        assertTrue(b.position(n - 1).distance(box) < PITCH / 2, "end " + b.position(n - 1).distance(box));
        assertTrue(b.position(1).y() > -PITCH * 0.3F, "leaves the tray flat: " + b.position(1));
        Vector3f last = new Vector3f(b.position(n - 1)).sub(b.position(n - 2)).normalize();
        assertTrue(last.dot(down) > 0.9F, "enters the mouth along it: " + last);
        for (int k = 0; k < n; k++) assertTrue(b.position(k).y() > box.y - PITCH / 2, "sags under the mouth at " + k);
    }

    @Test
    void tooFewRoundsForTheBoxHang() {
        BeltChain b = new BeltChain(PITCH);
        b.update(FEED, LEFT, BACK, new Vector3f(-0.058F, -0.035F, 0), new Vector3f(0, -1, 0), 3, G, 1 / 60F);
        assertEquals(3, b.links());
        assertChain(b);
    }

    @Test
    void sidewaysJerkLagsTheTail() {
        BeltChain b = new BeltChain(PITCH);
        b.update(FEED, LEFT, BACK, null, null, 20, G, 1 / 60F);
        Vector3f before = new Vector3f(b.position(19));
        b.update(new Vector3f(0.03F, 0, 0), LEFT, BACK, null, null, 20, G, 1 / 60F);
        float tail = b.position(19).x() - before.x;
        assertTrue(tail < 0.015F, "hanging tail lags a sideways jerk: " + tail);
        assertChain(b);
    }

    @Test
    void growingAndShrinkingKeepsThePitch() {
        BeltChain b = new BeltChain(PITCH);
        b.update(FEED, LEFT, BACK, null, null, 10, G, 1 / 60F);
        b.update(FEED, LEFT, BACK, null, null, 25, G, 1 / 60F);
        assertChain(b);
        b.update(FEED, LEFT, BACK, null, null, 5, G, 1 / 60F);
        assertEquals(5, b.links());
        assertChain(b);
    }

    @Test
    void shiftedLinksSlideOnePitchTowardTheFeed() {
        BeltChain b = new BeltChain(PITCH);
        b.update(FEED, LEFT, BACK, null, null, 20, G, 1 / 60F);
        Matrix4f m = new Matrix4f(), n = new Matrix4f();
        for (int k = 0; k < 19; k++) {
            assertEquals(0, b.link(k, 0, m).getTranslation(new Vector3f()).distance(b.position(k)), E, "rest " + k);
            assertEquals(0, b.link(k, 1, m).getTranslation(new Vector3f()).distance(b.position(k + 1)), E, "k " + k);
            assertEquals(0, b.link(k, 1, m).getColumn(0, new Vector3f()).distance(b.link(k + 1, n).getColumn(0,
                    new Vector3f())), E, "dir " + k);
        }
        assertEquals(0, b.link(-1, 1, m).getTranslation(new Vector3f()).distance(FEED), E, "stripped link at feed");
        assertEquals(PITCH, b.link(-1, 0.5F, m).getTranslation(new Vector3f()).distance(b.link(-1, 1.5F, n)
                .getTranslation(new Vector3f())), E, "uniform slide");
        assertEquals(PITCH, b.link(19, 1, m).getTranslation(new Vector3f()).distance(b.position(19)), E, "tail");
    }
}
