package com.wf.wflib.ragdoll;

import com.norwood.ahf.pose.PoseClip;
import com.wf.wflib.ragdoll.sim.Ragdoll;
import com.wf.wflib.ragdoll.sim.RagdollPose;
import com.wf.wflib.ragdoll.sim.Skeleton;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagdollTest {

    private static final double[] HEAD = {-4, -8, -4, 8, 8, 8, 0, 0, 0}, TORSO = {-4, 0, -2, 8, 12, 4, 0, 0, 0};
    private static final double[] LEFT_ARM = {-1, -2, -2, 4, 12, 4, 5, 2, 0}, RIGHT_ARM = {-3, -2, -2, 4, 12, 4, -5, 2, 0};
    private static final double[] LEFT_LEG = {-2, 0, -2, 4, 12, 4, 1.9, 12, 0}, RIGHT_LEG = {-2, 0, -2, 4, 12, 4, -1.9, 12, 0};
    private static final Skeleton SK = new Skeleton(HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG);

    private static final Ragdoll.World FLOOR = new Ragdoll.World() {
        private final double[] boxes = {-200, -1, -200, 200, 0, 200};

        @Override
        public int collect(double a, double b, double c, double d, double e, double f) {
            return 1;
        }

        @Override
        public double[] boxes() {
            return boxes;
        }
    };

    private static final Ragdoll.World NOTHING = new Ragdoll.World() {
        @Override
        public int collect(double a, double b, double c, double d, double e, double f) {
            return 0;
        }

        @Override
        public double[] boxes() {
            return new double[0];
        }
    };

    /** Stairs: step i spans x in [i, i + 1], top at -i; body starts above the top step, moving downhill (+x). */
    private static final Ragdoll.World STAIRS = new Ragdoll.World() {
        private final double[] boxes = new double[40 * 6];

        {
            for (int i = 0; i < 40; i++) {
                boxes[i * 6] = i - 5;
                boxes[i * 6 + 1] = -100;
                boxes[i * 6 + 2] = -50;
                boxes[i * 6 + 3] = i - 4;
                boxes[i * 6 + 4] = -i;
                boxes[i * 6 + 5] = 50;
            }
        }

        @Override
        public int collect(double a, double b, double c, double d, double e, double f) {
            return 40;
        }

        @Override
        public double[] boxes() {
            return boxes;
        }
    };

    /** Rest pose at the yaw-0 render transform, feet at y = {@code feet}. */
    private static Ragdoll standing(double feet) {
        Ragdoll sim = new Ragdoll(SK);
        double[][] model = {{0, -4, 0}, {0, 9, 0}, {0, 3, 0}, {6, 3, 0}, {6, 9, 0}, {-6, 3, 0}, {-6, 9, 0},
                {1.9, 15, 0}, {1.9, 21, 0}, {-1.9, 15, 0}, {-1.9, 21, 0}};
        Quaterniond up = new Quaterniond(1, 0, 0, 0);
        for (int i = 0; i < Skeleton.BODIES; i++) {
            // Y(0) = diag(1, -1, -1)
            sim.x[i * 3] = model[i][0] * Skeleton.S;
            sim.x[i * 3 + 1] = feet + (Skeleton.Y_SHIFT_PX - model[i][1]) * Skeleton.S;
            sim.x[i * 3 + 2] = -model[i][2] * Skeleton.S;
            sim.q[i * 4] = up.x;
            sim.q[i * 4 + 1] = up.y;
            sim.q[i * 4 + 2] = up.z;
            sim.q[i * 4 + 3] = up.w;
        }
        return sim;
    }

    private static void run(Ragdoll sim, Ragdoll.World world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            sim.step(world);
        }
    }

    private static double relDeg(Ragdoll sim, int a, int b) {
        Quaterniond qa = new Quaterniond(sim.q[a * 4], sim.q[a * 4 + 1], sim.q[a * 4 + 2], sim.q[a * 4 + 3]);
        Quaterniond qb = new Quaterniond(sim.q[b * 4], sim.q[b * 4 + 1], sim.q[b * 4 + 2], sim.q[b * 4 + 3]);
        Quaterniond rel = qa.conjugate().mul(qb);
        return Math.toDegrees(2 * Math.atan2(rel.x, rel.w));
    }

    private static void assertSane(Ragdoll sim, double floor) {
        for (int i = 0; i < Skeleton.BODIES; i++) {
            assertTrue(Double.isFinite(sim.x[i * 3 + 1]), "body " + i + " diverged");
        }
        assertTrue(sim.lowest() > floor - 0.05, "sank: " + sim.lowest());
    }

    @Test
    void fallsOverAndRestsOnTheFloorWithoutSinking() {
        Ragdoll sim = standing(0);
        sim.kick(0.5, 0, 0);
        run(sim, FLOOR, 200);
        assertSane(sim, 0);
        assertTrue(sim.x[Skeleton.TORSO * 3 + 1] < 0.6, "still upright: " + sim.x[Skeleton.TORSO * 3 + 1]);
        assertTrue(sim.asleep, "never settled");
    }

    @Test
    void kneesAndElbowsOnlyBendTheAnatomicalWay() {
        Ragdoll sim = standing(0);
        sim.kick(1.5, 0, 0);
        double kneeMin = 0, elbowMax = 0;
        for (int t = 0; t < 200; t++) {
            sim.step(FLOOR);
            kneeMin = Math.min(kneeMin, Math.min(relDeg(sim, Skeleton.LEFT_LEG, Skeleton.LEFT_SHIN),
                    relDeg(sim, Skeleton.RIGHT_LEG, Skeleton.RIGHT_SHIN)));
            elbowMax = Math.max(elbowMax, Math.max(relDeg(sim, Skeleton.LEFT_ARM, Skeleton.LEFT_FOREARM),
                    relDeg(sim, Skeleton.RIGHT_ARM, Skeleton.RIGHT_FOREARM)));
        }
        assertTrue(kneeMin > -8, "knee hyperextended " + kneeMin);
        assertTrue(elbowMax < 8, "elbow hyperextended " + elbowMax);
    }

    @Test
    void zeroGravityDriftsToTheNeutralPosture() {
        Ragdoll sim = standing(50);
        sim.gravity = 0;
        run(sim, NOTHING, 400);
        assertEquals(35, relDeg(sim, Skeleton.LEFT_LEG, Skeleton.LEFT_SHIN), 8, "knee");
        assertEquals(-55, relDeg(sim, Skeleton.RIGHT_ARM, Skeleton.RIGHT_FOREARM), 8, "elbow");
    }

    @Test
    void hardLimitsStayWhereTheyArePut() {
        Ragdoll sim = standing(50);
        sim.gravity = 0;
        sim.softLimits = false;
        run(sim, NOTHING, 200);
        assertEquals(0, relDeg(sim, Skeleton.LEFT_LEG, Skeleton.LEFT_SHIN), 2, "knee moved without tension");
    }

    /** Hard stop: rate falls off a cliff at the limit. Soft: it slows on the way in. */
    @Test
    void softLimitsSlowTheKneeBeforeItsStop() {
        double soft = hardestStop(true), hard = hardestStop(false);
        assertTrue(hard > 5 && soft < hard * 0.75, "soft " + soft + " hard " + hard);
    }

    /** Largest knee rate drop in one tick, deg/tick^2. */
    private static double hardestStop(boolean soft) {
        Ragdoll sim = standing(50);
        sim.gravity = 0;
        sim.softLimits = soft;
        sim.spin(Skeleton.LEFT_SHIN, 14, 0, 0);
        sim.spin(Skeleton.LEFT_LEG, -4, 0, 0);
        double worst = 0, last = relDeg(sim, Skeleton.LEFT_LEG, Skeleton.LEFT_SHIN), lastRate = Double.NaN;
        for (int t = 0; t < 40; t++) {
            sim.step(NOTHING);
            double now = relDeg(sim, Skeleton.LEFT_LEG, Skeleton.LEFT_SHIN), rate = now - last;
            if (!Double.isNaN(lastRate) && t > 1) {
                worst = Math.max(worst, lastRate - rate);
            }
            last = now;
            lastRate = rate;
        }
        return worst;
    }

    @Test
    void jointsHoldAtFullSpeedImpact() {
        Ragdoll sim = standing(40);
        sim.kick(10, -30, 4);
        run(sim, FLOOR, 200);
        assertSane(sim, 0);
    }

    @Test
    void tumblesDownStairsWithoutPassingThrough() {
        Ragdoll sim = standing(1);
        sim.kick(3, 0, 0);
        for (int t = 0; t < 300; t++) {
            sim.step(STAIRS);
            double x = sim.x[Skeleton.TORSO * 3], y = sim.x[Skeleton.TORSO * 3 + 1];
            if (x > 30) {
                break;
            }
            double step = -Math.floor(x + 5);
            assertTrue(y > step - 1.3, "through the stairs at t=" + t + " x=" + x + " y=" + y);
        }
        assertTrue(sim.x[Skeleton.TORSO * 3] > 1.5, "stayed on the top step");
    }

    @Test
    void blastThrowsTheBody() {
        Ragdoll sim = standing(0);
        sim.kick(-8, 3, 0);
        run(sim, FLOOR, 200);
        assertSane(sim, 0);
        assertTrue(sim.x[Skeleton.TORSO * 3] < -1.5, "x=" + sim.x[Skeleton.TORSO * 3]);
    }

    @Test
    void sameInputSameBits() {
        Ragdoll a = standing(0), b = standing(0);
        a.kick(2, 1, -1);
        b.kick(2, 1, -1);
        run(a, FLOOR, 100);
        run(b, FLOOR, 100);
        for (int i = 0; i < a.x.length; i++) {
            assertEquals(a.x[i], b.x[i], 0.0);
        }
    }

    /** Channels reproduce the sim's bodies through the rig's matrices. */
    @Test
    void channelsRebuildTheBodiesAcrossYaw() {
        Ragdoll sim = standing(0);
        sim.kick(1, 0, 0);
        run(sim, FLOOR, 40);
        RagdollPose pose = new RagdollPose();
        pose.from(sim);
        double[][] out = new double[PoseClip.LEFT_LEG + 1][PoseClip.CHANNELS];
        for (double yaw : new double[]{0, 37, -120, 179}) {
            double ex = sim.x[3] + 0.2, ey = sim.lowest(), ez = sim.x[5] - 0.3;
            pose.channels(SK, ex, ey, ez, yaw, out);
            double t = Math.toRadians(yaw), c = Math.cos(t), s = Math.sin(t);
            Matrix3d y = new Matrix3d(c, 0, s, 0, -1, 0, s, 0, -c);
            // Left leg upper half centre: pivot + R (0, 3, 0) px -> world, against the sim's thigh.
            org.joml.Vector3d p = new org.joml.Vector3d(0, 3, 0);
            new org.joml.Matrix3d().rotationZYX(out[PoseClip.LEFT_LEG][5], out[PoseClip.LEFT_LEG][4], out[PoseClip.LEFT_LEG][3]).transform(p);
            p.add(out[PoseClip.LEFT_LEG][0], out[PoseClip.LEFT_LEG][1] - Skeleton.Y_SHIFT_PX, out[PoseClip.LEFT_LEG][2]);
            y.transform(p);
            p.mul(Skeleton.S).add(ex, ey, ez);
            double i = Skeleton.LEFT_LEG * 3;
            assertEquals(sim.x[(int) i], p.x, 0.02, "yaw " + yaw);
            assertEquals(sim.x[(int) i + 1], p.y, 0.02, "yaw " + yaw);
            assertEquals(sim.x[(int) i + 2], p.z, 0.02, "yaw " + yaw);
        }
    }

    @Test
    void costPerAwakeTick() {
        Ragdoll sim = standing(0);
        sim.kick(1, 0, 0);
        long total = 0;
        int ticks = 0;
        for (int round = 0; round < 40; round++) {
            Ragdoll r = standing(0);
            r.kick(1, 0, 0);
            for (int t = 0; t < 60 && !r.asleep; t++) {
                long at = System.nanoTime();
                r.step(FLOOR);
                if (round >= 20) {
                    total += System.nanoTime() - at;
                    ticks++;
                }
            }
        }
        System.out.println("ragdoll us/tick " + total / 1000.0 / ticks);
    }
}
