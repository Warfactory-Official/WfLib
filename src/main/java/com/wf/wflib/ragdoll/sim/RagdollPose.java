package com.wf.wflib.ragdoll.sim;

import com.norwood.ahf.pose.PoseClip;

/**
 * Wire + draw state: torso centre + all rotations. Everything drawn comes from {@link #channels} (FK down the bones from
 * the torso, knee / elbow / waist twist dropped), so joints never open and drawn == rig.
 */
public final class RagdollPose {

    public double x, y, z;
    /** Torso centre y - entity feet y. */
    public float footDrop;
    public final double[] q = new double[Skeleton.BODIES * 4];

    public void from(Ragdoll sim) {
        x = sim.x[Skeleton.TORSO * 3];
        y = sim.x[Skeleton.TORSO * 3 + 1];
        z = sim.x[Skeleton.TORSO * 3 + 2];
        footDrop = (float) (y - sim.lowest());
        System.arraycopy(sim.q, 0, q, 0, q.length);
    }

    /** {@code this = a + (b - a) t}, rotations nlerped along the short arc. */
    public void lerp(RagdollPose a, RagdollPose b, double t) {
        x = a.x + (b.x - a.x) * t;
        y = a.y + (b.y - a.y) * t;
        z = a.z + (b.z - a.z) * t;
        footDrop = a.footDrop + (b.footDrop - a.footDrop) * (float) t;
        for (int i = 0; i < Skeleton.BODIES; i++) {
            int r = i * 4;
            double dot = a.q[r] * b.q[r] + a.q[r + 1] * b.q[r + 1] + a.q[r + 2] * b.q[r + 2] + a.q[r + 3] * b.q[r + 3];
            double sign = dot < 0 ? -1 : 1;
            double qx = a.q[r] + (b.q[r] * sign - a.q[r]) * t;
            double qy = a.q[r + 1] + (b.q[r + 1] * sign - a.q[r + 1]) * t;
            double qz = a.q[r + 2] + (b.q[r + 2] * sign - a.q[r + 2]) * t;
            double qw = a.q[r + 3] + (b.q[r + 3] * sign - a.q[r + 3]) * t;
            double n = 1.0 / Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            q[r] = qx * n;
            q[r + 1] = qy * n;
            q[r + 2] = qz * n;
            q[r + 3] = qw * n;
        }
    }

    public void set(RagdollPose o) {
        x = o.x;
        y = o.y;
        z = o.z;
        footDrop = o.footDrop;
        System.arraycopy(o.q, 0, q, 0, q.length);
    }

