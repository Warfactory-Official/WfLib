package com.wf.wflib.ragdoll.sim;

/**
 * Body + joint table from the AHF rig spec: {@code {ox, oy, oz, sx, sy, sz, px, py, pz}} per part, model px. Body axes =
 * part axes (y down the limb), rest rotation identity. Forward = -z: limb flexes forward at sx < 0.
 */
public final class Skeleton {

    public static final int HEAD = 0, TORSO = 1, CHEST = 2, LEFT_ARM = 3, LEFT_FOREARM = 4, RIGHT_ARM = 5,
            RIGHT_FOREARM = 6, LEFT_LEG = 7, LEFT_SHIN = 8, RIGHT_LEG = 9, RIGHT_SHIN = 10, BODIES = 11;
    public static final double MODEL_SCALE = 0.9375;
    /** Model px -> blocks. */
    public static final double S = MODEL_SCALE / 16.0;
    /** {@code HumanoidRig.Y_SHIFT}, px. */
    public static final double Y_SHIFT_PX = 1.501 * 16.0;

    private static final double TOTAL_MASS = 80.0;
    /** Winter body segment shares; forearm incl. hand, shin incl. foot. */
    private static final double[] SHARE = {0.08, 0.25, 0.25, 0.03, 0.022, 0.03, 0.022, 0.10, 0.06, 0.10, 0.06};

    public enum Kind {HINGE, BEND, BALL}

    /** Angles rad, sx about part x, sz about part z, twist about part y; neutral = Mount 2003 relaxed posture. */
    public static final class Joint {
        public final int a, b;
        public final Kind kind;
        final double[] anchorA, anchorB;
        final double sxMin, sxMax, szMin, szMax, twMin, twMax;
        final double nx, nz;
        /** N m / rad. */
        final double neutral, soft;

        Joint(int a, int b, Kind kind, double[] anchorA, double[] anchorB, double[] limitsDeg, double[] neutralDeg,
              double neutral, double soft) {
            this.a = a;
            this.b = b;
            this.kind = kind;
            this.anchorA = anchorA;
            this.anchorB = anchorB;
            sxMin = Math.toRadians(limitsDeg[0]);
            sxMax = Math.toRadians(limitsDeg[1]);
            szMin = Math.toRadians(limitsDeg[2]);
            szMax = Math.toRadians(limitsDeg[3]);
            twMin = Math.toRadians(limitsDeg[4]);
            twMax = Math.toRadians(limitsDeg[5]);
            nx = Math.toRadians(neutralDeg[0]);
            nz = Math.toRadians(neutralDeg[1]);
            this.neutral = neutral;
            this.soft = soft;
        }
    }

    public final double[][] half = new double[BODIES][3];
    public final double[] inverseMass = new double[BODIES];
    /** Body frame diagonal. */
    public final double[][] inverseInertia = new double[BODIES][3];
    public final Joint[] joints;
    /** Rest offsets from the torso part pivot, px: head, right arm, left arm, right leg, left leg pivots. */
    final double[] dHead, dRightArm, dLeftArm, dRightLeg, dLeftLeg;
    /** Torso part pivot -> lower half centre, px. */
    final double[] torsoCentre;

