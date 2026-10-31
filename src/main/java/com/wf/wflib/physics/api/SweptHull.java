package com.wf.wflib.physics.api;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.wf.wflib.geom.api.OBB;

import java.util.Arrays;

/**
 * Stops a hull from stepping over geometry it should have hit. The hull is trimmed to its climb
 * skirt and its step clipped to the exact fraction at which it first meets a box, so it rests
 * against obstacles instead of teleporting past them; an embedded hull can still escape what it
 * started inside while being stopped by new geometry.
 */
public final class SweptHull {

    /**
     * Depth of overlap treated as resolved rather than as a collision, in blocks;
     * only used to inset the cast hull sideways.
     */
    public static final float SLOP = 0.005f;

    /**
     * Fraction of the step held back on impact so the hull stops short of touching rather than
     * landing exactly on the boundary. Small because the impact fraction is now solved rather
     * than searched for, so there is no interval left over to cover.
     */
    private static final double SKIN = 0.001;

    /** Closing speed along an axis below which the pair is treated as neither meeting nor parting. */
    private static final float MOTION_EPS = 1.0e-7f;

    /** Why timeOfImpact returned what it did. */
    public enum Outcome {

        /** No boxes near the swept path; the test was skipped. */
        NO_BOXES,
        /** Movement handed to the cast was zero so nothing was tested, distinct from NO_BOXES. */
        NO_MOTION,
        /** The whole step is free. */
        CLEAR,
        /**
         * The hull overlapped something before the step started; motion within or out of those boxes
         * is free unless one ends deeper than the deepest started, entering new geometry clips.
         */
        ALREADY_INSIDE,
        /** The step was shortened to stop against geometry. */
        CLIPPED

    }

    /**
     * Scratch space for asking a sweep what it did; allocate one per traced vehicle and reuse.
     */
    public static final class Probe {

        public Outcome outcome = Outcome.NO_BOXES;

        /** Number of boxes offered to the sweep. */
        public int boxes;

        /** Box that stopped the step, or one the hull started inside. */
        @Nullable
        public AABB blocker;

        public double toi = 1.0;

        /** Deepest overlap found by the last measurePenetration call. */
        public double penetration;

        /** Direction and depth of that overlap, pointing out of the hull. */
        public final Vector3f penetrationAxis = new Vector3f();

        /** Box the hull is deepest inside. */
        @Nullable
        public AABB penetrator;

        public void reset() {
            outcome = Outcome.NO_BOXES;
            boxes = 0;
            blocker = null;
            toi = 1.0;
            penetration = 0;
            penetrationAxis.zero();
            penetrator = null;
        }

    }

    /**
     * Narrows the world's boxes to the handful near one substep; owned by the caller, not thread-safe.
     */
    public static final class Broadphase {

        private final BoxBuffer nearby = new BoxBuffer();
        private final Matrix3f basisScratch = new Matrix3f();
        private SectionSource source;

        /**
         * Points this broadphase at a section source; sections not held read as empty.
         */
        public void init(SectionSource source) {
            this.source = source;
        }

        /**
         * Boxes that could matter to a cast along the movement; returned buffer is scratch.
         */
        public BoxBuffer near(OBB hull, double moveX, double moveY, double moveZ) {
            return near(hull, null, hull.extents(), moveX, moveY, moveZ);
        }

