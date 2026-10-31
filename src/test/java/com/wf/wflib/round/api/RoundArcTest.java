package com.wf.wflib.round.api;

import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Independent recurrence; real {@code Rounds} step: {@code RoundGameTest.anArcPredictionIsTheFlight}. */
class RoundArcTest {

    private static KineticPreset preset(String id, double drag, double quadratic, double gravity) {
        return KineticPreset.builder(KineticPresets.rl(id), null, Warheads.rl("inert"))
                .speed(40.0).drag(drag).quadraticDrag(quadratic).gravity(gravity).life(300).build();
    }

    private static void assertBitEqual(KineticPreset p, double x, double y, double z, double vx, double vy,
                                       double vz, int life, double voidY, int ticks, int expectedSteps) {
        double[] out = new double[3 * ticks];
        int n = RoundArc.predict(p, x, y, z, vx, vy, vz, life, voidY, false, ticks, out);
        assertEquals(expectedSteps, n, "steps");
        for (int k = 0; k < n; k++) {
            x += vx;
            y += vy;
            z += vz;
            double decay = p.decay(Math.sqrt(vx * vx + vy * vy + vz * vz));
            vx *= decay;
            vy = vy * decay - p.gravity();
            vz *= decay;
            assertEquals(Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(out[3 * k]), "x @" + k);
            assertEquals(Double.doubleToRawLongBits(y), Double.doubleToRawLongBits(out[3 * k + 1]), "y @" + k);
            assertEquals(Double.doubleToRawLongBits(z), Double.doubleToRawLongBits(out[3 * k + 2]), "z @" + k);
        }
    }

    @Test
    void linearDragIsTheRecurrence() {
        assertBitEqual(preset("arc_linear", 0.01, 0.0, 0.05), 1.0e7 + 0.3, 80.1, -3.7, 40.0, 1.3, 0.7, 300,
                Double.NEGATIVE_INFINITY, 200, 200);
    }

    @Test
    void quadraticDragIsTheRecurrence() {
        assertBitEqual(preset("arc_quadratic", 0.002, 0.0011722, 0.03), 12.5, 64.0, 7.25, 31.0, 4.0, -9.0, 300,
                Double.NEGATIVE_INFINITY, 200, 200);
    }

    /** Server: {@code --life <= 0} before the move => life L leaves L - 1 steps. */
    @Test
    void lifeEndsTheArc() {
        assertBitEqual(preset("arc_life", 0.01, 0.0, 0.05), 0.0, 64.0, 0.0, 3.0, 0.0, 0.0, 17,
                Double.NEGATIVE_INFINITY, 200, 16);
    }

    /** Checked on the state before the step: the first step below the floor while descending is not taken. */
    @Test
    void theVoidEndsTheArc() {
        assertBitEqual(preset("arc_void", 0.0, 0.0, 0.0), 0.0, 10.0, 0.0, 0.0, -1.0, 0.0, 300, 0.0, 200, 11);
    }

    /** Reference: rocket loop in {@link Vec3} arithmetic, motor along {@code nose} after drag/gravity for burnTicks steps. */
    private static void assertPoweredBitEqual(KineticPreset p, Vec3 start, Vec3 velocity0, Vec3 nose, int ticks) {
        double[] out = new double[3 * ticks];
        int n = RoundArc.predictPowered(p, start.x, start.y, start.z, velocity0.x, velocity0.y, velocity0.z, nose.x,
                nose.y, nose.z, ticks + 1, Double.NEGATIVE_INFINITY, ticks, out);
        assertEquals(ticks, n, "steps");
        Vec3 position = start;
        Vec3 velocity = velocity0;
        Vec3 thrust = nose.normalize().scale(p.motorAccel());
        for (int tick = 1; tick <= ticks; tick++) {
            position = position.add(velocity);
            velocity = velocity.scale(p.decay(velocity.length())).subtract(0, p.gravity(), 0);
            if (tick <= p.burnTicks()) {
                velocity = velocity.add(thrust);
            }
            int k = tick - 1;
            assertEquals(Double.doubleToRawLongBits(position.x), Double.doubleToRawLongBits(out[3 * k]), "x @" + k);
            assertEquals(Double.doubleToRawLongBits(position.y), Double.doubleToRawLongBits(out[3 * k + 1]), "y @" + k);
            assertEquals(Double.doubleToRawLongBits(position.z), Double.doubleToRawLongBits(out[3 * k + 2]), "z @" + k);
        }
    }

    @Test
    void poweredArcIsTheVec3Recurrence() {
        KineticPreset rocket = KineticPreset.builder(KineticPresets.rl("arc_rocket"), null, Warheads.rl("inert"))
                .speed(4.0).drag(0.004).quadraticDrag(0.0009).gravity(0.0245).life(400).motor(0.18, 35).build();
        assertPoweredBitEqual(rocket, new Vec3(1.0e7 + 0.3, 120.4, -3.7), new Vec3(3.1, 0.4, -1.2),
                new Vec3(0.9, 0.12, -0.35), 300);
        assertPoweredBitEqual(rocket, new Vec3(12.5, 64.0, 7.25), new Vec3(0, 0, 0), new Vec3(0, 1, 0), 120);
        assertPoweredBitEqual(rocket, new Vec3(0, 64, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 0), 60);
    }

    @Test
    void unpoweredIsPredict() {
        KineticPreset p = preset("arc_dry", 0.002, 0.0011722, 0.03);
        double[] a = new double[600], b = new double[600];
        int n = RoundArc.predict(p, 12.5, 64.0, 7.25, 31.0, 4.0, -9.0, 300, 0.0, false, 200, a);
        int m = RoundArc.predictPowered(p, 12.5, 64.0, 7.25, 31.0, 4.0, -9.0, 1, 0, 0, 300, 0.0, 200, b);
        assertEquals(n, m);
        for (int i = 0; i < 3 * n; i++) {
            assertEquals(Double.doubleToRawLongBits(a[i]), Double.doubleToRawLongBits(b[i]), "@" + i);
        }
    }
}
