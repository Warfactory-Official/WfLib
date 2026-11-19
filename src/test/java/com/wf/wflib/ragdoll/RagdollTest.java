package com.wf.wflib.ragdoll;

import com.norwood.ahf.pose.PoseClip;
import com.wf.wflib.ragdoll.sim.Ragdoll;
import com.wf.wflib.ragdoll.sim.RagdollPose;
import com.wf.wflib.ragdoll.sim.Skeleton;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
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
    void gripCarriesTheBodyAndReleaseKeepsItsMomentum() {
        Ragdoll sim = standing(0.5);
        double[] to = new double[7];
        System.arraycopy(sim.q, Skeleton.TORSO * 4, to, 3, 4);
        double z = sim.x[Skeleton.TORSO * 3 + 2];
        for (int t = 1; t <= 80; t++) {
            to[0] = 0.075 * t;
            to[1] = 1.6;
            to[2] = z;
            sim.grip(Skeleton.TORSO, to);
            sim.step(FLOOR);
            assertEquals(to[0], sim.x[Skeleton.TORSO * 3], 1.0e-9);
            assertEquals(to[1], sim.x[Skeleton.TORSO * 3 + 1], 1.0e-9);
        }
        assertSane(sim, 0);
        assertTrue(!sim.asleep, "slept while held");
        double[] shin = {sim.x[Skeleton.LEFT_SHIN * 3], sim.x[Skeleton.LEFT_SHIN * 3 + 1]};
        assertTrue(shin[1] < 1.2 && Math.abs(shin[0] - to[0]) < 0.6, "legs not hanging: " + shin[0] + ", " + shin[1]);
        sim.release();
        sim.step(FLOOR);
        assertTrue(sim.v[Skeleton.TORSO * 3] > 1.0, "momentum lost: " + sim.v[Skeleton.TORSO * 3]);
        run(sim, FLOOR, 200);
        assertSane(sim, 0);
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
            Vector3d p = new Vector3d(0, 3, 0);
            new Matrix3d().rotationZYX(out[PoseClip.LEFT_LEG][5], out[PoseClip.LEFT_LEG][4], out[PoseClip.LEFT_LEG][3]).transform(p);
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

    /** What is mounted on the head (a headlamp) must point where the head bone points, face to the ceiling included. */
    @Test
    void theHeadFrameFollowsTheHeadBone() {
        RagdollPose pose = new RagdollPose();
        pose.from(standing(0));
        double[] c = new double[3], f = new double[3], u = new double[3];
        pose.headFrame(SK, c, f, u);
        assertEquals(0, f[0], 1e-9);
        assertEquals(0, f[1], 1e-9);
        assertEquals(1, f[2], 1e-9, "upright at yaw 0 the face points along +z");
        assertEquals(1, u[1], 1e-9, "and the crown points up");

        // Tip the head back until the face looks at the ceiling: rotate the rest orientation by -90 degrees about x.
        Quaterniond tipped = new Quaterniond().rotateX(-Math.PI / 2).mul(new Quaterniond(1, 0, 0, 0));
        int r = Skeleton.HEAD * 4;
        pose.q[r] = tipped.x;
        pose.q[r + 1] = tipped.y;
        pose.q[r + 2] = tipped.z;
        pose.q[r + 3] = tipped.w;
        double[] c2 = new double[3];
        pose.headFrame(SK, c2, f, u);
        assertEquals(1, f[1], 1e-9, "face to the ceiling");
        assertEquals(-1, u[2], 1e-9, "crown toward the back");
        assertEquals(0, f[0] * u[0] + f[1] * u[1] + f[2] * u[2], 1e-9, "still a right angle");
        assertTrue(Math.abs(c2[1] - c[1]) > 0.01 || Math.abs(c2[2] - c[2]) > 0.01, "the head centre moves with the bone");
    }

    /** A rope walked away from a fallen body drags it behind, near the walker's pace, never floating. */
    @Test
    void aRopeDragsAFallenBodyAlongTheFloor() {
        Ragdoll sim = standing(0);
        run(sim, FLOOR, 120);
        int t = Skeleton.TORSO * 3;
        double x0 = sim.x[t], highest = 0;
        for (int i = 0; i < 160; i++) {
            sim.tether(x0 + 3 + i * 0.05, 1.0, 0, 2.0);        // walker at 1 m/s, waist height
            sim.step(FLOOR);
            highest = Math.max(highest, sim.x[t + 1]);
        }
        double moved = sim.x[t] - x0;
        assertTrue(moved > 5.0, "dragged only " + moved + " m of " + (160 * 0.05 + 3));
        assertTrue(moved < 9.5, "overshot the walker: " + moved);
        assertSane(sim, 0);
    }

    /** A rope from above lifts a body only as far as it can bear it, and lets it fall when it goes. */
    @Test
    void aRopeFromAboveHangsABodyAndDropsItWhenReleased() {
        Ragdoll sim = standing(0);
        run(sim, FLOOR, 120);
        int t = Skeleton.TORSO * 3;
        for (int i = 0; i < 100; i++) {
            sim.tether(sim.x[t], 3.0, 0, 1.0);
            sim.step(FLOOR);
        }
        assertTrue(sim.x[t + 1] > 1.2, "not lifted: " + sim.x[t + 1]);
        run(sim, FLOOR, 100);
        assertTrue(sim.x[t + 1] < 0.6, "left hanging: " + sim.x[t + 1]);
    }

    /** Peak torso speed (m/s) in each of {@code windows} consecutive windows of {@code per} ticks, the anchor held where it is. */
    /** The floor and a fence post (0.375 wide, 1.5 tall) at the origin. */
    private static final Ragdoll.World POST = new Ragdoll.World() {
        private final double[] boxes = {-200, -1, -200, 200, 0, 200, -0.1875, 0, -0.1875, 0.1875, 1.5, 0.1875};

        @Override
        public int collect(double a, double b, double c, double d, double e, double f) {
            return 2;
        }

        @Override
        public double[] boxes() {
            return boxes;
        }
    };

    private static double[] tiedPeaks(Ragdoll sim, double ax, double ay, double az, double rope, int windows, int per) {
        return tiedPeaks(sim, FLOOR, ax, ay, az, rope, windows, per);
    }

    private static double[] tiedPeaks(Ragdoll sim, Ragdoll.World world, double ax, double ay, double az, double rope, int windows, int per) {
        int t = Skeleton.TORSO * 3;
        double[] peaks = new double[windows];
        for (int w = 0; w < windows; w++) {
            for (int i = 0; i < per; i++) {
                sim.tether(ax, ay, az, rope);
                sim.step(world);
                peaks[w] = Math.max(peaks[w], Math.sqrt(sim.v[t] * sim.v[t] + sim.v[t + 1] * sim.v[t + 1] + sim.v[t + 2] * sim.v[t + 2]));
            }
        }
        return peaks;
    }

    /**
     * A body tied to a fixed point (a fence post) must lose speed, not gain it: whatever it was doing, its peak speed over
     * every later stretch is no more than the one before plus a little, and ends at rest. Prints the peaks per 2 s for tuning.
     */
    @Test
    void aBodyTiedToAPostSettlesAndNeverSpeedsUp() {
        double[][] cases = {                          // anchor x, y, z, rope, kick
                {1.5, 1.4, 0, 1.0, 0}, {1.5, 1.4, 0, 2.0, 0}, {0, 3.0, 0, 1.0, 0}, {2.5, 1.0, 0, 2.0, 2.0}, {0, 2.0, 1.0, 1.5, 3.0}};
        for (double[] c : cases) {
            Ragdoll sim = standing(0);
            sim.kick(c[4], 0, c[4] / 2);
            double[] peaks = tiedPeaks(sim, c[0], c[1], c[2], c[3], 10, 40);
            System.out.println("[tie] anchor " + c[0] + "," + c[1] + "," + c[2] + " rope " + c[3] + " kick " + c[4] + " peaks m/s per 2s: "
                    + java.util.Arrays.stream(peaks).mapToObj(p -> String.format("%.2f", p)).collect(java.util.stream.Collectors.joining(" ")));
            for (int w = 1; w < peaks.length; w++) {
                assertTrue(peaks[w] <= peaks[w - 1] + 0.5, "sped up at " + w * 2 + " s: " + peaks[w - 1] + " -> " + peaks[w] + " for " + java.util.Arrays.toString(c));
            }
            assertTrue(peaks[peaks.length - 1] < 0.6, "still moving at " + peaks[peaks.length - 1] + " m/s after 20 s for " + java.util.Arrays.toString(c));
            assertSane(sim, 0);
        }
    }

    /** Tied to a real post: the rope's end is at the post itself, so the rope must not drag the body into it and back out again. */
    @Test
    void aBodyTiedToAFencePostSettlesInsteadOfFlyingAround() {
        double[][] cases = {                          // body start x, z, rope, anchor y
                {1.5, 0, 2.0, 1.4}, {2.5, 0.5, 2.0, 1.4}, {1.0, 0, 1.0, 1.4}, {3.0, 0, 1.0, 1.0}, {0.8, 0.8, 2.0, 1.4}};
        for (double[] c : cases) {
            Ragdoll sim = standing(0);
            for (int i = 0; i < Skeleton.BODIES; i++) {
                sim.x[i * 3] += c[0];
                sim.x[i * 3 + 2] += c[1];
            }
            run(sim, POST, 100);                      // lies down first
            sim.wake();
            double[] peaks = tiedPeaks(sim, POST, 0, c[3], 0, c[2], 10, 40);
            System.out.println("[post] start " + c[0] + "," + c[1] + " rope " + c[2] + " anchor y " + c[3] + " peaks m/s per 2s: "
                    + java.util.Arrays.stream(peaks).mapToObj(p -> String.format("%.2f", p)).collect(java.util.stream.Collectors.joining(" ")));
            double worst = 0;
            for (double p : peaks) worst = Math.max(worst, p);
            assertTrue(worst < 8.0, "flew at " + worst + " m/s for " + java.util.Arrays.toString(c));
            assertTrue(peaks[peaks.length - 1] < 0.6, "still moving at " + peaks[peaks.length - 1] + " m/s after 20 s for " + java.util.Arrays.toString(c));
            assertSane(sim, 0);
        }
    }

    /**
     * Bodies thrown through the air on a rope tied to a fixed point, from many starts and speeds: none may speed up as time
     * passes, and every one must have nearly stopped within 20 s.
     */
    @Test
    void aThrownBodyOnARopeLosesEnergyWhateverItStartedWith() {
        java.util.Random rnd = new java.util.Random(7);
        double worstLate = 0, worstOverall = 0;
        String worstCase = "";
        for (int n = 0; n < 60; n++) {
            Ragdoll sim = standing(0);
            double dx = (rnd.nextDouble() - 0.5) * 12, dy = rnd.nextDouble() * 8, dz = (rnd.nextDouble() - 0.5) * 12;
            for (int i = 0; i < Skeleton.BODIES; i++) {
                sim.x[i * 3] += dx;
                sim.x[i * 3 + 1] += dy;
                sim.x[i * 3 + 2] += dz;
            }
            sim.wake();
            sim.kick((rnd.nextDouble() - 0.5) * 12, rnd.nextDouble() * 6, (rnd.nextDouble() - 0.5) * 12);
            double ay = 0.5 + rnd.nextDouble() * 4, rope = 1 + rnd.nextDouble() * 2;
            double[] peaks = tiedPeaks(sim, POST, 0, ay, 0, rope, 10, 40);
            double late = Math.max(peaks[8], peaks[9]), all = 0;
            for (double p : peaks) all = Math.max(all, p);
            if (late > worstLate) {
                worstLate = late;
                worstCase = String.format("start %.1f,%.1f,%.1f anchor y %.1f rope %.1f peaks %s", dx, dy, dz, ay, rope, java.util.Arrays.toString(peaks));
            }
            worstOverall = Math.max(worstOverall, all);
            assertSane(sim, 0);
        }
        System.out.println("[thrown] worst late speed " + worstLate + " overall " + worstOverall + " :: " + worstCase);
        assertTrue(worstLate < 1.0, "still flying at " + worstLate + " m/s: " + worstCase);
    }
}