        /**
         * As above, for a bound that is not the hull's own box.
         *
         * <p>A compound vehicle reaches further than the piece its solve is built around: a gun
         * barrel out past the nose is still geometry that has to meet the world. The bound covering
         * every piece is not centred on the hull, so it is given as a centre and half extents in the
         * hull's own frame rather than as extents alone.
         *
         * @param localCentre the bound's centre in the hull's frame, or null for the hull's own
         */
        public BoxBuffer near(OBB hull, @Nullable Vector3f localCentre, Vector3f extents,
                              double moveX, double moveY, double moveZ) {
            // Project extents onto world axes through rotation; not the circumscribed sphere, which
            // defeats the narrowing for the large hulls it exists for.
            Matrix3f m = hull.rotation().get(basisScratch);
            double reachX = Math.abs(m.m00()) * extents.x + Math.abs(m.m10()) * extents.y
                    + Math.abs(m.m20()) * extents.z + MARGIN;
            double reachY = Math.abs(m.m01()) * extents.x + Math.abs(m.m11()) * extents.y
                    + Math.abs(m.m21()) * extents.z + MARGIN;
            double reachZ = Math.abs(m.m02()) * extents.x + Math.abs(m.m12()) * extents.y
                    + Math.abs(m.m22()) * extents.z + MARGIN;
            double offX = 0, offY = 0, offZ = 0;
            if (localCentre != null) {
                offX = m.m00() * localCentre.x + m.m10() * localCentre.y + m.m20() * localCentre.z;
                offY = m.m01() * localCentre.x + m.m11() * localCentre.y + m.m21() * localCentre.z;
                offZ = m.m02() * localCentre.x + m.m12() * localCentre.y + m.m22() * localCentre.z;
            }
            double minX = hull.centerX() + offX + Math.min(0, moveX) - reachX;
            double minY = hull.centerY() + offY + Math.min(0, moveY) - reachY;
            double minZ = hull.centerZ() + offZ + Math.min(0, moveZ) - reachZ;
            double maxX = hull.centerX() + offX + Math.max(0, moveX) + reachX;
            double maxY = hull.centerY() + offY + Math.max(0, moveY) + reachY;
            double maxZ = hull.centerZ() + offZ + Math.max(0, moveZ) + reachZ;
            nearby.clear();
            if (source != null) {
                ChunkCollisionCache.collectBoxes(source, minX, minY, minZ, maxX, maxY, maxZ, nearby);
            }
            return nearby;
        }

        /** Slack on the query bound so rounding never drops a box the cast would hit. */
        private static final double MARGIN = 0.5;

    }

    /**
     * Thinnest the trimmed hull is allowed to get; the shortfall above the skirt plane is made up above it.
     */
    private static final float MIN_HALF_HEIGHT = 0.05f;

    private SweptHull() {}

    /**
     * Fills dest with source minus its climb skirt; this trimmed hull is swept against the world,
     * with the underside raised to the skirt plane.
     *
     * @param skirt local Y of the skirt plane.
     * @param dest scratch hull, overwritten; keep one per vehicle.
     */
    public static OBB climbHull(OBB source, double skirt, OBB dest) {
        float extentY = source.extents().y;
        // Skirt is measured in the hull's frame from its centre, so band height is how far it
        // sits above the underside.
        float band = (float) skirt + extentY;
        if (band <= 0) {
            dest.setCenter(source);
            dest.extents().set(source.extents());
            dest.rotation().set(source.rotation());
            return dest;
        }
        // Underside at skirt; if hull is shorter than ride band, make up shortfall above to keep
        // nothing below the skirt able to block. Clamp top to keep cast box inside the real hull.
        float bottom = climbBottom(extentY, skirt);
        float top = Math.max(extentY, bottom + 2 * MIN_HALF_HEIGHT);
        // Inset sideways by contact slop so surfaces already resting against the hull are invisible.
        float inset = Math.min(SLOP,
                Math.min(source.extents().x, source.extents().z) * 0.5f);
        // Along the hull's own up axis; a rolled hull's skirt tilts with it.
        dest.rotation().set(source.rotation());
        dest.extents().set(source.extents().x - inset, (top - bottom) * 0.5f,
                source.extents().z - inset);
        // Borrow dest's own mirror as scratch for the rotated offset; setCenter overwrites it.
        Vector3f offset = dest.center().set(0, (top + bottom) * 0.5f, 0).rotate(source.rotation());
        dest.setCenter(source.centerX() + offset.x,
                source.centerY() + offset.y,
                source.centerZ() + offset.z);
        return dest;
    }

