package com.wf.wflib.ragdoll.sim;

import com.wf.wflib.physics.api.BoxContacts;

import java.util.Arrays;

/**
 * XPBD small steps (Mueller 2020), one iteration per substep. {@link #softLimits}: spring + damping ramp over the last
 * {@link #SOFT_BAND} of a joint's range + weak pull to the relaxed posture; off => hard stops. Units m, s, kg.
 */
public final class Ragdoll {

    public static final int SUBSTEPS = 8;
    /** Contact regeneration period, substeps. */
    private static final int CONTACT_PERIOD = 2;
    public static final double DT = 0.05;
    private static final double H = DT / SUBSTEPS;
    private static final double GRAVITY = 9.81;
    private static final int MAX_CONTACTS = 96;
    /** Position error closed per substep: spawn overlap and start-pose gaps ease in, never launch. */
    private static final double MAX_PUSH = 0.03;
    private static final double MAX_SPEED = 50.0, MAX_SPIN = 40.0;
    private static final double MU_STATIC = 0.8, MU_DYNAMIC = 0.6, RESTITUTION = 0.05;
    private static final double HARD_COMPLIANCE = 1.0e-5;
    private static final double SOFT_BAND = 0.3;
    /** Joint viscosity, 1/s: tone everywhere + ramp in the soft band. */
    private static final double DAMP_BASE = 2.5, DAMP_SOFT = 35.0;
    /** Self collision box scale: rest touching parts (arm, chest) must not collide. */
    private static final double SELF_SHRINK = 0.8;
    private static final int[][] SELF_PAIRS = {
            {Skeleton.LEFT_FOREARM, Skeleton.CHEST}, {Skeleton.LEFT_FOREARM, Skeleton.TORSO},
            {Skeleton.RIGHT_FOREARM, Skeleton.CHEST}, {Skeleton.RIGHT_FOREARM, Skeleton.TORSO},
            {Skeleton.LEFT_ARM, Skeleton.TORSO}, {Skeleton.RIGHT_ARM, Skeleton.TORSO},
            {Skeleton.LEFT_FOREARM, Skeleton.HEAD}, {Skeleton.RIGHT_FOREARM, Skeleton.HEAD},
            {Skeleton.LEFT_FOREARM, Skeleton.LEFT_LEG}, {Skeleton.RIGHT_FOREARM, Skeleton.RIGHT_LEG},
            {Skeleton.LEFT_FOREARM, Skeleton.RIGHT_FOREARM},
            {Skeleton.LEFT_LEG, Skeleton.RIGHT_LEG}, {Skeleton.LEFT_SHIN, Skeleton.RIGHT_SHIN},
            {Skeleton.LEFT_LEG, Skeleton.RIGHT_SHIN}, {Skeleton.RIGHT_LEG, Skeleton.LEFT_SHIN},
    };
    private static final int CALM_TICKS = 12, DRIFT_WINDOW = 40;
    private static final double DRIFT_MAX = 0.02;
    private static final double CALM_SPEED = 0.15, CALM_SPIN = 0.8;

    /** Static boxes meeting the bounds, as {@code min x, y, z, max x, y, z}. */
    public interface World {
        /** @return box count written to {@link #boxes} */
        int collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

        double[] boxes();
    }

    public final Skeleton skeleton;
    public final double[] x = new double[Skeleton.BODIES * 3];
    public final double[] q = new double[Skeleton.BODIES * 4];
    public final double[] v = new double[Skeleton.BODIES * 3];
    public final double[] w = new double[Skeleton.BODIES * 3];
    private final double[] xPrev = new double[x.length], qPrev = new double[q.length];
    /** 0..1 submerged, set per tick by the caller. */
    public final double[] submerged = new double[Skeleton.BODIES];
    private final double[] jointBand;

    public boolean softLimits = true;
    public double gravity = GRAVITY;
    public boolean asleep;
    private int calm, windowAge;
    private final double[] windowStart = new double[x.length];

    private final BoxContacts narrow = new BoxContacts();
    /** Contact: a body, b body or -1 static; local point on a; (b: on b); world normal b -> a; separation at gen. */
    private final int[] ca = new int[MAX_CONTACTS], cb = new int[MAX_CONTACTS];
    private final double[] cLocalA = new double[MAX_CONTACTS * 3], cLocalB = new double[MAX_CONTACTS * 3];
    private final double[] cSurface = new double[MAX_CONTACTS * 3], cNormal = new double[MAX_CONTACTS * 3];
    private final double[] cLambda = new double[MAX_CONTACTS], cApproach = new double[MAX_CONTACTS];
    private int contacts;

    private double tx, ty, tz;
    private final double[] ra = new double[3], rb = new double[3];

    /** Kinematic body, -1 none: moved from its pose at {@link #grip} to {@link #gripTo} over the next step. */
    private int grip = -1;
    private final double[] gripFrom = new double[7], gripTo = new double[7];

