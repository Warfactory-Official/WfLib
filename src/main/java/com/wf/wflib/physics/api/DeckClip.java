package com.wf.wflib.physics.api;

/**
 * Box arithmetic behind deck walking, primitives only.
 * Follows vanilla's axis collision, including its rule that overlapping boxes do not block.
 */
public final class DeckClip {

    /** Matches vanilla's shape collision gap. */
    public static final double EPSILON = 1.0E-7;

    /**
     * How far inside a deck box a rider may be and still count as resting on it. Compensates for
     * frame coordinate noise from float quaternion conversions on large hulls. Downward movement
     * inside the skin clips to zero without lifting.
     */
    public static final double SKIN = 0.005;

    /** Below this on an axis, not worth a pass over the boxes. */
    private static final double MIN_MOVE = 1.0E-9;

    private DeckClip() {}

    /**
     * Compacts into dest the deck boxes a moving box could touch: swept box grown upward by lift,
     * accounting for grounded movement on tilted hulls. Six floats written per box, their
     * {@link DeckSnapshot#FRAME} floats into destFrames.
     */
    public static int narrow(float[] boxes, float[] frames, int count,
                             double cx, double cy, double cz,
                             double hx, double hy, double hz,
                             double mx, double my, double mz,
                             double groundedX, double groundedZ,
                             double lift, float[] dest, float[] destFrames) {
        // Double, to match clip precision; SKIN accounts for clip reach.
        double reachMinX = Math.min(Math.min(mx, groundedX), 0);
        double reachMaxX = Math.max(Math.max(mx, groundedX), 0);
        double reachMinZ = Math.min(Math.min(mz, groundedZ), 0);
        double reachMaxZ = Math.max(Math.max(mz, groundedZ), 0);
        double minX = cx - hx + reachMinX - SKIN;
        double maxX = cx + hx + reachMaxX + SKIN;
        double minY = cy - hy + Math.min(my, 0) - SKIN;
        double maxY = cy + hy + Math.max(my, 0) + lift + SKIN;
        double minZ = cz - hz + reachMinZ - SKIN;
        double maxZ = cz + hz + reachMaxZ + SKIN;
        int found = 0;
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            if (maxX > boxes[o] && minX < boxes[o + 3]
                    && maxY > boxes[o + 1] && minY < boxes[o + 4]
                    && maxZ > boxes[o + 2] && minZ < boxes[o + 5]) {
                System.arraycopy(boxes, o, dest, found * 6, 6);
                System.arraycopy(frames, i * DeckSnapshot.FRAME, destFrames, found * DeckSnapshot.FRAME, DeckSnapshot.FRAME);
                found++;
            }
        }
        return found;
    }

    /**
     * Clips movement against the deck: vertical first, then horizontal; step-up via lift and retry.
     * On downward block, horizontal falls back to grounded (gravity-excluded) intent.
     * @param wantX full local movement; {@code groundedX}/{@code groundedZ} are intent excluding gravity
     * @param out allowed movement as {x, y, z}
     * @return true when downward blocked
     */
    public static boolean sweep(float[] boxes, float[] frames, int count,
                                double cx, double cy, double cz,
                                double hx, double hy, double hz,
                                double wantX, double wantY, double wantZ,
                                double groundedX, double groundedZ,
                                double maxUpStep, boolean onGround,
                                double[] out) {
        out[0] = wantX;
        out[1] = wantY;
        out[2] = wantZ;
        if (count == 0 || !anyBoxNear(boxes, frames, count, cx, cy, cz, hx, hy, hz, wantX, wantY, wantZ)) {
            return false;
        }

        double gotY = clipY(boxes, frames, count, cx, cy, cz, hx, hy, hz, wantY);
        boolean blockedDown = wantY < 0 && gotY > wantY;
        if (blockedDown) {
            wantX = groundedX;
            wantZ = groundedZ;
        }
        double runX = cx;
        double runY = cy + gotY;
        double runZ = cz;
        double gotX;
        double gotZ;
        if (Math.abs(wantX) >= Math.abs(wantZ)) {
            gotX = clipX(boxes, frames, count, runX, runY, runZ, hx, hy, hz, wantX);
            runX += gotX;
            gotZ = clipZ(boxes, frames, count, runX, runY, runZ, hx, hy, hz, wantZ);
        } else {
            gotZ = clipZ(boxes, frames, count, runX, runY, runZ, hx, hy, hz, wantZ);
            runZ += gotZ;
            gotX = clipX(boxes, frames, count, runX, runY, runZ, hx, hy, hz, wantX);
        }

        boolean blockedSide = gotX != wantX || gotZ != wantZ;

        if (blockedSide && maxUpStep > 0 && (blockedDown || onGround)) {
            double baseY = cy + gotY;
            double lift = clipY(boxes, frames, count, cx, baseY, cz, hx, hy, hz, maxUpStep);
            if (lift > EPSILON) {
                double stepX;
                double stepZ;
                double sx = cx;
                double sy = baseY + lift;
                double sz = cz;
                if (Math.abs(wantX) >= Math.abs(wantZ)) {
                    stepX = clipX(boxes, frames, count, sx, sy, sz, hx, hy, hz, wantX);
                    sx += stepX;
                    stepZ = clipZ(boxes, frames, count, sx, sy, sz, hx, hy, hz, wantZ);
                    sz += stepZ;
                } else {
                    stepZ = clipZ(boxes, frames, count, sx, sy, sz, hx, hy, hz, wantZ);
                    sz += stepZ;
                    stepX = clipX(boxes, frames, count, sx, sy, sz, hx, hy, hz, wantX);
                    sx += stepX;
                }
                if (stepX * stepX + stepZ * stepZ > gotX * gotX + gotZ * gotZ) {
                    // Farther than the flat path: step-up wins, drop back onto it.
                    double drop = clipY(boxes, frames, count, sx, sy, sz, hx, hy, hz, -lift);
                    gotX = stepX;
                    gotZ = stepZ;
                    gotY += lift + drop;
                    blockedDown = blockedDown || drop > -lift;
                }
            }
        }

        out[0] = gotX;
        out[1] = gotY;
        out[2] = gotZ;
        return blockedDown;
    }

    /**
     * Pushes a box out of any deck geometry it is inside, along the shallowest overlap axis;
     * up wins ties between vertical and horizontal.
     * @param mayDescend false: downward push replaced by the shallower horizontal one (terrain below)
     * @param out receives the correction as {x, y, z}
     * @return true when a correction was needed
     */
    public static boolean depenetrate(float[] boxes, float[] frames, int count,
                                      double cx, double cy, double cz,
                                      double hx, double hy, double hz,
                                      boolean mayDescend, int maxPasses, double[] out) {
        double[] scratchPush = PUSH.get();
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        for (int pass = 0; pass < maxPasses; pass++) {
            int worst = -1;
            double worstDepth = 0;
            double pushX = 0;
            double pushY = 0;
            double pushZ = 0;
            for (int i = 0; i < count; i++) {
                int o = i * 6;
                double overlapX = Math.min(cx + hx, boxes[o + 3]) - Math.max(cx - hx, boxes[o]);
                if (overlapX <= 0) continue;
                double overlapY = Math.min(cy + hy, boxes[o + 4]) - Math.max(cy - hy, boxes[o + 1]);
                if (overlapY <= 0) continue;
                double overlapZ = Math.min(cz + hz, boxes[o + 5]) - Math.max(cz - hz, boxes[o + 2]);
                if (overlapZ <= 0) continue;
                if (oriented(frames, i)) {
                    double depth = pushOriented(frames, i, cx, cy, cz, hx, hy, hz, mayDescend, scratchPush);
                    if (depth <= SKIN || depth <= worstDepth) continue;
                    worst = i;
                    worstDepth = depth;
                    pushX = scratchPush[0];
                    pushY = scratchPush[1];
                    pushZ = scratchPush[2];
                    continue;
                }
                // Resolve deepest overlap first; shallower ones often vanish after.
                double depth = Math.min(overlapY, Math.min(overlapX, overlapZ));
                // Don't correct within SKIN; clip tolerates riders there.
                if (depth <= SKIN || depth <= worstDepth) continue;
                worst = i;
                worstDepth = depth;
                boolean up = cy >= (boxes[o + 1] + boxes[o + 4]) * 0.5;
                if (overlapY <= overlapX && overlapY <= overlapZ && (up || mayDescend)) {
                    // Up wins ties; push vertical first.
                    pushX = 0;
                    pushY = up ? overlapY + EPSILON : -(overlapY + EPSILON);
                    pushZ = 0;
                } else if (overlapX <= overlapZ) {
                    boolean plus = cx >= (boxes[o] + boxes[o + 3]) * 0.5;
                    pushX = plus ? overlapX + EPSILON : -(overlapX + EPSILON);
                    pushY = 0;
                    pushZ = 0;
                } else {
                    boolean plus = cz >= (boxes[o + 2] + boxes[o + 5]) * 0.5;
                    pushX = 0;
                    pushY = 0;
                    pushZ = plus ? overlapZ + EPSILON : -(overlapZ + EPSILON);
                }
            }
            if (worst < 0) {
                break;
            }
            cx += pushX;
            cy += pushY;
            cz += pushZ;
            out[0] += pushX;
            out[1] += pushY;
            out[2] += pushZ;
        }
        return out[0] != 0 || out[1] != 0 || out[2] != 0;
    }

    /** True when any deck box lies within the box's swept extent. */
    public static boolean anyBoxNear(float[] boxes, float[] frames, int count,
                                     double cx, double cy, double cz,
                                     double hx, double hy, double hz,
                                     double mx, double my, double mz) {
        double minX = cx - hx + Math.min(mx, 0);
        double maxX = cx + hx + Math.max(mx, 0);
        double minY = cy - hy + Math.min(my, 0);
        double maxY = cy + hy + Math.max(my, 0);
        double minZ = cz - hz + Math.min(mz, 0);
        double maxZ = cz + hz + Math.max(mz, 0);
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            if (maxX > boxes[o] && minX < boxes[o + 3]
                    && maxY > boxes[o + 1] && minY < boxes[o + 4]
                    && maxZ > boxes[o + 2] && minZ < boxes[o + 5]
                    && (!oriented(frames, i) || overlapsOriented(frames, i, (minX + maxX) * 0.5, (minY + maxY) * 0.5,
                    (minZ + maxZ) * 0.5, (maxX - minX) * 0.5, (maxY - minY) * 0.5, (maxZ - minZ) * 0.5))) {
                return true;
            }
        }
        return false;
    }

    public static double clipY(float[] boxes, float[] frames, int count, double cx, double cy, double cz,
                               double hx, double hy, double hz, double move) {
        if (Math.abs(move) < MIN_MOVE) {
            return move;
        }
        double minX = cx - hx, maxX = cx + hx;
        double minZ = cz - hz, maxZ = cz + hz;
        double minY = cy - hy, maxY = cy + hy;
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            if (maxX <= boxes[o] || minX >= boxes[o + 3] || maxZ <= boxes[o + 2] || minZ >= boxes[o + 5]) {
                continue;
            }
            if (oriented(frames, i)) {
                move = clipOriented(frames, i, cx, cy, cz, hx, hy, hz, 0, move, 0);
                continue;
            }
            if (move > 0 && maxY <= boxes[o + 1] + SKIN) {
                double gap = boxes[o + 1] - maxY - EPSILON;
                if (gap < move) move = Math.max(gap, 0);
            } else if (move < 0 && minY >= boxes[o + 4] - SKIN) {
                double gap = boxes[o + 4] - minY + EPSILON;
                if (gap > move) move = Math.min(gap, 0);
            }
        }
        return move;
    }

    public static double clipX(float[] boxes, float[] frames, int count, double cx, double cy, double cz,
                               double hx, double hy, double hz, double move) {
        if (Math.abs(move) < MIN_MOVE) {
            return move;
        }
        double minY = cy - hy, maxY = cy + hy;
        double minZ = cz - hz, maxZ = cz + hz;
        double minX = cx - hx, maxX = cx + hx;
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            if (maxY <= boxes[o + 1] || minY >= boxes[o + 4] || maxZ <= boxes[o + 2] || minZ >= boxes[o + 5]) {
                continue;
            }
            if (oriented(frames, i)) {
                move = clipOriented(frames, i, cx, cy, cz, hx, hy, hz, move, 0, 0);
                continue;
            }
            if (move > 0 && maxX <= boxes[o] + SKIN) {
                double gap = boxes[o] - maxX - EPSILON;
                if (gap < move) move = Math.max(gap, 0);
            } else if (move < 0 && minX >= boxes[o + 3] - SKIN) {
                double gap = boxes[o + 3] - minX + EPSILON;
                if (gap > move) move = Math.min(gap, 0);
            }
        }
        return move;
    }

    public static double clipZ(float[] boxes, float[] frames, int count, double cx, double cy, double cz,
                               double hx, double hy, double hz, double move) {
        if (Math.abs(move) < MIN_MOVE) {
            return move;
        }
        double minX = cx - hx, maxX = cx + hx;
        double minY = cy - hy, maxY = cy + hy;
        double minZ = cz - hz, maxZ = cz + hz;
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            if (maxX <= boxes[o] || minX >= boxes[o + 3] || maxY <= boxes[o + 1] || minY >= boxes[o + 4]) {
                continue;
            }
            if (oriented(frames, i)) {
                move = clipOriented(frames, i, cx, cy, cz, hx, hy, hz, 0, 0, move);
                continue;
            }
            if (move > 0 && maxZ <= boxes[o + 2] + SKIN) {
                double gap = boxes[o + 2] - maxZ - EPSILON;
                if (gap < move) move = Math.max(gap, 0);
            } else if (move < 0 && minZ >= boxes[o + 5] - SKIN) {
                double gap = boxes[o + 5] - minZ + EPSILON;
                if (gap > move) move = Math.min(gap, 0);
            }
        }
        return move;
    }

    /** |w| of a frame rotation treated as none: AABB exact. */
    private static final float IDENTITY_W = 1 - 1e-6f;
    /** SAT axis shorter than this (parallel edge pair) carries no separation. */
    private static final double AXIS_EPS = 1.0E-6;
    /** Axis speed below this: static on it. */
    private static final double SPEED_EPS = 1.0E-12;
    private static final int AXES = 15;

    private static final ThreadLocal<double[]> PUSH = ThreadLocal.withInitial(() -> new double[3]);
    private static final ThreadLocal<double[]> AXIS = ThreadLocal.withInitial(() -> new double[3 + 9]);

    private static boolean oriented(float[] frames, int i) {
        return Math.abs(frames[i * DeckSnapshot.FRAME + 9]) < IDENTITY_W;
    }

    /** Frame i's box axes u0,u1,u2 into a[3..11]. */
    private static double[] pieceAxes(float[] frames, int i) {
        int f = i * DeckSnapshot.FRAME;
        double x = frames[f + 6], y = frames[f + 7], z = frames[f + 8], w = frames[f + 9];
        double[] a = AXIS.get();
        a[3] = 1 - 2 * (y * y + z * z); a[4] = 2 * (x * y + w * z); a[5] = 2 * (x * z - w * y);
        a[6] = 2 * (x * y - w * z); a[7] = 1 - 2 * (x * x + z * z); a[8] = 2 * (y * z + w * x);
        a[9] = 2 * (x * z + w * y); a[10] = 2 * (y * z - w * x); a[11] = 1 - 2 * (x * x + y * y);
        return a;
    }

    /** SAT axis k (0..14: world x/y/z, piece u0..u2, world_i x u_j), unnormalised, into a[0..2]; false = degenerate. */
    private static boolean axis(double[] a, int k) {
        if (k < 3) {
            a[0] = k == 0 ? 1 : 0;
            a[1] = k == 1 ? 1 : 0;
            a[2] = k == 2 ? 1 : 0;
            return true;
        }
        if (k < 6) {
            int u = 3 + (k - 3) * 3;
            a[0] = a[u];
            a[1] = a[u + 1];
            a[2] = a[u + 2];
            return true;
        }
        int world = (k - 6) / 3;
        int u = 3 + ((k - 6) % 3) * 3;
        double ux = a[u], uy = a[u + 1], uz = a[u + 2];
        switch (world) {
            case 0 -> { a[0] = 0; a[1] = -uz; a[2] = uy; }
            case 1 -> { a[0] = uz; a[1] = 0; a[2] = -ux; }
            default -> { a[0] = -uy; a[1] = ux; a[2] = 0; }
        }
        double length = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (length < AXIS_EPS) {
            return false;
        }
        a[0] /= length;
        a[1] /= length;
        a[2] /= length;
        return true;
    }

    /** Sum of both boxes' projected half widths on axis a[0..2]. */
    private static double reach(float[] frames, int f, double[] a, double hx, double hy, double hz) {
        double nx = a[0], ny = a[1], nz = a[2];
        return Math.abs(nx) * hx + Math.abs(ny) * hy + Math.abs(nz) * hz
                + frames[f + 3] * Math.abs(nx * a[3] + ny * a[4] + nz * a[5])
                + frames[f + 4] * Math.abs(nx * a[6] + ny * a[7] + nz * a[8])
                + frames[f + 5] * Math.abs(nx * a[9] + ny * a[10] + nz * a[11]);
    }

    /** Static SAT: box (carrier-frame axis-aligned) overlaps frame i's oriented box. */
    private static boolean overlapsOriented(float[] frames, int i,
                                            double cx, double cy, double cz,
                                            double hx, double hy, double hz) {
        int f = i * DeckSnapshot.FRAME;
        double[] a = pieceAxes(frames, i);
        double dx = cx - frames[f], dy = cy - frames[f + 1], dz = cz - frames[f + 2];
        for (int k = 0; k < AXES; k++) {
            if (axis(a, k) && Math.abs(dx * a[0] + dy * a[1] + dz * a[2]) >= reach(frames, f, a, hx, hy, hz)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Axis-aligned clip rules (contact => stop EPSILON short; start inside by <= SKIN => stop; deeper => pass)
     * against frame i's oriented box, swept SAT. Exactly one of mx/my/mz non-zero.
     * @return allowed move on that axis
     */
    private static double clipOriented(float[] frames, int i,
                                       double cx, double cy, double cz,
                                       double hx, double hy, double hz,
                                       double mx, double my, double mz) {
        double move = mx + my + mz;
        int f = i * DeckSnapshot.FRAME;
        double[] a = pieceAxes(frames, i);
        double dx = cx - frames[f], dy = cy - frames[f + 1], dz = cz - frames[f + 2];
        double enter = Double.NEGATIVE_INFINITY;
        double enterSpeed = 0;
        double exit = Double.POSITIVE_INFINITY;
        for (int k = 0; k < AXES; k++) {
            if (!axis(a, k)) {
                continue;
            }
            double s = dx * a[0] + dy * a[1] + dz * a[2];
            double r = reach(frames, f, a, hx, hy, hz);
            // Per unit of move fraction.
            double v = mx * a[0] + my * a[1] + mz * a[2];
            if (Math.abs(v) < SPEED_EPS) {
                if (Math.abs(s) >= r) {
                    return move;
                }
                continue;
            }
            double first = (-r - s) / v;
            double last = (r - s) / v;
            if (first > last) {
                double swap = first;
                first = last;
                last = swap;
            }
            if (first > enter) {
                enter = first;
                enterSpeed = Math.abs(v);
            }
            if (last < exit) {
                exit = last;
            }
            if (enter >= exit || enter >= 1 || exit <= 0) {
                return move;
            }
        }
        if (enter > 0) {
            double allowed = Math.max(enter * Math.abs(move) - EPSILON, 0);
            return Math.copySign(allowed, move);
        }
        // Inside at start: within SKIN of the entry face => resting there.
        return -enter * enterSpeed <= SKIN ? 0 : move;
    }

    /**
     * Shallowest SAT push of the box out of frame i's oriented box; up wins ties.
     * @param mayDescend false => downward pushes skipped
     * @return depth, 0 when none possible; push into out
     */
    private static double pushOriented(float[] frames, int i,
                                       double cx, double cy, double cz,
                                       double hx, double hy, double hz,
                                       boolean mayDescend, double[] out) {
        int f = i * DeckSnapshot.FRAME;
        double[] a = pieceAxes(frames, i);
        double dx = cx - frames[f], dy = cy - frames[f + 1], dz = cz - frames[f + 2];
        double best = Double.POSITIVE_INFINITY;
        double bestUp = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < AXES; k++) {
            if (!axis(a, k)) {
                continue;
            }
            double s = dx * a[0] + dy * a[1] + dz * a[2];
            double depth = reach(frames, f, a, hx, hy, hz) - Math.abs(s);
            if (depth <= 0) {
                return 0;
            }
            double sign = s >= 0 ? 1 : -1;
            double up = sign * a[1];
            if (up < -AXIS_EPS && !mayDescend) {
                continue;
            }
            if (depth < best - EPSILON || depth <= best + EPSILON && up > bestUp) {
                best = depth;
                bestUp = up;
                out[0] = sign * a[0] * (depth + EPSILON);
                out[1] = sign * a[1] * (depth + EPSILON);
                out[2] = sign * a[2] * (depth + EPSILON);
            }
        }
        return best == Double.POSITIVE_INFINITY ? 0 : best;
    }

}