    /**
     * The real shape to cast, to go with the box {@link #climbHull} produces.
     *
     * <p>Same trim: everything below the climb skirt is cut away, so geometry the vehicle is going to
     * drive over does not read as a wall, and the result is drawn back from its own surface so a hull
     * already resting against something is not reported as blocked by it. The points come back
     * relative to the climb box's centre, because that is the centre the cast is run from.
     *
     * <p>Returns null when there is nothing left to cast, which is a hull shorter than its own ride
     * band. The caller falls back to the box, which is larger than the truth and so stops early
     * rather than passing through anything.
     *
     * @param hull   the shape in the cube's own frame, centred where the box is centred
     * @param extentY the box's half height, which is what the skirt is measured against
     */
    @Nullable
    public static ConvexHull castShape(ConvexHull hull, float extentY, double skirt) {
        if ((float) skirt + extentY <= 0) {
            return hull;
        }
        ConvexHull kept = hull.clipped(0, -1, 0, -climbBottom(extentY, skirt), SLOP);
        if (kept == null) {
            return null;
        }
        // Into the cast box's frame. climbHull centres its box between bottom and top, and the cast
        // is run from that centre, so the shape has to be measured from the same place.
        return kept.transformed(new Quaternionf(), new Vector3f(0, -climbRise(extentY, skirt), 0));
    }

    /**
     * Local Y of the trimmed hull's underside: the skirt plane, or as low as a hull shorter than its
     * own ride band can be cut without leaving nothing to cast.
     *
     * <p>Public because a compound vehicle cuts every one of its pieces against this one plane. The
     * skirt belongs to the vehicle, not to the piece: a gun barrel is not allowed its own idea of
     * what height counts as something to drive over.
     */
    public static float climbBottom(float extentY, double skirt) {
        float band = (float) skirt + extentY;
        if (band <= 0) {
            return -extentY;
        }
        return Math.min(-extentY + band, extentY - 2 * MIN_HALF_HEIGHT);
    }

    /** How far the trimmed hull's centre sits above the hull's own centre, in the hull's frame. */
    public static float climbRise(float extentY, double skirt) {
        if ((float) skirt + extentY <= 0) {
            return 0;
        }
        float bottom = climbBottom(extentY, skirt);
        float top = Math.max(extentY, bottom + 2 * MIN_HALF_HEIGHT);
        return (top + bottom) * 0.5f;
    }

    /**
     * How much of the movement the hull may take before overlapping something.
     *
     * @return a fraction in [0, 1]; 1 when the whole step is clear.
     */
    public static double timeOfImpact(OBB obb, BoxBuffer boxes, Vec3 movement) {
        return timeOfImpact(obb, boxes, movement, null);
    }

    /**
     * As timeOfImpact, recording into probe why the answer came out the way it did.
     */
    public static double timeOfImpact(OBB obb, BoxBuffer boxes, Vec3 movement,
                                      @Nullable Probe probe) {
        return timeOfImpact(obb, boxes, movement.x, movement.y, movement.z, probe);
    }

    /**
     * As above, without a Vec3; builds a throwaway frame for a single cast.
     */
    public static double timeOfImpact(OBB obb, BoxBuffer boxes,
                                      double moveX, double moveY, double moveZ,
                                      @Nullable Probe probe) {
        return timeOfImpact(obb, boxes, moveX, moveY, moveZ, probe,
                new OBB.SatFrame().set(obb.rotation()));
    }

    /**
     * As above, against a caller-prepared SatFrame; must match obb.rotation().
     */
    public static double timeOfImpact(OBB obb, BoxBuffer boxes,
                                      double moveX, double moveY, double moveZ,
                                      @Nullable Probe probe, OBB.SatFrame frame) {
        return timeOfImpact(obb, null, boxes, moveX, moveY, moveZ, probe, frame);
    }