    public Skeleton(double[] head, double[] torso, double[] leftArm, double[] rightArm, double[] leftLeg, double[] rightLeg) {
        box(HEAD, head, false);
        box(TORSO, torso, true);
        box(CHEST, torso, true);
        box(LEFT_ARM, leftArm, true);
        box(LEFT_FOREARM, leftArm, true);
        box(RIGHT_ARM, rightArm, true);
        box(RIGHT_FOREARM, rightArm, true);
        box(LEFT_LEG, leftLeg, true);
        box(LEFT_SHIN, leftLeg, true);
        box(RIGHT_LEG, rightLeg, true);
        box(RIGHT_SHIN, rightLeg, true);
        for (int i = 0; i < BODIES; i++) {
            double m = TOTAL_MASS * SHARE[i], k = 3.0 / m;
            double ex = half[i][0], ey = half[i][1], ez = half[i][2];
            inverseMass[i] = 1.0 / m;
            inverseInertia[i][0] = k / (ey * ey + ez * ez);
            inverseInertia[i][1] = k / (ex * ex + ez * ez);
            inverseInertia[i][2] = k / (ex * ex + ey * ey);
        }

        double[] tp = pivot(torso);
        torsoCentre = sub(centre(torso, 0.75), tp);
        dHead = sub(pivot(head), tp);
        dRightArm = sub(pivot(rightArm), tp);
        dLeftArm = sub(pivot(leftArm), tp);
        dRightLeg = sub(pivot(rightLeg), tp);
        dLeftLeg = sub(pivot(leftLeg), tp);

        double[] chestC = centre(torso, 0.25), torsoC = centre(torso, 0.75);
        joints = new Joint[]{
                new Joint(TORSO, CHEST, Kind.BEND, local(centre(torso, 0.5), torsoC), local(centre(torso, 0.5), chestC),
                        new double[]{-25, 55, -25, 25, 0, 0}, new double[]{5, 0}, 30, 300),
                new Joint(CHEST, HEAD, Kind.BALL, local(pivot(head), chestC), local(pivot(head), centre(head, 0.5)),
                        new double[]{-40, 55, -35, 35, -60, 60}, new double[]{15, 0}, 6, 60),
                new Joint(CHEST, RIGHT_ARM, Kind.BALL, local(pivot(rightArm), chestC), local(pivot(rightArm), centre(rightArm, 0.25)),
                        new double[]{-170, 50, -25, 160, -60, 60}, new double[]{-35, 25}, 5, 70),
                new Joint(RIGHT_ARM, RIGHT_FOREARM, Kind.HINGE, local(centre(rightArm, 0.5), centre(rightArm, 0.25)),
                        local(centre(rightArm, 0.5), centre(rightArm, 0.75)),
                        new double[]{-145, 3, 0, 0, 0, 0}, new double[]{-55, 0}, 1, 20),
                new Joint(CHEST, LEFT_ARM, Kind.BALL, local(pivot(leftArm), chestC), local(pivot(leftArm), centre(leftArm, 0.25)),
                        new double[]{-170, 50, -160, 25, -60, 60}, new double[]{-35, -25}, 5, 70),
                new Joint(LEFT_ARM, LEFT_FOREARM, Kind.HINGE, local(centre(leftArm, 0.5), centre(leftArm, 0.25)),
                        local(centre(leftArm, 0.5), centre(leftArm, 0.75)),
                        new double[]{-145, 3, 0, 0, 0, 0}, new double[]{-55, 0}, 1, 20),
                new Joint(TORSO, RIGHT_LEG, Kind.BALL, local(pivot(rightLeg), torsoC), local(pivot(rightLeg), centre(rightLeg, 0.25)),
                        new double[]{-110, 25, -25, 45, -35, 35}, new double[]{-30, 8}, 15, 200),
                new Joint(RIGHT_LEG, RIGHT_SHIN, Kind.HINGE, local(centre(rightLeg, 0.5), centre(rightLeg, 0.25)),
                        local(centre(rightLeg, 0.5), centre(rightLeg, 0.75)),
                        new double[]{-3, 140, 0, 0, 0, 0}, new double[]{35, 0}, 3, 40),
                new Joint(TORSO, LEFT_LEG, Kind.BALL, local(pivot(leftLeg), torsoC), local(pivot(leftLeg), centre(leftLeg, 0.25)),
                        new double[]{-110, 25, -45, 25, -35, 35}, new double[]{-30, -8}, 15, 200),
                new Joint(LEFT_LEG, LEFT_SHIN, Kind.HINGE, local(centre(leftLeg, 0.5), centre(leftLeg, 0.25)),
                        local(centre(leftLeg, 0.5), centre(leftLeg, 0.75)),
                        new double[]{-3, 140, 0, 0, 0, 0}, new double[]{35, 0}, 3, 40),
        };
    }

    private void box(int i, double[] p, boolean split) {
        half[i][0] = p[3] * 0.5 * S;
        half[i][1] = p[4] * (split ? 0.25 : 0.5) * S;
        half[i][2] = p[5] * 0.5 * S;
    }

    private static double[] pivot(double[] p) {
        return new double[]{p[6], p[7], p[8]};
    }

    /** Box point at {@code fy} of its height, x + z centred; model px. */
    private static double[] centre(double[] p, double fy) {
        return new double[]{p[6] + p[0] + p[3] * 0.5, p[7] + p[1] + p[4] * fy, p[8] + p[2] + p[5] * 0.5};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] local(double[] point, double[] bodyCentre) {
        return new double[]{(point[0] - bodyCentre[0]) * S, (point[1] - bodyCentre[1]) * S, (point[2] - bodyCentre[2]) * S};
    }
}