    public Ragdoll(Skeleton skeleton) {
        this.skeleton = skeleton;
        this.jointBand = new double[skeleton.joints.length];
    }

    public void step(World world) {
        if (asleep) {
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double fastest = 0;
        for (int i = 0; i < Skeleton.BODIES; i++) {
            double reach = Math.sqrt(sq(skeleton.half[i][0]) + sq(skeleton.half[i][1]) + sq(skeleton.half[i][2]));
            double speed = len(v, i) + len(w, i) * reach;
            fastest = Math.max(fastest, speed);
            double r = reach + speed * DT + 0.05;
            minX = Math.min(minX, x[i * 3] - r);
            minY = Math.min(minY, x[i * 3 + 1] - r);
            minZ = Math.min(minZ, x[i * 3 + 2] - r);
            maxX = Math.max(maxX, x[i * 3] + r);
            maxY = Math.max(maxY, x[i * 3 + 1] + r);
            maxZ = Math.max(maxZ, x[i * 3 + 2] + r);
        }
        int boxCount = world.collect(minX, minY, minZ, maxX, maxY, maxZ);
        double[] boxes = world.boxes();
        for (int sub = 0; sub < SUBSTEPS; sub++) {
            if (sub % CONTACT_PERIOD == 0) {
                gatherContacts(boxes, boxCount, CONTACT_PERIOD * H * Math.min(fastest, MAX_SPEED) + 0.02);
            }
            integrate();
            pin(sub);
            solveJoints();
            solveContacts();
            pin(sub);
            velocities();
            contactVelocities();
            jointDamping();
        }
        settle();
    }

    /**
     * Body {@code i} follows {@code to} = {@code x, y, z, qx, qy, qz, qw} through the next step, infinite mass; the rest
     * hangs off it. Call every tick; its velocity comes out of the path, so {@link #release} keeps the momentum.
     */
    public void grip(int i, double[] to) {
        grip = i;
        System.arraycopy(x, i * 3, gripFrom, 0, 3);
        System.arraycopy(q, i * 4, gripFrom, 3, 4);
        System.arraycopy(to, 0, gripTo, 0, 7);
        if (gripFrom[3] * to[3] + gripFrom[4] * to[4] + gripFrom[5] * to[5] + gripFrom[6] * to[6] < 0) {
            for (int k = 3; k < 7; k++) {
                gripTo[k] = -to[k];
            }
        }
        wake();
    }

    public void release() {
        grip = -1;
    }

    public int gripped() {
        return grip;
    }

    private void pin(int sub) {
        if (grip < 0) {
            return;
        }
        double s = (sub + 1) / (double) SUBSTEPS, r = 1 - s;
        int o = grip * 3, p = grip * 4;
        x[o] = gripFrom[0] * r + gripTo[0] * s;
        x[o + 1] = gripFrom[1] * r + gripTo[1] * s;
        x[o + 2] = gripFrom[2] * r + gripTo[2] * s;
        double qx = gripFrom[3] * r + gripTo[3] * s, qy = gripFrom[4] * r + gripTo[4] * s;
        double qz = gripFrom[5] * r + gripTo[5] * s, qw = gripFrom[6] * r + gripTo[6] * s;
        double n = 1.0 / Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        q[p] = qx * n;
        q[p + 1] = qy * n;
        q[p + 2] = qz * n;
        q[p + 3] = qw * n;
    }

    private void settle() {
        if (grip >= 0) {
            calm = 0;
            return;
        }
        boolean still = true;
        for (int i = 0; i < Skeleton.BODIES && still; i++) {
            still = len(v, i) < CALM_SPEED && len(w, i) < CALM_SPIN;
        }
        calm = still ? calm + 1 : 0;
        if (calm >= CALM_TICKS || driftWindowEnded()) {
            asleep = true;
            Arrays.fill(v, 0);
            Arrays.fill(w, 0);
        }
    }

    /** Micro jitter on block seams can hold a speed above calm for good; nothing actually going anywhere also settles. */
    private boolean driftWindowEnded() {
        if (++windowAge < DRIFT_WINDOW) {
            return false;
        }
        windowAge = 0;
        double worst = 0;
        for (int i = 0; i < x.length; i++) {
            worst = Math.max(worst, Math.abs(x[i] - windowStart[i]));
        }
        System.arraycopy(x, 0, windowStart, 0, x.length);
        return worst < DRIFT_MAX;
    }

    public void wake() {
        asleep = false;
        calm = 0;
        windowAge = 0;
        System.arraycopy(x, 0, windowStart, 0, x.length);
    }

    /** Impulse N s at world point {@code (px, py, pz)} on body {@code i}. */
    public void impulse(int i, double px, double py, double pz, double jx, double jy, double jz) {
        wake();
        double m = skeleton.inverseMass[i];
        v[i * 3] += jx * m;
        v[i * 3 + 1] += jy * m;
        v[i * 3 + 2] += jz * m;
        double rx = px - x[i * 3], ry = py - x[i * 3 + 1], rz = pz - x[i * 3 + 2];
        inverseInertia(i, ry * jz - rz * jy, rz * jx - rx * jz, rx * jy - ry * jx);
        w[i * 3] += tx;
        w[i * 3 + 1] += ty;
        w[i * 3 + 2] += tz;
    }

    /** Whole body velocity change. */
    public void kick(double dvx, double dvy, double dvz) {
        wake();
        for (int i = 0; i < Skeleton.BODIES; i++) {
            v[i * 3] += dvx;
            v[i * 3 + 1] += dvy;
            v[i * 3 + 2] += dvz;
        }
    }

    public void spin(int i, double wx, double wy, double wz) {
        wake();
        w[i * 3] += wx;
        w[i * 3 + 1] += wy;
        w[i * 3 + 2] += wz;
    }

    /** Lowest point of the world box of every body. */
    public double lowest() {
        double low = Double.MAX_VALUE;
        for (int i = 0; i < Skeleton.BODIES; i++) {
            low = Math.min(low, x[i * 3 + 1] - extentY(i));
        }
        return low;
    }

    private double extentY(int i) {
        double[] h = skeleton.half[i];
        double a = q[i * 4], b = q[i * 4 + 1], c = q[i * 4 + 2], d = q[i * 4 + 3];
        double xy = 2 * (a * b + c * d), yy = 1 - 2 * (a * a + c * c), zy = 2 * (b * c - a * d);
        return Math.abs(xy) * h[0] + Math.abs(yy) * h[1] + Math.abs(zy) * h[2];
    }

    private void gatherContacts(double[] boxes, int boxCount, double margin) {
        contacts = 0;
        for (int i = 0; i < Skeleton.BODIES; i++) {
            double[] h = skeleton.half[i];
            narrow.pose(x[i * 3], x[i * 3 + 1], x[i * 3 + 2], q[i * 4], q[i * 4 + 1], q[i * 4 + 2], q[i * 4 + 3],
                    h[0], h[1], h[2]);
            double r = Math.sqrt(sq(h[0]) + sq(h[1]) + sq(h[2])) + margin;
            for (int k = 0; k < boxCount; k++) {
                int o = k * 6;
                if (x[i * 3] + r < boxes[o] || x[i * 3] - r > boxes[o + 3] || x[i * 3 + 1] + r < boxes[o + 1]
                        || x[i * 3 + 1] - r > boxes[o + 4] || x[i * 3 + 2] + r < boxes[o + 2] || x[i * 3 + 2] - r > boxes[o + 5]) {
                    continue;
                }
                int n = narrow.againstWorld(boxes[o], boxes[o + 1], boxes[o + 2], boxes[o + 3], boxes[o + 4], boxes[o + 5], margin);
                for (int c = 0; c < n; c++) {
                    addContact(i, -1, narrow.x(c), narrow.y(c), narrow.z(c), narrow.normalX(c), narrow.normalY(c),
                            narrow.normalZ(c), narrow.separation(c));
                }
            }
        }
        for (int[] pair : SELF_PAIRS) {
            int a = pair[0], b = pair[1];
            double[] ha = skeleton.half[a], hb = skeleton.half[b];
            double dx = x[a * 3] - x[b * 3], dy = x[a * 3 + 1] - x[b * 3 + 1], dz = x[a * 3 + 2] - x[b * 3 + 2];
            double r = Math.sqrt(sq(ha[0]) + sq(ha[1]) + sq(ha[2])) + Math.sqrt(sq(hb[0]) + sq(hb[1]) + sq(hb[2])) + margin;
            if (dx * dx + dy * dy + dz * dz > r * r) {
                continue;
            }
            narrow.pose(x[a * 3], x[a * 3 + 1], x[a * 3 + 2], q[a * 4], q[a * 4 + 1], q[a * 4 + 2], q[a * 4 + 3],
                    ha[0] * SELF_SHRINK, ha[1] * SELF_SHRINK, ha[2] * SELF_SHRINK);
            int n = narrow.pair(x[b * 3], x[b * 3 + 1], x[b * 3 + 2], q[b * 4], q[b * 4 + 1], q[b * 4 + 2], q[b * 4 + 3],
                    hb[0] * SELF_SHRINK, hb[1] * SELF_SHRINK, hb[2] * SELF_SHRINK, margin);
            for (int c = 0; c < n; c++) {
                // Normal b -> a.
                addContact(a, b, narrow.x(c), narrow.y(c), narrow.z(c), narrow.normalX(c), narrow.normalY(c),
                        narrow.normalZ(c), narrow.separation(c));
            }
        }
    }

    private void addContact(int a, int b, double px, double py, double pz, double nx, double ny, double nz, double sep) {
        if (contacts == MAX_CONTACTS) {
            return;
        }
        int c = contacts++;
        ca[c] = a;
        cb[c] = b;
        cNormal[c * 3] = nx;
        cNormal[c * 3 + 1] = ny;
        cNormal[c * 3 + 2] = nz;
        toLocal(a, px - x[a * 3], py - x[a * 3 + 1], pz - x[a * 3 + 2]);
        cLocalA[c * 3] = tx;
        cLocalA[c * 3 + 1] = ty;
        cLocalA[c * 3 + 2] = tz;
        if (b >= 0) {
            toLocal(b, px - nx * sep - x[b * 3], py - ny * sep - x[b * 3 + 1], pz - nz * sep - x[b * 3 + 2]);
            cLocalB[c * 3] = tx;
            cLocalB[c * 3 + 1] = ty;
            cLocalB[c * 3 + 2] = tz;
        }
        // World point the a side must stay on the +n side of.
        cSurface[c * 3] = px - nx * sep;
        cSurface[c * 3 + 1] = py - ny * sep;
        cSurface[c * 3 + 2] = pz - nz * sep;
    }

    private void integrate() {
        for (int i = 0; i < Skeleton.BODIES; i++) {
            int o = i * 3, r = i * 4;
            xPrev[o] = x[o];
            xPrev[o + 1] = x[o + 1];
            xPrev[o + 2] = x[o + 2];
            System.arraycopy(q, r, qPrev, r, 4);
            double sink = submerged[i];
            v[o + 1] -= H * gravity * (1.0 - 1.05 * sink);
            double drag = Math.exp(-H * (0.02 + 3.0 * sink));
            v[o] *= drag;
            v[o + 1] *= drag;
            v[o + 2] *= drag;
            double spinDrag = Math.exp(-H * (0.1 + 3.0 * sink));
            w[o] *= spinDrag;
            w[o + 1] *= spinDrag;
            w[o + 2] *= spinDrag;
            clamp(v, i, MAX_SPEED);
            clamp(w, i, MAX_SPIN);
            x[o] += H * v[o];
            x[o + 1] += H * v[o + 1];
            x[o + 2] += H * v[o + 2];
            rotate(i, H * w[o], H * w[o + 1], H * w[o + 2]);
        }
    }

    private static void clamp(double[] a, int i, double max) {
        double l = len(a, i);
        if (l > max) {
            double s = max / l;
            a[i * 3] *= s;
            a[i * 3 + 1] *= s;
            a[i * 3 + 2] *= s;
        }
    }

    private void solveJoints() {
        Skeleton.Joint[] js = skeleton.joints;
        for (int k = 0; k < js.length; k++) {
            Skeleton.Joint j = js[k];
            solveBall(j);
            solveAngular(j, k);
        }
    }

    private void solveBall(Skeleton.Joint j) {
        toWorld(j.a, j.anchorA[0], j.anchorA[1], j.anchorA[2]);
        ra[0] = tx;
        ra[1] = ty;
        ra[2] = tz;
        toWorld(j.b, j.anchorB[0], j.anchorB[1], j.anchorB[2]);
        rb[0] = tx;
        rb[1] = ty;
        rb[2] = tz;
        double dx = x[j.b * 3] + rb[0] - x[j.a * 3] - ra[0];
        double dy = x[j.b * 3 + 1] + rb[1] - x[j.a * 3 + 1] - ra[1];
        double dz = x[j.b * 3 + 2] + rb[2] - x[j.a * 3 + 2] - ra[2];
        double c = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (c < 1.0e-9) {
            return;
        }
        positional(j.b, j.a, rb[0], rb[1], rb[2], ra[0], ra[1], ra[2], dx / c, dy / c, dz / c, Math.min(c, MAX_PUSH));
    }

    private void solveAngular(Skeleton.Joint j, int k) {
        // rel = qA^-1 qB = swing twist, twist about part y.
        int a = j.a * 4, b = j.b * 4;
        double ax = -q[a], ay = -q[a + 1], az = -q[a + 2], aw = q[a + 3];
        double bx = q[b], by = q[b + 1], bz = q[b + 2], bw = q[b + 3];
        double rx = aw * bx + ax * bw + ay * bz - az * by;
        double ry = aw * by - ax * bz + ay * bw + az * bx;
        double rz = aw * bz + ax * by - ay * bx + az * bw;
        double rw = aw * bw - ax * bx - ay * by - az * bz;
        if (rw < 0) {
            rx = -rx;
            ry = -ry;
            rz = -rz;
            rw = -rw;
        }
        double tn = Math.sqrt(ry * ry + rw * rw);
        double ty0 = tn > 1.0e-9 ? ry / tn : 0, tw0 = tn > 1.0e-9 ? rw / tn : 1;
        double twist = 2 * Math.atan2(ty0, tw0);
        // swing = rel conj(twist)
        double sx = rx * tw0 + rz * ty0;
        double sz = rz * tw0 - rx * ty0;
        double sw = rw * tw0 + ry * ty0;
        double sl = Math.sqrt(sx * sx + sz * sz);
        double angle = 2 * Math.atan2(sl, sw);
        double swingX = sl > 1.0e-9 ? sx / sl * angle : 0, swingZ = sl > 1.0e-9 ? sz / sl * angle : 0;

        double band = 0;
        double errX = 0, errZ = 0;
        double softX = 0, softZ = 0;
        switch (j.kind) {
            case HINGE -> {
                alignHinge(j);
                double span = j.sxMax - j.sxMin;
                double bandWidth = SOFT_BAND * span;
                if (swingX > j.sxMax) {
                    errX = swingX - j.sxMax;
                } else if (swingX < j.sxMin) {
                    errX = swingX - j.sxMin;
                }
                if (swingX > j.sxMax - bandWidth) {
                    band = Math.min((swingX - (j.sxMax - bandWidth)) / bandWidth, 1);
                    softX = swingX - (j.sxMax - bandWidth);
                } else if (swingX < j.sxMin + bandWidth) {
                    band = Math.min((j.sxMin + bandWidth - swingX) / bandWidth, 1);
                    softX = swingX - (j.sxMin + bandWidth);
                }
            }
            case BEND, BALL -> {
                double lx = swingX >= 0 ? j.sxMax : -j.sxMin, lz = swingZ >= 0 ? j.szMax : -j.szMin;
                double n = Math.sqrt(sq(swingX / lx) + sq(swingZ / lz));
                if (n > 1) {
                    errX = swingX * (1 - 1 / n);
                    errZ = swingZ * (1 - 1 / n);
                }
                if (n > 1 - SOFT_BAND) {
                    band = Math.min((n - (1 - SOFT_BAND)) / SOFT_BAND, 1);
                    double scale = 1 - (1 - SOFT_BAND) / n;
                    softX = swingX * scale;
                    softZ = swingZ * scale;
                }
            }
        }
        double errTwist = j.kind == Skeleton.Kind.HINGE ? 0 : j.kind == Skeleton.Kind.BEND ? twist
                : twist > j.twMax ? twist - j.twMax : twist < j.twMin ? twist - j.twMin : 0;

        if (errX != 0 || errZ != 0) {
            swing(j, errX, errZ, HARD_COMPLIANCE);
        }
        if (errTwist != 0) {
            twist(j, errTwist, HARD_COMPLIANCE);
        }
        if (!softLimits) {
            jointBand[k] = 0;
            return;
        }
        jointBand[k] = band;
        if (band > 0 && (softX != 0 || softZ != 0)) {
            swing(j, softX, softZ, 1.0 / (j.soft * (0.05 + band * band)));
        }
        swing(j, swingX - j.nx, j.kind == Skeleton.Kind.HINGE ? 0 : swingZ - j.nz, 1.0 / j.neutral);
        if (j.kind == Skeleton.Kind.BALL) {
            twist(j, twist, 1.0 / j.neutral);
        }
    }

    /** Hinge axis (part x) of the child onto the parent's. Off-axis swing + twist are coupled at large flexion: removing them apart diverges. */
    private void alignHinge(Skeleton.Joint j) {
        toWorld(j.a, 1, 0, 0);
        double ax = tx, ay = ty, az = tz;
        toWorld(j.b, 1, 0, 0);
        angular(j.a, j.b, ay * tz - az * ty, az * tx - ax * tz, ax * ty - ay * tx, HARD_COMPLIANCE);
    }

    /** Remove swing {@code (ex, ez)} (parent frame). */
    private void swing(Skeleton.Joint j, double ex, double ez, double compliance) {
        toWorld(j.a, ex, 0, ez);
        angular(j.a, j.b, tx, ty, tz, compliance);
    }

    /** Remove twist about the child's y. */
    private void twist(Skeleton.Joint j, double e, double compliance) {
        toWorld(j.b, 0, e, 0);
        angular(j.a, j.b, tx, ty, tz, compliance);
    }

    /** Rotates b by -e and a by +e, shared by inverse inertia. */
    private void angular(int a, int b, double ex, double ey, double ez, double compliance) {
        double angle = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (angle < 1.0e-9) {
            return;
        }
        double nx = ex / angle, ny = ey / angle, nz = ez / angle;
        double wa = generalized(a, nx, ny, nz), wb = generalized(b, nx, ny, nz);
        double lambda = angle / (wa + wb + compliance / (H * H));
        double px = nx * lambda, py = ny * lambda, pz = nz * lambda;
        inverseInertia(a, px, py, pz);
        rotate(a, tx, ty, tz);
        inverseInertia(b, px, py, pz);
        rotate(b, -tx, -ty, -tz);
    }

    private void solveContacts() {
        for (int c = 0; c < contacts; c++) {
            cLambda[c] = 0;
            int a = ca[c], b = cb[c];
            double nx = cNormal[c * 3], ny = cNormal[c * 3 + 1], nz = cNormal[c * 3 + 2];
            toWorld(a, cLocalA[c * 3], cLocalA[c * 3 + 1], cLocalA[c * 3 + 2]);
            double rax = tx, ray = ty, raz = tz;
            double pax = x[a * 3] + rax, pay = x[a * 3 + 1] + ray, paz = x[a * 3 + 2] + raz;
            double sx, sy, sz, rbx = 0, rby = 0, rbz = 0;
            if (b >= 0) {
                toWorld(b, cLocalB[c * 3], cLocalB[c * 3 + 1], cLocalB[c * 3 + 2]);
                rbx = tx;
                rby = ty;
                rbz = tz;
                sx = x[b * 3] + rbx;
                sy = x[b * 3 + 1] + rby;
                sz = x[b * 3 + 2] + rbz;
            } else {
                sx = cSurface[c * 3];
                sy = cSurface[c * 3 + 1];
                sz = cSurface[c * 3 + 2];
            }
            double sep = (pax - sx) * nx + (pay - sy) * ny + (paz - sz) * nz;
            if (sep >= 0) {
                continue;
            }
            cApproach[c] = pointVelocityAlong(a, b, rax, ray, raz, rbx, rby, rbz, nx, ny, nz);
            double push = Math.min(-sep, MAX_PUSH);
            double lambda = positional(b, a, rbx, rby, rbz, rax, ray, raz, nx, ny, nz, push);
            cLambda[c] = lambda;

            // Static friction: undo tangential slip of the point this substep while within the cone.
            toWorld(a, cLocalA[c * 3], cLocalA[c * 3 + 1], cLocalA[c * 3 + 2]);
            rax = tx;
            ray = ty;
            raz = tz;
            prevWorld(a, cLocalA[c * 3], cLocalA[c * 3 + 1], cLocalA[c * 3 + 2]);
            double dx = x[a * 3] + rax - xPrev[a * 3] - tx;
            double dy = x[a * 3 + 1] + ray - xPrev[a * 3 + 1] - ty;
            double dz = x[a * 3 + 2] + raz - xPrev[a * 3 + 2] - tz;
            if (b >= 0) {
                toWorld(b, cLocalB[c * 3], cLocalB[c * 3 + 1], cLocalB[c * 3 + 2]);
                rbx = tx;
                rby = ty;
                rbz = tz;
                prevWorld(b, cLocalB[c * 3], cLocalB[c * 3 + 1], cLocalB[c * 3 + 2]);
                dx -= x[b * 3] + rbx - xPrev[b * 3] - tx;
                dy -= x[b * 3 + 1] + rby - xPrev[b * 3 + 1] - ty;
                dz -= x[b * 3 + 2] + rbz - xPrev[b * 3 + 2] - tz;
            }
            double dn = dx * nx + dy * ny + dz * nz;
            dx -= dn * nx;
            dy -= dn * ny;
            dz -= dn * nz;
            double slip = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (slip < 1.0e-9) {
                continue;
            }
            double ux = dx / slip, uy = dy / slip, uz = dz / slip;
            double weight = pointWeight(a, rax, ray, raz, ux, uy, uz) + (b >= 0 ? pointWeight(b, rbx, rby, rbz, ux, uy, uz) : 0);
            if (slip / weight <= MU_STATIC * lambda) {
                positional(b, a, rbx, rby, rbz, rax, ray, raz, -ux, -uy, -uz, slip);
            }
        }
    }

    private double pointVelocityAlong(int a, int b, double rax, double ray, double raz, double rbx, double rby, double rbz,
                                      double nx, double ny, double nz) {
        double vx = v[a * 3] + w[a * 3 + 1] * raz - w[a * 3 + 2] * ray;
        double vy = v[a * 3 + 1] + w[a * 3 + 2] * rax - w[a * 3] * raz;
        double vz = v[a * 3 + 2] + w[a * 3] * ray - w[a * 3 + 1] * rax;
        if (b >= 0) {
            vx -= v[b * 3] + w[b * 3 + 1] * rbz - w[b * 3 + 2] * rby;
            vy -= v[b * 3 + 1] + w[b * 3 + 2] * rbx - w[b * 3] * rbz;
            vz -= v[b * 3 + 2] + w[b * 3] * rby - w[b * 3 + 1] * rbx;
        }
        return vx * nx + vy * ny + vz * nz;
    }

    private void velocities() {
        double invH = 1.0 / H;
        for (int i = 0; i < Skeleton.BODIES; i++) {
            int o = i * 3, r = i * 4;
            v[o] = (x[o] - xPrev[o]) * invH;
            v[o + 1] = (x[o + 1] - xPrev[o + 1]) * invH;
            v[o + 2] = (x[o + 2] - xPrev[o + 2]) * invH;
            // dq = q conj(qPrev)
            double ax = q[r], ay = q[r + 1], az = q[r + 2], aw = q[r + 3];
            double bx = -qPrev[r], by = -qPrev[r + 1], bz = -qPrev[r + 2], bw = qPrev[r + 3];
            double dx = aw * bx + ax * bw + ay * bz - az * by;
            double dy = aw * by - ax * bz + ay * bw + az * bx;
            double dz = aw * bz + ax * by - ay * bx + az * bw;
            double dw = aw * bw - ax * bx - ay * by - az * bz;
            double sign = dw >= 0 ? 2 * invH : -2 * invH;
            w[o] = dx * sign;
            w[o + 1] = dy * sign;
            w[o + 2] = dz * sign;
        }
    }

    private void contactVelocities() {
        for (int c = 0; c < contacts; c++) {
            double lambda = cLambda[c];
            if (lambda <= 0) {
                continue;
            }
            int a = ca[c], b = cb[c];
            double nx = cNormal[c * 3], ny = cNormal[c * 3 + 1], nz = cNormal[c * 3 + 2];
            toWorld(a, cLocalA[c * 3], cLocalA[c * 3 + 1], cLocalA[c * 3 + 2]);
            double rax = tx, ray = ty, raz = tz;
            double rbx = 0, rby = 0, rbz = 0;
            if (b >= 0) {
                toWorld(b, cLocalB[c * 3], cLocalB[c * 3 + 1], cLocalB[c * 3 + 2]);
                rbx = tx;
                rby = ty;
                rbz = tz;
            }
            double vx = v[a * 3] + w[a * 3 + 1] * raz - w[a * 3 + 2] * ray;
            double vy = v[a * 3 + 1] + w[a * 3 + 2] * rax - w[a * 3] * raz;
            double vz = v[a * 3 + 2] + w[a * 3] * ray - w[a * 3 + 1] * rax;
            if (b >= 0) {
                vx -= v[b * 3] + w[b * 3 + 1] * rbz - w[b * 3 + 2] * rby;
                vy -= v[b * 3 + 1] + w[b * 3 + 2] * rbx - w[b * 3] * rbz;
                vz -= v[b * 3 + 2] + w[b * 3] * rby - w[b * 3 + 1] * rbx;
            }
            double vn = vx * nx + vy * ny + vz * nz;
            double ux = vx - vn * nx, uy = vy - vn * ny, uz = vz - vn * nz;
            double vt = Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (vt > 1.0e-9) {
                double weight = pointWeight(a, rax, ray, raz, ux / vt, uy / vt, uz / vt)
                        + (b >= 0 ? pointWeight(b, rbx, rby, rbz, ux / vt, uy / vt, uz / vt) : 0);
                velocityImpulse(a, b, rax, ray, raz, rbx, rby, rbz, -ux / vt, -uy / vt, -uz / vt,
                        Math.min(MU_DYNAMIC * lambda / H, vt / weight));
            }
            double bounce = cApproach[c] < -2 * gravity * H ? -RESTITUTION * cApproach[c] : 0;
            double dv = bounce - vn;
            if (dv != 0) {
                double weight = pointWeight(a, rax, ray, raz, nx, ny, nz) + (b >= 0 ? pointWeight(b, rbx, rby, rbz, nx, ny, nz) : 0);
                velocityImpulse(a, b, rax, ray, raz, rbx, rby, rbz, nx, ny, nz, dv / weight);
            }
        }
    }

    private void velocityImpulse(int a, int b, double rax, double ray, double raz, double rbx, double rby, double rbz,
                                 double nx, double ny, double nz, double j) {
        double px = nx * j, py = ny * j, pz = nz * j;
        double ma = skeleton.inverseMass[a];
        v[a * 3] += px * ma;
        v[a * 3 + 1] += py * ma;
        v[a * 3 + 2] += pz * ma;
        inverseInertia(a, ray * pz - raz * py, raz * px - rax * pz, rax * py - ray * px);
        w[a * 3] += tx;
        w[a * 3 + 1] += ty;
        w[a * 3 + 2] += tz;
        if (b >= 0) {
            double mb = skeleton.inverseMass[b];
            v[b * 3] -= px * mb;
            v[b * 3 + 1] -= py * mb;
            v[b * 3 + 2] -= pz * mb;
            inverseInertia(b, rby * pz - rbz * py, rbz * px - rbx * pz, rbx * py - rby * px);
            w[b * 3] -= tx;
            w[b * 3 + 1] -= ty;
            w[b * 3 + 2] -= tz;
        }
    }

    private void jointDamping() {
        Skeleton.Joint[] js = skeleton.joints;
        for (int k = 0; k < js.length; k++) {
            Skeleton.Joint j = js[k];
            int a = j.a * 3, b = j.b * 3;
            double dx = w[b] - w[a], dy = w[b + 1] - w[a + 1], dz = w[b + 2] - w[a + 2];
            double spin = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (spin < 1.0e-9) {
                continue;
            }
            double band = jointBand[k];
            double rate = softLimits ? DAMP_BASE + DAMP_SOFT * band * band : 0.2;
            double keep = 1 - Math.exp(-rate * H);
            double nx = dx / spin, ny = dy / spin, nz = dz / spin;
            double weight = generalized(j.a, nx, ny, nz) + generalized(j.b, nx, ny, nz);
            double l = keep * spin / weight;
            inverseInertia(j.a, nx * l, ny * l, nz * l);
            w[a] += tx;
            w[a + 1] += ty;
            w[a + 2] += tz;
            inverseInertia(j.b, nx * l, ny * l, nz * l);
            w[b] -= tx;
            w[b + 1] -= ty;
            w[b + 2] -= tz;
        }
    }

    /** Moves i along +n, j along -n ({@code -1} static) closing {@code c}. @return lambda */
    private double positional(int j, int i, double rjx, double rjy, double rjz, double rix, double riy, double riz,
                              double nx, double ny, double nz, double c) {
        double wi = pointWeight(i, rix, riy, riz, nx, ny, nz);
        double wj = j >= 0 ? pointWeight(j, rjx, rjy, rjz, nx, ny, nz) : 0;
        double lambda = c / (wi + wj);
        double px = nx * lambda, py = ny * lambda, pz = nz * lambda;
        double mi = skeleton.inverseMass[i];
        x[i * 3] += px * mi;
        x[i * 3 + 1] += py * mi;
        x[i * 3 + 2] += pz * mi;
        inverseInertia(i, riy * pz - riz * py, riz * px - rix * pz, rix * py - riy * px);
        rotate(i, tx, ty, tz);
        if (j >= 0) {
            double mj = skeleton.inverseMass[j];
            x[j * 3] -= px * mj;
            x[j * 3 + 1] -= py * mj;
            x[j * 3 + 2] -= pz * mj;
            inverseInertia(j, rjy * pz - rjz * py, rjz * px - rjx * pz, rjx * py - rjy * px);
            rotate(j, -tx, -ty, -tz);
        }
        return lambda;
    }

    private double pointWeight(int i, double rx, double ry, double rz, double nx, double ny, double nz) {
        return skeleton.inverseMass[i] + generalized(i, ry * nz - rz * ny, rz * nx - rx * nz, rx * ny - ry * nx);
    }

    /** {@code n^T I^-1 n}, world n (need not be unit). */
    private double generalized(int i, double nx, double ny, double nz) {
        toLocal(i, nx, ny, nz);
        double[] ii = skeleton.inverseInertia[i];
        return tx * tx * ii[0] + ty * ty * ii[1] + tz * tz * ii[2];
    }

    /** {@code I^-1 world(tx, ty, tz)}, world. */
    private void inverseInertia(int i, double nx, double ny, double nz) {
        toLocal(i, nx, ny, nz);
        double[] ii = skeleton.inverseInertia[i];
        toWorld(i, tx * ii[0], ty * ii[1], tz * ii[2]);
    }

    /** {@code q += 0.5 (d, 0) q}, normalized. */
    private void rotate(int i, double dx, double dy, double dz) {
        int r = i * 4;
        double ax = q[r], ay = q[r + 1], az = q[r + 2], aw = q[r + 3];
        double nx = ax + 0.5 * (dx * aw + dy * az - dz * ay);
        double ny = ay + 0.5 * (dy * aw + dz * ax - dx * az);
        double nz = az + 0.5 * (dz * aw + dx * ay - dy * ax);
        double nw = aw + 0.5 * (-dx * ax - dy * ay - dz * az);
        double s = 1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz + nw * nw);
        q[r] = nx * s;
        q[r + 1] = ny * s;
        q[r + 2] = nz * s;
        q[r + 3] = nw * s;
    }