    /**
     * As above, against the hull's real shape rather than the box around it.
     *
     * <p>Every box is still tested against the box first, because the shape is inside it: a box the
     * cheap test never meets could not have met the shape either, and a box whose cheap entry is later
     * than the earliest found so far cannot improve on it, since the shape is always met later than
     * its own bound. So the expensive test runs on the few boxes that could change the answer.
     *
     * @param shape the posed hull, or null to sweep the box as before
     */
    public static double timeOfImpact(OBB obb, @Nullable ConvexSweep shape, BoxBuffer boxes,
                                      double moveX, double moveY, double moveZ,
                                      @Nullable Probe probe, OBB.SatFrame frame) {
        if (probe != null) {
            probe.reset();
            probe.boxes = boxes.size();
        }
        if (boxes.isEmpty()) {
            return 1.0;
        }
        if (moveX * moveX + moveY * moveY + moveZ * moveZ < 1.0e-12) {
            if (probe != null) {
                probe.outcome = Outcome.NO_MOTION;
            }
            return 1.0;
        }
        // The hull's centre is never moved; the probe position is just arithmetic.
        double ox = obb.centerX();
        double oy = obb.centerY();
        double oz = obb.centerZ();
        Vector3f extents = obb.extents();

        ConvexSweep cast = shape != null && shape.ready() ? shape : null;
        double entry = sweepAll(frame, extents, cast, boxes, ox, oy, oz, moveX, moveY, moveZ, null, probe);
        if (entry > 0) {
            if (entry >= 1.0) {
                if (probe != null) {
                    probe.outcome = Outcome.CLEAR;
                }
                return 1.0;
            }
            double toi = Math.max(0.0, entry - skin(extents, moveX, moveY, moveZ));
            if (probe != null) {
                probe.outcome = Outcome.CLIPPED;
                probe.toi = toi;
            }
            return toi;
        }
        // => ALREADY_INSIDE; confirm the overlap before excusing it (allOverlaps).
        int[] ignore = allOverlaps(frame, extents, cast, boxes, ox, oy, oz);
        if (probe != null) {
            probe.outcome = Outcome.ALREADY_INSIDE;
        }
        if (ignore.length == 0) {
            // Nothing actually overlaps at the start pose, so the zero came from a graze the
            // static test does not agree with. Take it as blocked rather than looping.
            return 0.0;
        }
        if (deepens(frame, extents, boxes, ignore, ox, oy, oz, moveX, moveY, moveZ)) {
            if (probe != null) {
                probe.toi = 0;
            }
            return 0.0;
        }
        double free = sweepAll(frame, extents, cast, boxes, ox, oy, oz, moveX, moveY, moveZ, ignore, probe);
        if (free >= 1.0) {
            return 1.0;
        }
        double toi = Math.max(0.0, free - skin(extents, moveX, moveY, moveZ));
        if (probe != null) {
            probe.toi = toi;
        }
        return toi;
    }