    /**
     * {@code out[PoseClip bone][channels]} for an entity at feet {@code (ex, ey, ez)}, body yaw {@code yawDeg}. Model ->
     * world: {@code w = e + Y S (p - (0, Y_SHIFT, 0))}, {@code Y = Ry(-yaw) Rx(pi)} (its own inverse).
     */
    public void channels(Skeleton sk, double ex, double ey, double ez, double yawDeg, double[][] out) {
        double half = Math.toRadians(yawDeg) * 0.5;
        double yx = Math.cos(half), yz = Math.sin(half);
        double c = Math.cos(2 * half), s = Math.sin(2 * half);

        double[] qm = new double[q.length];
        for (int i = 0; i < Skeleton.BODIES; i++) {
            int r = i * 4;
            // qY q, qY = (yx, 0, yz, 0)
            double bx = q[r], by = q[r + 1], bz = q[r + 2], bw = q[r + 3];
            qm[r] = yx * bw - yz * by;
            qm[r + 1] = yz * bx - yx * bz;
            qm[r + 2] = yx * by + yz * bw;
            qm[r + 3] = -yx * bx - yz * bz;
        }
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double mx = (c * dx + s * dz) / Skeleton.S;
        double my = -dy / Skeleton.S + Skeleton.Y_SHIFT_PX;
        double mz = (s * dx - c * dz) / Skeleton.S;

        // Torso part: rigid with the torso body; pivot from the centre.
        double[] v = new double[3];
        rotate(qm, Skeleton.TORSO, sk.torsoCentre, v);
        double px = mx - v[0], py = my - v[1], pz = mz - v[2];
        double[] tq = {qm[Skeleton.TORSO * 4], qm[Skeleton.TORSO * 4 + 1], qm[Skeleton.TORSO * 4 + 2], qm[Skeleton.TORSO * 4 + 3]};
        set(out[PoseClip.TORSO], px, py, pz, tq);
        double[] cq = bend(tq, qm, Skeleton.CHEST, out[PoseClip.TORSO]);
        // Upper-body conjugate: C = qT B qT^-1; channels of head + arms = C^-1 (final), pivots rigid with the torso.
        double[] conjInv = normalize(mul(tq, conj(cq)));

        bone(sk.dHead, tq, px, py, pz, conjInv, qm, Skeleton.HEAD, out[PoseClip.HEAD]);
        bone(sk.dRightArm, tq, px, py, pz, conjInv, qm, Skeleton.RIGHT_ARM, out[PoseClip.RIGHT_ARM]);
        bone(sk.dLeftArm, tq, px, py, pz, conjInv, qm, Skeleton.LEFT_ARM, out[PoseClip.LEFT_ARM]);
        bone(sk.dRightLeg, tq, px, py, pz, null, qm, Skeleton.RIGHT_LEG, out[PoseClip.RIGHT_LEG]);
        bone(sk.dLeftLeg, tq, px, py, pz, null, qm, Skeleton.LEFT_LEG, out[PoseClip.LEFT_LEG]);
        double[] up = new double[4];
        System.arraycopy(qm, Skeleton.RIGHT_ARM * 4, up, 0, 4);
        bend(up, qm, Skeleton.RIGHT_FOREARM, out[PoseClip.RIGHT_ARM]);
        System.arraycopy(qm, Skeleton.LEFT_ARM * 4, up, 0, 4);
        bend(up, qm, Skeleton.LEFT_FOREARM, out[PoseClip.LEFT_ARM]);
        System.arraycopy(qm, Skeleton.RIGHT_LEG * 4, up, 0, 4);
        bend(up, qm, Skeleton.RIGHT_SHIN, out[PoseClip.RIGHT_LEG]);
        System.arraycopy(qm, Skeleton.LEFT_LEG * 4, up, 0, 4);
        bend(up, qm, Skeleton.LEFT_SHIN, out[PoseClip.LEFT_LEG]);
    }

    /** World centre of {@code HEAD} or {@code CHEST} by FK. */
    public void bodyCentre(Skeleton sk, int i, double[] out) {
        if (i != Skeleton.HEAD && i != Skeleton.CHEST) {
            throw new IllegalArgumentException("head or chest");
        }
        double hy = sk.half[Skeleton.TORSO][1];
        double[] v = new double[3];
        worldRotate(Skeleton.TORSO, 0, -hy, 0, v);
        double wx = x + v[0], wy = y + v[1], wz = z + v[2];
        worldRotate(Skeleton.CHEST, 0, -hy, 0, v);
        double cx = wx + v[0], cy = wy + v[1], cz = wz + v[2];
        if (i == Skeleton.CHEST) {
            out[0] = cx;
            out[1] = cy;
            out[2] = cz;
            return;
        }
        worldRotate(Skeleton.CHEST, 0, -hy, 0, v);
        double nx = cx + v[0], ny = cy + v[1], nz = cz + v[2];
        worldRotate(Skeleton.HEAD, 0, -sk.half[Skeleton.HEAD][1], 0, v);
        out[0] = nx + v[0];
        out[1] = ny + v[1];
        out[2] = nz + v[2];
    }

    private void worldRotate(int i, double vx, double vy, double vz, double[] out) {
        rotate(q, i, new double[]{vx, vy, vz}, out);
    }