    private void toWorld(int i, double vx, double vy, double vz) {
        rotateBy(q[i * 4], q[i * 4 + 1], q[i * 4 + 2], q[i * 4 + 3], vx, vy, vz);
    }

    private void prevWorld(int i, double vx, double vy, double vz) {
        rotateBy(qPrev[i * 4], qPrev[i * 4 + 1], qPrev[i * 4 + 2], qPrev[i * 4 + 3], vx, vy, vz);
    }

    private void toLocal(int i, double vx, double vy, double vz) {
        rotateBy(-q[i * 4], -q[i * 4 + 1], -q[i * 4 + 2], q[i * 4 + 3], vx, vy, vz);
    }

    private void rotateBy(double a, double b, double c, double d, double vx, double vy, double vz) {
        double ux = 2 * (b * vz - c * vy), uy = 2 * (c * vx - a * vz), uz = 2 * (a * vy - b * vx);
        tx = vx + d * ux + b * uz - c * uy;
        ty = vy + d * uy + c * ux - a * uz;
        tz = vz + d * uz + a * uy - b * ux;
    }

    private static double len(double[] a, int i) {
        return Math.sqrt(sq(a[i * 3]) + sq(a[i * 3 + 1]) + sq(a[i * 3 + 2]));
    }

    private static double sq(double a) {
        return a * a;
    }
}
