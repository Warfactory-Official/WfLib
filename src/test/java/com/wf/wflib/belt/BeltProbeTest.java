package com.wf.wflib.belt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;
import com.wf.wflib.belt.api.BeltChain;
import com.wf.wflib.belt.api.BeltProbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeltProbeTest {
    private static final Vector3f FEED = new Vector3f(), LEFT = new Vector3f(-1, 0, 0), BACK = new Vector3f(0, 0, 1);
    private static final Matrix4f I = new Matrix4f();
    private static final float PITCH = 0.0127F, DT = 1 / 60F;

    private static String erratic(BeltProbe p) {
        return p.frames().stream().filter(BeltProbe.Frame::erratic).limit(8).map(BeltProbe.Frame::summary)
                .collect(Collectors.joining("\n"));
    }

    /** Gravity turned about the cartridge axis by {@code deg}, from straight down. */
    private static Vector3f rolled(float deg) {
        return new Vector3f(0, -9.81F, 0).rotateZ((float) Math.toRadians(deg));
    }

    @Test
    void freeHangSettlesQuietly() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        for (int i = 0; i < 120; i++) b.update(FEED, LEFT, BACK, List.of(), null, null, 40, rolled(0), I, DT);
        assertEquals("", erratic(p));
    }

    @Test
    void gravityRollingPastTheBeltDirectionDoesNotFlipTheHang() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        // out = -X; gravity sweeps through +X (antiparallel to out) and back, 1 deg/frame
        for (int i = 0; i <= 360; i++) b.update(FEED, LEFT, BACK, List.of(), null, null, 40, rolled(60 + 60 * (float) Math.sin(i * Math.PI / 180)), I, DT);
        assertEquals("", erratic(p));
    }

    @Test
    void lookingStraightDownKeepsTheBeltStill() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        // gravity along the cartridge axis (barrel down) + view-bob wobble
        for (int i = 0; i < 300; i++) {
            Vector3f g = new Vector3f(0.02F * (float) Math.sin(i * 0.3), 0.02F * (float) Math.cos(i * 0.23), 1)
                    .normalize(9.81F);
            b.update(FEED, LEFT, BACK, List.of(), null, null, 40, g, I, DT);
        }
        assertEquals("", erratic(p));
    }

    @Test
    void boxBeltRidesRecoilWithoutErraticLinks() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        Vector3f box = new Vector3f(-0.058F, -0.035F, 0), down = new Vector3f(0, -1, 0);
        for (int i = 0; i < 300; i++) {
            // 700 rpm kick: feed 3 mm back every ~5 frames, box rides with the gun
            float kick = i % 5 == 0 ? 0.003F : 0;
            Vector3f feed = new Vector3f(0, kick * 0.3F, kick), mouth = new Vector3f(box).add(feed);
            b.update(feed, LEFT, BACK, List.of(), mouth, down, 200, new Vector3f(0, -9.81F, 0), I, DT);
        }
        assertEquals("", erratic(p));
    }

    @Test
    void jitteredHangStaysPlanarForEitherHingeHandedness() {
        // in-game M249: out +X, cartridge -Z (-X / +Z mirrored); 0.1 mm feed + 0.3 mrad axis jitter per frame
        for (float sign : new float[]{1, -1}) {
            BeltChain b = new BeltChain(PITCH);
            BeltProbe p = new BeltProbe(1000);
            b.probe(p);
            for (int i = 0; i < 300; i++) {
                float n = (float) Math.sin(i * 1.7), m = (float) Math.cos(i * 2.3);
                Vector3f feed = new Vector3f(1e-4F * n, 1e-4F * m, 0);
                Vector3f out = new Vector3f(sign, 0, 3e-4F * m).normalize();
                Vector3f axis = new Vector3f(0, 3e-4F * n, -sign).normalize();
                b.update(feed, out, axis, List.of(), null, null, 50, rolled(0), I, DT);
            }
            assertEquals("", erratic(p), "sign " + sign);
        }
    }

    @Test
    void replayReproducesTheCapture() throws Exception {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        StringBuilder jsonl = new StringBuilder(BeltProbe.resetJson(PITCH) + "\n");
        for (int i = 0; i < 90; i++) {
            Vector3f feed = new Vector3f(0.01F * (float) Math.sin(i * 0.4), 0, 0);
            b.update(feed, LEFT, BACK, List.of(), null, null, 30, rolled(i), I, DT);
            jsonl.append(p.last().toJson()).append('\n');
        }
        List<BeltProbe.Frame> again = BeltProbe.replay(new BufferedReader(new StringReader(jsonl.toString())));
        assertEquals(90, again.size());
        for (int i = 0; i < 90; i++) {
            BeltProbe.Frame a = p.frames().get(i), c = again.get(i);
            assertEquals(a.jitter(), c.jitter(), "frame " + i);
            assertEquals(a.restDev(), c.restDev(), "frame " + i);
            assertEquals(a.flags(), c.flags(), "frame " + i);
        }
    }

    @Test
    void boxBeltIsFlaggedWhenTooFewRoundsDropTheAnchor() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(10);
        b.probe(p);
        b.update(FEED, LEFT, BACK, List.of(), new Vector3f(-0.058F, -0.035F, 0), new Vector3f(0, -1, 0), 3, rolled(0), I, DT);
        assertTrue(p.last().flags().contains(BeltProbe.Flag.ANCHOR_DROPPED), p.last().summary());
    }

    /** Hand-space frame: gun at {@code yaw} about the eye (origin), hand 0.4 m ahead. */
    private static Matrix4f turned(float yaw) {
        return new Matrix4f().rotateY(yaw).translate(0.15F, -0.2F, -0.4F);
    }

    @Test
    void turningTheGunLagsTheBeltInWorldSpace() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        for (int i = 0; i < 60; i++) b.update(FEED, LEFT, BACK, List.of(), null, null, 40, rolled(0), turned(0), DT);
        float hung = p.last().restDev();
        // 90 deg eased over 0.5 s, then held
        float lag = 0;
        for (int i = 1; i <= 150; i++) {
            float yaw = i >= 30 ? (float) Math.PI / 2 : (float) (Math.PI / 4 * (1 - Math.cos(Math.PI * i / 30)));
            Matrix4f f = turned(yaw);
            b.update(FEED, LEFT, BACK, List.of(), null, null, 40,
                    new Matrix4f(f).invert().transformDirection(new Vector3f(0, -9.81F, 0)), f, DT);
            if (i == 15) lag = b.position(39).distance(b.restPosition(39));
        }
        assertTrue(lag > 0.05F, "tail lags the turn mid-swing: " + lag);
        assertEquals(hung, p.last().restDev(), 0.1F, "settles after: " + p.last().summary());
        assertEquals("", erratic(p));
    }

    @Test
    void handShakeDoesNotJitterTheBelt() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        for (int i = 0; i < 300; i++) {
            // idle sway + 1 mm 9 Hz tremor in world space; hand space fixed
            float t = i * DT;
            Matrix4f f = turned(0.05F * (float) Math.sin(t * 1.3)).translateLocal(
                    0.001F * (float) Math.sin(t * 57), 0.001F * (float) Math.cos(t * 61), 0);
            b.update(FEED, LEFT, BACK, List.of(), null, null, 40, rolled(0), f, DT);
        }
        assertEquals("", erratic(p));
    }

    @Test
    void guidedBeltFollowsItsPathThenHangs() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        // out of the tray, around the magwell: left, then a tight turn down (arc != chord there)
        List<Vector3f> guides = List.of(new Vector3f(-0.04F, -0.005F, 0), new Vector3f(-0.06F, -0.01F, 0),
                new Vector3f(-0.08F, -0.07F, 0));
        for (int i = 0; i < 120; i++) b.update(FEED, LEFT, BACK, guides, null, null, 40, rolled(0), I, DT);
        assertTrue(b.pinned() >= 8, "pinned " + b.pinned());
        assertTrue(b.position(b.pinned()).distance(guides.get(2)) < PITCH, "path end " + b.position(b.pinned()));
        for (int k = 1; k < b.links(); k++)
            assertEquals(PITCH, b.position(k).distance(b.position(k - 1)), 1e-4F, "pitch at " + k);
        Vector3f tail = new Vector3f(b.position(39)).sub(b.position(38)).normalize();
        assertTrue(tail.y < -0.95F, "tail hangs " + tail);
        assertEquals("", erratic(p));
    }

    @Test
    void guidedBoxBeltReachesTheMouth() {
        BeltChain b = new BeltChain(PITCH);
        BeltProbe p = new BeltProbe(1000);
        b.probe(p);
        List<Vector3f> guides = List.of(new Vector3f(-0.03F, -0.004F, 0));
        Vector3f box = new Vector3f(-0.07F, -0.06F, 0), down = new Vector3f(0, -1, 0);
        for (int i = 0; i < 120; i++) b.update(FEED, LEFT, BACK, guides, box, down, 200, rolled(0), I, DT);
        assertTrue(p.last().mode() == BeltProbe.Mode.ANCHORED, p.last().summary());
        assertTrue(b.pinned() >= 2, "pinned " + b.pinned());
        assertTrue(b.position(b.links() - 1).distance(box) < PITCH / 2, "end " + b.position(b.links() - 1));
        assertEquals("", erratic(p));
    }
}