    private static void bone(double[] offset, double[] tq, double px, double py, double pz, double[] conjInv, double[] qm,
                             int body, double[] out) {
        double[] v = new double[3];
        rotate(tq, 0, offset, v);
        double[] own = {qm[body * 4], qm[body * 4 + 1], qm[body * 4 + 2], qm[body * 4 + 3]};
        set(out, px + v[0], py + v[1], pz + v[2], conjInv == null ? own : normalize(mul(conjInv, own)));
    }

    /**
     * Relative rotation parent -> {@code child} with its twist about y dropped -> {@code out} bend channels.
     * @return the projected relative rotation
     */
    private static double[] bend(double[] parent, double[] qm, int child, double[] out) {
        double[] rel = mul(conj(parent), new double[]{qm[child * 4], qm[child * 4 + 1], qm[child * 4 + 2], qm[child * 4 + 3]});
        if (rel[3] < 0) {
            rel[0] = -rel[0];
            rel[1] = -rel[1];
            rel[2] = -rel[2];
            rel[3] = -rel[3];
        }
        double tn = Math.sqrt(rel[1] * rel[1] + rel[3] * rel[3]);
        double ty = tn > 1.0e-9 ? rel[1] / tn : 0, tw = tn > 1.0e-9 ? rel[3] / tn : 1;
        double sx = rel[0] * tw + rel[2] * ty;
        double sz = rel[2] * tw - rel[0] * ty;
        double sw = rel[3] * tw + rel[1] * ty;
        double l = Math.sqrt(sx * sx + sz * sz);
        double angle = 2 * Math.atan2(l, sw);
        out[PoseClip.BEND_AXIS] = l > 1.0e-9 ? Math.atan2(-sz, sx) : 0;
        out[PoseClip.BEND] = l > 1.0e-9 ? angle : 0;
        double n = 1.0 / Math.sqrt(l * l + sw * sw);
        double[] projected = {sx * n, 0, sz * n, sw * n};
        return mul(parent, projected);
    }

    private static void set(double[] out, double px, double py, double pz, double[] r) {
        out[0] = px;
        out[1] = py;
        out[2] = pz;
        // Rz Ry Rx
        double a = r[0], b = r[1], c = r[2], d = r[3];
        double m20 = 2 * (a * c - b * d);
        double m21 = 2 * (b * c + a * d);
        double m22 = 1 - 2 * (a * a + b * b);
        double m10 = 2 * (a * b + c * d);
        double m00 = 1 - 2 * (b * b + c * c);
        double sinY = Math.max(-1, Math.min(1, -m20));
        out[4] = Math.asin(sinY);
        if (Math.abs(sinY) < 0.999999) {
            out[3] = Math.atan2(m21, m22);
            out[5] = Math.atan2(m10, m00);
        } else {
            out[3] = Math.atan2(-2 * (b * c - a * d), 1 - 2 * (a * a + c * c));
            out[5] = 0;
        }
        out[PoseClip.BEND_AXIS] = 0;
        out[PoseClip.BEND] = 0;
    }

    private static double[] conj(double[] a) {
        return new double[]{-a[0], -a[1], -a[2], a[3]};
    }

    private static double[] normalize(double[] a) {
        double n = 1.0 / Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2] + a[3] * a[3]);
        a[0] *= n;
        a[1] *= n;
        a[2] *= n;
        a[3] *= n;
        return a;
    }

    private static double[] mul(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static void rotate(double[] q, int i, double[] v, double[] out) {
        double a = q[i * 4], b = q[i * 4 + 1], c = q[i * 4 + 2], d = q[i * 4 + 3];
        double ux = 2 * (b * v[2] - c * v[1]), uy = 2 * (c * v[0] - a * v[2]), uz = 2 * (a * v[1] - b * v[0]);
        out[0] = v[0] + d * ux + b * uz - c * uy;
        out[1] = v[1] + d * uy + c * ux - a * uz;
        out[2] = v[2] + d * uz + a * uy - b * ux;
    }
}
