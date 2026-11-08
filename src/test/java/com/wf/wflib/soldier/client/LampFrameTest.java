package com.wf.wflib.soldier.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The lens follows the wearer's current position and turn: a flying, turning wearer's light is never a frame behind. */
class LampFrameTest {
    /** Minecraft's view and up vectors for a yaw / pitch (degrees), eye at (x, y, z). */
    private static LampFrame.Basis head(double x, double y, double z, float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        Vector3f f = new Vector3f((float) (-Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch),
                (float) (Math.cos(yaw) * Math.cos(pitch)));
        Vector3f u = new Vector3f((float) (-Math.sin(yaw) * Math.sin(pitch)), (float) Math.cos(pitch),
                (float) (Math.cos(yaw) * Math.sin(pitch)));
        return new LampFrame.Basis(x, y, z, f, u);
    }

    @Test
    void decodingAtTheSamePoseGivesBackWhatWasDrawn() {
        LampFrame.Basis b = head(100.5, 64.2, -20.25, 33, -12);
        Vector3f fwd = new Vector3f(b.forward()).add(0.05f, 0.02f, 0).normalize(), up = new Vector3f(b.up());
        LampFrame.World w = LampFrame.decode(b, LampFrame.encode(b, 100.7, 64.4, -19.8, fwd, up));
        assertEquals(100.7, w.x(), 1e-4);
        assertEquals(64.4, w.y(), 1e-4);
        assertEquals(-19.8, w.z(), 1e-4);
        assertEquals(fwd.x, w.forward().x, 1e-4);
        assertEquals(fwd.z, w.forward().z, 1e-4);
    }

    @Test
    void aWearerThatFlewAndTurnedCarriesTheLensWithIt() {
        LampFrame.Basis then = head(0, 70, 0, 20, 5), now = head(10, 95, -4, 75, -35);
        double lx = 0.1, ly = -0.02, lz = 0.44;                       // the lamp, on the brow, as last drawn
        Vector3f r = then.right();
        double wx = lx * r.x + ly * then.up().x + lz * then.forward().x, wy = lx * r.y + ly * then.up().y + lz * then.forward().y,
                wz = lx * r.z + ly * then.up().z + lz * then.forward().z;
        LampFrame.Local local = LampFrame.encode(then, then.ex() + wx, then.ey() + wy, then.ez() + wz, new Vector3f(then.forward()), new Vector3f(then.up()));
        LampFrame.World w = LampFrame.decode(now, local);
        // expected: the same offset, in the new head frame
        Vector3f r2 = now.right();
        assertEquals(now.ex() + lx * r2.x + ly * now.up().x + lz * now.forward().x, w.x(), 1e-4);
        assertEquals(now.ey() + lx * r2.y + ly * now.up().y + lz * now.forward().y, w.y(), 1e-4);
        assertEquals(now.ez() + lx * r2.z + ly * now.up().z + lz * now.forward().z, w.z(), 1e-4);
        // and the lamp still shines straight ahead of the head it is on
        assertEquals(now.forward().x, w.forward().x, 1e-4);
        assertEquals(now.forward().y, w.forward().y, 1e-4);
        assertEquals(now.forward().z, w.forward().z, 1e-4);
        // 25 m flown and 55 degrees turned are all in the result: nothing is left at where the head was a frame ago
        assertEquals(true, Math.abs(w.y() - then.ey()) > 20);
    }
}