    /**
     * Stop-short fraction. Gap left >= 2x the SAT radius inflation, or a slow large hull's gap is
     * read as overlap next step, excused, and the hull walks into the box.
     */
    private static double skin(Vector3f extents, double moveX, double moveY, double moveZ) {
        double length = Math.sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ);
        return Math.max(SKIN, 2 * OBB.SatFrame.inflation(extents) / length);
    }

    /**
     * Earliest fraction of the movement at which the hull meets any box it is not excused from.
     * Returns 1.0 when the whole step is clear, and 0 when it starts overlapping.
     *
     * @param ignore indices of boxes the hull started inside; overlaps with those do not count as a hit.
     */
    private static double sweepAll(OBB.SatFrame frame, Vector3f extents, @Nullable ConvexSweep cast,
                                   BoxBuffer boxes,
                                   double ox, double oy, double oz,
                                   double moveX, double moveY, double moveZ,
                                   int @Nullable [] ignore, @Nullable Probe probe) {
        double earliest = 1.0;
        int hit = -1;
        for (int i = 0, size = boxes.size(); i < size; i++) {
            if (ignore != null && containsIndex(ignore, i)) {
                continue;
            }
            double entry = sweepBox(frame, extents, ox, oy, oz, moveX, moveY, moveZ,
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                    boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i));
            if (entry >= earliest) {
                continue;
            }
            if (cast != null) {
                entry = cast.entry(ox, oy, oz, moveX, moveY, moveZ,
                        boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                        boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i));
            }
            if (entry < earliest) {
                earliest = entry;
                hit = i;
                if (entry <= 0) {
                    break;
                }
            }
        }
        if (probe != null && hit >= 0) {
            probe.blocker = boxes.get(hit);
        }
        return earliest;
    }

    /**
     * Exact time of impact for one box under pure translation: clips the step against the fifteen
     * separating slabs bounding the Minkowski sum of the two boxes. One pass, no search.
     *
     * @return the entry fraction in [0, 1]; 1 when this box is never met, 0 when already inside.
     */
    private static double sweepBox(OBB.SatFrame f, Vector3f extents,
                                   double ox, double oy, double oz,
                                   double moveX, double moveY, double moveZ,
                                   double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        float h0 = (float) ((maxX - minX) * 0.5);
        float h1 = (float) ((maxY - minY) * 0.5);
        float h2 = (float) ((maxZ - minZ) * 0.5);
        float t0 = (float) ((minX + maxX) * 0.5 - ox);
        float t1 = (float) ((minY + maxY) * 0.5 - oy);
        float t2 = (float) ((minZ + maxZ) * 0.5 - oz);
        float vx = (float) moveX;
        float vy = (float) moveY;
        float vz = (float) moveZ;
        float e0 = extents.x;
        float e1 = extents.y;
        float e2 = extents.z;

        float enter = 0;
        float exit = 1;
        float d, r, v, ta, tb, swap;

        // World axes.
        d = t0; r = h0 + e0 * f.a00 + e1 * f.a01 + e2 * f.a02; v = vx;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t1; r = h1 + e0 * f.a10 + e1 * f.a11 + e2 * f.a12; v = vy;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t2; r = h2 + e0 * f.a20 + e1 * f.a21 + e2 * f.a22; v = vz;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        // Hull axes.
        d = t0 * f.r00 + t1 * f.r10 + t2 * f.r20; r = e0 + h0 * f.a00 + h1 * f.a10 + h2 * f.a20;
        v = vx * f.r00 + vy * f.r10 + vz * f.r20;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t0 * f.r01 + t1 * f.r11 + t2 * f.r21; r = e1 + h0 * f.a01 + h1 * f.a11 + h2 * f.a21;
        v = vx * f.r01 + vy * f.r11 + vz * f.r21;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t0 * f.r02 + t1 * f.r12 + t2 * f.r22; r = e2 + h0 * f.a02 + h1 * f.a12 + h2 * f.a22;
        v = vx * f.r02 + vy * f.r12 + vz * f.r22;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        // Cross axes, world axis by hull axis. Unnormalised: distance, radius and closing speed
        // all scale with the axis length, so the fraction they solve for is unaffected.
        d = t2 * f.r10 - t1 * f.r20; r = h1 * f.a20 + h2 * f.a10 + e1 * f.a02 + e2 * f.a01;
        v = vz * f.r10 - vy * f.r20;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t2 * f.r11 - t1 * f.r21; r = h1 * f.a21 + h2 * f.a11 + e2 * f.a00 + e0 * f.a02;
        v = vz * f.r11 - vy * f.r21;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t2 * f.r12 - t1 * f.r22; r = h1 * f.a22 + h2 * f.a12 + e0 * f.a01 + e1 * f.a00;
        v = vz * f.r12 - vy * f.r22;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t0 * f.r20 - t2 * f.r00; r = h2 * f.a00 + h0 * f.a20 + e1 * f.a12 + e2 * f.a11;
        v = vx * f.r20 - vz * f.r00;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t0 * f.r21 - t2 * f.r01; r = h2 * f.a01 + h0 * f.a21 + e2 * f.a10 + e0 * f.a12;
        v = vx * f.r21 - vz * f.r01;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t0 * f.r22 - t2 * f.r02; r = h2 * f.a02 + h0 * f.a22 + e0 * f.a11 + e1 * f.a10;
        v = vx * f.r22 - vz * f.r02;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t1 * f.r00 - t0 * f.r10; r = h0 * f.a10 + h1 * f.a00 + e1 * f.a22 + e2 * f.a21;
        v = vy * f.r00 - vx * f.r10;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t1 * f.r01 - t0 * f.r11; r = h0 * f.a11 + h1 * f.a01 + e2 * f.a20 + e0 * f.a22;
        v = vy * f.r01 - vx * f.r11;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        d = t1 * f.r02 - t0 * f.r12; r = h0 * f.a12 + h1 * f.a02 + e0 * f.a21 + e1 * f.a20;
        v = vy * f.r02 - vx * f.r12;
        if (v > -MOTION_EPS && v < MOTION_EPS) { if (d > r || d < -r) return 1.0; }
        else { ta = (d - r) / v; tb = (d + r) / v;
            if (ta > tb) { swap = ta; ta = tb; tb = swap; }
            if (ta > enter) enter = ta; if (tb < exit) exit = tb;
            if (enter > exit) return 1.0; }

        return enter;
    }

    /**
     * Deepest overlap between the hull and any boxes, written into probe; purely a measurement.
     */
    public static void measurePenetration(OBB obb, BoxBuffer boxes, Probe probe) {
        probe.penetration = 0;
        probe.penetrationAxis.zero();
        probe.penetrator = null;
        OBB.SatFrame frame = new OBB.SatFrame().set(obb.rotation());
        for (int i = 0, size = boxes.size(); i < size; i++) {
            // Cheap rejection first; most boxes are nowhere near the hull.
            if (!OBB.intersectsBox(frame, obb.centerX(), obb.centerY(), obb.centerZ(), obb.extents(),
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                    boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i))) {
                continue;
            }
            // Only on a genuinely overlapping box is materializing one worth it.
            AABB box = boxes.get(i);
            Vector3f mtv = obb.calculateMTV(box);
            float depth = mtv.length();
            if (depth > probe.penetration) {
                probe.penetration = depth;
                probe.penetrationAxis.set(mtv);
                probe.penetrator = box;
            }
        }
    }

    /** Depth gain past the deepest excused box that counts as deeper, blocks; > float noise at 1e7 coordinates. */
    private static final float DEEPEN_EPS = 1.0e-4f;

    /** Box checked only while depth < full thickness: deeper = piece engulfed through its thin axis, depth arbitrary. */
    private static float engulfed(Vector3f extents) {
        return 2 * Math.min(extents.x, Math.min(extents.y, extents.z));
    }

    /**
     * An excused box, not engulfing, ends deeper than the deepest excused box starts. Excused boxes ignored => hull crept
     * 0.006-0.05 b/t into a wall until its centre was inside ({@code PitBench}). Per-box MTV direction FORBIDDEN:
     * bow-tip floor box under an embedded keel has x as its MTV => submarine could not sail. Every box checked
     * FORBIDDEN: submarine hydroplane (0.05 thick) engulfed in a seabed block, depth jumps as it moves. Fixed 0.25 cut
     * FORBIDDEN: hull past it crept freely, 1.1 into a wall.
     */
    private static boolean deepens(OBB.SatFrame frame, Vector3f extents, BoxBuffer boxes, int[] excused,
                                   double ox, double oy, double oz, double moveX, double moveY, double moveZ) {
        Vector3f mtv = new Vector3f();
        float deepest = 0;
        float[] start = new float[excused.length];
        for (int k = 0; k < excused.length; k++) {
            int i = excused[k];
            start[k] = OBB.mtv(frame, ox, oy, oz, extents, boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                    boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i), mtv);
            deepest = Math.max(deepest, start[k]);
        }
        float engulfed = engulfed(extents);
        for (int k = 0; k < excused.length; k++) {
            int i = excused[k];
            if (start[k] >= engulfed) {
                continue;
            }
            float depth = OBB.mtv(frame, ox + moveX, oy + moveY, oz + moveZ, extents, boxes.minX(i), boxes.minY(i),
                    boxes.minZ(i), boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i), mtv);
            if (depth > deepest + DEEPEN_EPS) {
                return true;
            }
        }
        return false;
    }

    /** New overlap a turn may make beyond what already was, blocks: grazes free, creep bounded. */
    private static final float TURN_ALLOWANCE = 0.05f;

    /**
     * A box not engulfing before ends deeper than max(deepest at before, allowance) at after.
     * Pitch/roll unchecked => sprung hull rolled 36 -> 63 deg into a wall, 2.3 deep ({@code PitBench}).
     */
    public static boolean turnDeepens(OBB before, OBB after, BoxBuffer boxes) {
        OBB.SatFrame from = new OBB.SatFrame().set(before.rotation()), to = new OBB.SatFrame().set(after.rotation());
        Vector3f mtv = new Vector3f();
        float deepest = TURN_ALLOWANCE;
        float[] start = new float[boxes.size()];
        for (int i = 0, size = boxes.size(); i < size; i++) {
            start[i] = OBB.mtv(from, before.centerX(), before.centerY(), before.centerZ(), before.extents(),
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i), boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i), mtv);
            deepest = Math.max(deepest, start[i]);
        }
        float engulfed = engulfed(before.extents());
        for (int i = 0, size = boxes.size(); i < size; i++) {
            if (start[i] < engulfed && OBB.mtv(to, after.centerX(), after.centerY(), after.centerZ(), after.extents(),
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i), boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i), mtv)
                    > deepest + DEEPEN_EPS) {
                return true;
            }
        }
        return false;
    }

    /** Whether value is in indices; indices are the handful of boxes a hull starts embedded in. */
    private static boolean containsIndex(int[] indices, int value) {
        for (int index : indices) {
            if (index == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every box the hull overlaps with its centre at the given point; array is tiny.
     */
    private static int[] allOverlaps(OBB.SatFrame frame, Vector3f extents, @Nullable ConvexSweep cast,
                                     BoxBuffer boxes,
                                     double cx, double cy, double cz) {
        int[] found = new int[4];
        int count = 0;
        for (int i = 0, size = boxes.size(); i < size; i++) {
            if (!OBB.intersectsBox(frame, cx, cy, cz, extents,
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                    boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i))) {
                continue;
            }
            // Excusing a box the hull is not really inside would let it move straight through that
            // box, so the bound's answer is confirmed against the shape before it is believed. A
            // sweep with no motion is the static test: zero when the two overlap, one when they part.
            if (cast != null && cast.entry(cx, cy, cz, 0, 0, 0,
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                    boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i)) > 0) {
                continue;
            }
            if (count == found.length) {
                found = Arrays.copyOf(found, count * 2);
            }
            found[count++] = i;
        }
        return count == found.length ? found : Arrays.copyOf(found, count);
    }

    /**
     * Index of the first box the hull overlaps at its current pose, or -1.
     */
    public static int firstOverlappingBox(OBB obb, BoxBuffer boxes) {
        return firstOverlap(new OBB.SatFrame().set(obb.rotation()), obb.extents(), boxes,
                obb.centerX(), obb.centerY(), obb.centerZ());
    }

    /**
     * As above, confirming a bound-level overlap against the real shape where there is one.
     *
     * <p>A turn is refused by whatever the hull would end up inside, and the hull's box is bigger
     * than the hull. Without this, a tank with a sloped glacis would refuse to turn beside a wall its
     * plate leans away from.
     *
     * @param shape the posed hull, or null to answer from the box as before
     */
    public static int firstOverlappingBox(OBB obb, @Nullable ConvexSweep shape, BoxBuffer boxes) {
        OBB.SatFrame frame = new OBB.SatFrame().set(obb.rotation());
        ConvexSweep cast = shape != null && shape.ready() ? shape : null;
        double cx = obb.centerX();
        double cy = obb.centerY();
        double cz = obb.centerZ();
        for (int i = 0, size = boxes.size(); i < size; i++) {
            if (!OBB.intersectsBox(frame, cx, cy, cz, obb.extents(),
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                    boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i))) {
                continue;
            }
            // Inside the bound but not the shape: an entry fraction above zero says the shape would
            // still have to travel to reach this box, and it is not travelling.
            if (cast != null && cast.entry(cx, cy, cz, 0, 0, 0,
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                    boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i)) > 0) {
                continue;
            }
            return i;
        }
        return -1;
    }

    /** Index of the first box overlapping the hull at the given centre, or -1. */
    private static int firstOverlap(OBB.SatFrame frame, Vector3f extents, BoxBuffer boxes,
                                    double cx, double cy, double cz) {
        for (int i = 0, size = boxes.size(); i < size; i++) {
            if (OBB.intersectsBox(frame, cx, cy, cz, extents,
                    boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                    boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i))) {
                return i;
            }
        }
        return -1;
    }

}
