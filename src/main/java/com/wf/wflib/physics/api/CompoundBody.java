package com.wf.wflib.physics.api;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.wf.wflib.geom.api.OBB;

import java.util.ArrayList;
import java.util.List;

/**
 * Compound pieces of a vehicle beyond the one hull piece its solve is built around.
 *
 * <p>Solve frame = hull's frame: this poses every piece into it and maps contacts back, never
 * replaces the hull as the thing solved.
 *
 * <p>Only solid pieces from the pack's hull file; one inside the hull's own shape is dropped at
 * snapshot (adds no outside surface).
 *
 * <p>Contact inside another piece's solid dropped; shared face keeps both sides (boundary != interior).
 *
 * <p>Not thread-safe: one per vehicle, refilled on tick thread, read only by that vehicle's solve
 * (single thread for its life).
 */
public final class CompoundBody {

    /** Blocks. Speculative contact margin the swept queries reach by. */
    private static final double REACH_MARGIN = 0.05;

    /** Slack on the test that drops a piece for being inside the hull already, in blocks. */
    private static final float CONTAINED_EPS = 1.0e-3f;

    /** The piece the solve is built around. Everything here is measured in its frame. */
    @Nullable
    private HullPiece main;

    /** Its shape, or null when its box is its shape. */
    @Nullable
    private ConvexHull mainShape;

    private final Vector3f mainExtents = new Vector3f();

    /** The pieces in play this tick, and every Piece ever built, so a tick allocates nothing. */
    private final List<Piece> pieces = new ArrayList<>();
    private final List<Piece> pool = new ArrayList<>();

    /** Bound covering the hull and every piece, in the hull's frame. */
    private final Vector3f boundCentre = new Vector3f();
    private final Vector3f boundExtents = new Vector3f();

    /** Bound over pieces alone, hull's frame; rejects under-vehicle contacts before they reach a turret. */
    private float pieceMinX, pieceMinY, pieceMinZ, pieceMaxX, pieceMaxY, pieceMaxZ;

    /** Scratch for that bound in a climb box's frame, which sits above the hull's own. */
    private final Vector3f sweptCentre = new Vector3f();
    private final Vector3f scratch = new Vector3f();
    private final OBB.SatFrame reach = new OBB.SatFrame();
    private final Quaternionf inverse = new Quaternionf();
    private final Matrix3f inertiaBasis = new Matrix3f();
    private final Matrix3f inertiaRotated = new Matrix3f();
    private final Vector3f inertiaCentroid = new Vector3f();

    /** Whether this vehicle collides as more than the one piece its solve is built around. */
    public boolean hasPieces() {
        return !pieces.isEmpty();
    }

    public int pieceCount() {
        return pieces.size();
    }

    /** The piece at an index, for the debug view and for tests. */
    public HullPiece piece(int index) {
        return pieces.get(index).cube;
    }

    public ConvexHull pieceShape(int index) {
        return pieces.get(index).shape;
    }

    /** Hull frame. */
    public Vector3f pieceOffset(int index) {
        return pieces.get(index).offset;
    }

    /** Hull frame. */
    public Quaternionf pieceRotation(int index) {
        return pieces.get(index).rotation;
    }

    /** Whether solve meets world through this piece, not just the hull; debug view's "does this collide?". */
    public boolean solvesAgainst(HullPiece cube) {
        if (cube == main) {
            return true;
        }
        for (int i = 0, size = pieces.size(); i < size; i++) {
            if (pieces.get(i).cube == cube) {
                return true;
            }
        }
        return false;
    }

    /**
     * Must run after {@code updateOBBs}. Output stays valid all solve long: hull only translates
     * (never rotates) and a turret's traverse is tick-boundary discrete.
     *
     * @param cubes    every cube on the vehicle, body + parts
     * @param detached parallel to cubes; true = that piece left on its own entity
     */
    public void refresh(@Nullable HullPiece hull, List<? extends HullPiece> cubes,
                        boolean @Nullable [] detached) {
        pieces.clear();
        main = hull;
        if (hull == null) {
            mainShape = null;
            return;
        }
        mainShape = hull.hull();
        mainExtents.set(hull.obb().extents());
        boundCentre.zero();
        boundExtents.set(mainExtents);
        hull.localRotation().conjugate(inverse);
        float minX = -mainExtents.x, minY = -mainExtents.y, minZ = -mainExtents.z;
        float maxX = mainExtents.x, maxY = mainExtents.y, maxZ = mainExtents.z;
        for (int i = 0, size = cubes.size(); i < size; i++) {
            HullPiece cube = cubes.get(i);
            if (cube == hull || cube.hull() == null || !cube.isSolid()
                    || (detached != null && i < detached.length && detached[i])) {
                continue;
            }
            Piece piece = pieceFor(cube);
            inverse.mul(cube.localRotation(), piece.rotation);
            cube.localCenter().sub(hull.localCenter(), piece.offset);
            inverse.transform(piece.offset);
            piece.rotation.conjugate(piece.inverse);
            piece.bound();
            if (contained(piece)) {
                continue;
            }
            pieces.add(piece);
            minX = Math.min(minX, piece.minX);
            minY = Math.min(minY, piece.minY);
            minZ = Math.min(minZ, piece.minZ);
            maxX = Math.max(maxX, piece.maxX);
            maxY = Math.max(maxY, piece.maxY);
            maxZ = Math.max(maxZ, piece.maxZ);
        }
        boundCentre.set((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f);
        boundExtents.set((maxX - minX) * 0.5f, (maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f);
        pieceMinX = Float.MAX_VALUE; pieceMinY = Float.MAX_VALUE; pieceMinZ = Float.MAX_VALUE;
        pieceMaxX = -Float.MAX_VALUE; pieceMaxY = -Float.MAX_VALUE; pieceMaxZ = -Float.MAX_VALUE;
        for (int i = 0, size = pieces.size(); i < size; i++) {
            Piece piece = pieces.get(i);
            pieceMinX = Math.min(pieceMinX, piece.minX); pieceMaxX = Math.max(pieceMaxX, piece.maxX);
            pieceMinY = Math.min(pieceMinY, piece.minY); pieceMaxY = Math.max(pieceMaxY, piece.maxY);
            pieceMinZ = Math.min(pieceMinZ, piece.minZ); pieceMaxZ = Math.max(pieceMaxZ, piece.maxZ);
        }
    }

    /** Forgets everything; for a vehicle whose geometry is about to be rebuilt. */
    public void clear() {
        pieces.clear();
        pool.clear();
        main = null;
        mainShape = null;
    }

    private Piece pieceFor(HullPiece cube) {
        for (int i = 0, size = pool.size(); i < size; i++) {
            if (pool.get(i).cube == cube) {
                return pool.get(i);
            }
        }
        Piece piece = new Piece(cube);
        pool.add(piece);
        return piece;
    }

    /** Hull+pieces bound, climb-box frame; off-hull-centre as a compound cast, so centre/extents split. */
    public Vector3f sweptCentre(float rise) {
        return sweptCentre.set(boundCentre.x, boundCentre.y - rise, boundCentre.z);
    }

    public Vector3f sweptExtents() {
        return boundExtents;
    }

    /** Max reach from hull centre; the radius a spin sweeps. Sizes substep count elsewhere (half-block/slice target). */
    public float reachRadius() {
        float x = Math.max(Math.abs(boundCentre.x - boundExtents.x),
                Math.abs(boundCentre.x + boundExtents.x));
        float y = Math.max(Math.abs(boundCentre.y - boundExtents.y),
                Math.abs(boundCentre.y + boundExtents.y));
        float z = Math.max(Math.abs(boundCentre.z - boundExtents.z),
                Math.abs(boundCentre.z + boundExtents.z));
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Inertia per unit mass about com (hull frame): hull + collided pieces, uniform density.
     * Pieces inside the hull add no mass (dropped at snapshot).
     */
    public Matrix3f inertia(Vector3f com, Matrix3f dest) {
        dest.zero();
        if (main == null) {
            return dest;
        }
        float volume;
        if (mainShape != null) {
            volume = addMass(dest, mainShape.inertia(), mainShape.volume(), mainShape.centroid(), com);
        } else {
            float w = 2 * mainExtents.x, h = 2 * mainExtents.y, d = 2 * mainExtents.z;
            Matrix3f box = inertiaBasis.scaling((h * h + d * d) / 12, (w * w + d * d) / 12, (w * w + h * h) / 12);
            volume = addMass(dest, box, w * h * d, inertiaCentroid.zero(), com);
        }
        Matrix3f rotated = inertiaRotated;
        Matrix3f basis = inertiaBasis;
        Vector3f centroid = inertiaCentroid;
        for (int i = 0, size = pieces.size(); i < size; i++) {
            Piece piece = pieces.get(i);
            basis.set(piece.rotation);
            // R I R^T
            rotated.set(basis).mul(piece.shape.inertia()).mul(basis.transpose());
            piece.rotation.transform(piece.shape.centroid(), centroid).add(piece.offset);
            volume += addMass(dest, rotated, piece.shape.volume(), centroid, com);
        }
        return volume > 0 ? dest.scale(1 / volume) : dest;
    }

    /** dest += V (I + |r|^2 E - r r^T), r = centroid - com. @return V */
    private static float addMass(Matrix3f dest, Matrix3f inertia, float volume, Vector3f centroid, Vector3f com) {
        if (!(volume > 0)) {
            return 0;
        }
        float rx = centroid.x - com.x, ry = centroid.y - com.y, rz = centroid.z - com.z;
        float r2 = rx * rx + ry * ry + rz * rz;
        dest.m00 += volume * (inertia.m00 + r2 - rx * rx);
        dest.m11 += volume * (inertia.m11 + r2 - ry * ry);
        dest.m22 += volume * (inertia.m22 + r2 - rz * rz);
        float xy = volume * (inertia.m01 - rx * ry);
        float xz = volume * (inertia.m02 - rx * rz);
        float yz = volume * (inertia.m12 - ry * rz);
        dest.m01 += xy; dest.m10 += xy;
        dest.m02 += xz; dest.m20 += xz;
        dest.m12 += yz; dest.m21 += yz;
        return volume;
    }

    /**
     * First box any piece already overlaps, or null; what a refused turn rams.
     * @param climb  hull's climb box at pose tested
     * @param rise   climb box centre's offset above hull's own
     * @param bottom skirt plane height, hull's frame
     */
    @Nullable
    public AABB firstOverlap(OBB climb, float rise, float bottom, BoxBuffer boxes) {
        if (pieces.isEmpty() || boxes.isEmpty()
                || !anyPieceNear(climb, rise, boxes, 0, 0, 0)) {
            return null;
        }
        for (int i = 0, size = pieces.size(); i < size; i++) {
            Piece piece = pieces.get(i);
            if (!piece.prepare(bottom)) {
                continue;
            }
            piece.pose(climb, rise, true);
            BoxBuffer near = piece.narrow(boxes, 0, 0, 0);
            int hit = SweptHull.firstOverlappingBox(piece.posed, piece.sweep, near);
            if (hit >= 0) {
                return near.get(hit);
            }
        }
        return null;
    }

    /**
     * Earliest any piece meets the world, fraction of step; min over independent per-piece casts
     * (conservative where pieces overlap: stops early, never late).
     * @param climb  hull's climb box at pose cast from
     * @param rise   climb box centre's offset above hull's own
     * @param bottom skirt plane height, hull's frame
     * @param best   hull's own answer so far; never raised
     */
    public double timeOfImpact(OBB climb, float rise, float bottom, BoxBuffer boxes,
                               double moveX, double moveY, double moveZ, double best) {
        if (pieces.isEmpty() || boxes.isEmpty() || best <= 0
                || !anyPieceNear(climb, rise, boxes, moveX, moveY, moveZ)) {
            return best;
        }
        for (int i = 0, size = pieces.size(); i < size && best > 0; i++) {
            Piece piece = pieces.get(i);
            if (!piece.prepare(bottom)) {
                continue;
            }
            piece.pose(climb, rise, true);
            BoxBuffer near = piece.narrow(boxes, moveX, moveY, moveZ);
            if (near.isEmpty()) {
                continue;
            }
            best = Math.min(best, SweptHull.timeOfImpact(piece.posed, piece.sweep, near,
                    moveX, moveY, moveZ, null, piece.frame));
        }
        return best;
    }

    /** One-shot reject vs per-piece pose cost (quat multiply + 3 transforms); pieces-only bound, hull's own boxes don't count. */
    private boolean anyPieceNear(OBB from, float rise, BoxBuffer boxes,
                                 double moveX, double moveY, double moveZ) {
        reach.set(from.rotation());
        float cx = (pieceMinX + pieceMaxX) * 0.5f;
        float cy = (pieceMinY + pieceMaxY) * 0.5f - rise;
        float cz = (pieceMinZ + pieceMaxZ) * 0.5f;
        float ex = (pieceMaxX - pieceMinX) * 0.5f;
        float ey = (pieceMaxY - pieceMinY) * 0.5f;
        float ez = (pieceMaxZ - pieceMinZ) * 0.5f;
        double reachX = reach.a00 * ex + reach.a01 * ey + reach.a02 * ez + REACH_MARGIN;
        double reachY = reach.a10 * ex + reach.a11 * ey + reach.a12 * ez + REACH_MARGIN;
        double reachZ = reach.a20 * ex + reach.a21 * ey + reach.a22 * ez + REACH_MARGIN;
        double ox = from.centerX() + reach.r00 * cx + reach.r01 * cy + reach.r02 * cz;
        double oy = from.centerY() + reach.r10 * cx + reach.r11 * cy + reach.r12 * cz;
        double oz = from.centerZ() + reach.r20 * cx + reach.r21 * cy + reach.r22 * cz;
        return boxes.anyWithin(
                ox + Math.min(0, moveX) - reachX, oy + Math.min(0, moveY) - reachY,
                oz + Math.min(0, moveZ) - reachZ, ox + Math.max(0, moveX) + reachX,
                oy + Math.max(0, moveY) + reachY, oz + Math.max(0, moveZ) + reachZ);
    }

    /**
     * Behind every face plane = inside.
     * @param slack negative: strictly inside (contact test); positive: boundary counts (whole-piece test)
     */
    private static boolean behindEvery(ConvexHull shape, float x, float y, float z, float slack) {
        float[] planes = shape.planes();
        for (int f = 0, faces = shape.faceCount(); f < faces; f++) {
            if (planes[f * 4] * x + planes[f * 4 + 1] * y + planes[f * 4 + 2] * z
                    - planes[f * 4 + 3] > slack) {
                return false;
            }
        }
        return true;
    }

    /** Whether the hull's own shape already covers a piece, leaving it no surface of its own. */
    private boolean contained(Piece piece) {
        float[] points = piece.shape.points();
        for (int i = 0; i < points.length; i += 3) {
            scratch.set(points[i], points[i + 1], points[i + 2]);
            piece.rotation.transform(scratch).add(piece.offset);
            if (Math.abs(scratch.x) > mainExtents.x + CONTAINED_EPS
                    || Math.abs(scratch.y) > mainExtents.y + CONTAINED_EPS
                    || Math.abs(scratch.z) > mainExtents.z + CONTAINED_EPS) {
                return false;
            }
            if (mainShape != null
                    && !behindEvery(mainShape, scratch.x, scratch.y, scratch.z, CONTAINED_EPS)) {
                return false;
            }
        }
        return true;
    }

    /** One piece of the compound: its pose in the hull's frame, and its own solve scratch. */
    private final class Piece {

        private final HullPiece cube;
        private final ConvexHull shape;

        /** Where this piece sits in the hull's frame; constant for the whole of one solve. */
        private final Vector3f offset = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Quaternionf inverse = new Quaternionf();

        /** This piece's bound in the hull's frame, for rejecting points nowhere near it. */
        private float minX, minY, minZ, maxX, maxY, maxZ;

        private final OBB posed = new OBB(new Vector3f(), new Vector3f(), new Quaternionf());
        private final Vector3f[] axes = {new Vector3f(), new Vector3f(), new Vector3f()};
        private final OBB.SatFrame frame = new OBB.SatFrame();
        private final ConvexSweep sweep = new ConvexSweep();
        private final BoxBuffer near = new BoxBuffer();
        private final Vector3f local = new Vector3f();
        private final Vector3f spare = new Vector3f();
        private final Vector3f castCentre = new Vector3f();
        private final Vector3f castExtents = new Vector3f();

        /** The shape trimmed to the vehicle's skirt, about its own bound's centre. */
        @Nullable
        private ConvexHull cast;
        /** The plane the cached trim was cut by, in this piece's frame; NaN before the first cut. */
        private float cutX, cutY, cutZ, cutD = Float.NaN;

        Piece(HullPiece cube) {
            this.cube = cube;
            this.shape = cube.hull();
        }

        void bound() {
            Vector3f extents = cube.obb().extents();
            frame.set(rotation);
            float rx = frame.a00 * extents.x + frame.a01 * extents.y + frame.a02 * extents.z;
            float ry = frame.a10 * extents.x + frame.a11 * extents.y + frame.a12 * extents.z;
            float rz = frame.a20 * extents.x + frame.a21 * extents.y + frame.a22 * extents.z;
            minX = offset.x - rx; maxX = offset.x + rx;
            minY = offset.y - ry; maxY = offset.y + ry;
            minZ = offset.z - rz; maxZ = offset.z + rz;
        }

        /**
         * @param rise  offset above hull's own box centre (shared by climb box and hull box poses)
         * @param swept true: pose skirt-trimmed cast shape, not the whole piece
         */
        void pose(OBB from, float rise, boolean swept) {
            Quaternionf world = from.rotation();
            world.mul(rotation, posed.rotation());
            posed.extents().set(swept ? castExtents : cube.obb().extents());
            local.set(offset.x, offset.y - rise, offset.z);
            if (swept) {
                rotation.transform(castCentre, spare);
                local.add(spare);
            }
            world.transform(local);
            posed.setCenter(from.centerX() + local.x, from.centerY() + local.y,
                    from.centerZ() + local.z);
            axes[0].set(1, 0, 0);
            axes[1].set(0, 1, 0);
            axes[2].set(0, 0, 1);
            posed.rotation().transform(axes[0]);
            posed.rotation().transform(axes[1]);
            posed.rotation().transform(axes[2]);
            frame.set(posed.rotation());
            if (swept) {
                sweep.pose(cast, posed.rotation());
            }
        }

        /**
         * Hull-height skirt => plane in this (possibly rotated) piece's frame. Cached by plane params:
         * body piece cuts once per vehicle life; turret's yaw-only traverse leaves local down
         * unchanged, so its cut survives too.
         * @return false: nothing left to cast
         */
        private static final float CUT_TOLERANCE = 1.0e-4f;

        boolean prepare(float bottom) {
            inverse.transform(spare.set(0, -1, 0));
            float d = offset.y - bottom;
            // Tolerant: traversing turret's inverse carries float noise; exact compare would re-cut every tick.
            if (Math.abs(spare.x - cutX) < CUT_TOLERANCE && Math.abs(spare.y - cutY) < CUT_TOLERANCE
                    && Math.abs(spare.z - cutZ) < CUT_TOLERANCE && Math.abs(d - cutD) < CUT_TOLERANCE) {
                return cast != null;
            }
            cutX = spare.x; cutY = spare.y; cutZ = spare.z; cutD = d;
            cast = minY >= bottom ? shape : shape.clipped(spare.x, spare.y, spare.z, d, SweptHull.SLOP);
            if (cast == null) {
                sweep.clear();
                return false;
            }
            castCentre.set((cast.minX() + cast.maxX()) * 0.5f, (cast.minY() + cast.maxY()) * 0.5f,
                    (cast.minZ() + cast.maxZ()) * 0.5f);
            castExtents.set((cast.maxX() - cast.minX()) * 0.5f, (cast.maxY() - cast.minY()) * 0.5f,
                    (cast.maxZ() - cast.minZ()) * 0.5f);
            // Cast origin = box centre; shape re-measured from there to match.
            if (castCentre.lengthSquared() > 1.0e-12f) {
                cast = cast.transformed(new Quaternionf(), castCentre.negate(new Vector3f()));
            }
            return true;
        }

        /** The boxes that could reach this piece over the step; a subset of the vehicle's own. */
        BoxBuffer narrow(BoxBuffer boxes, double moveX, double moveY, double moveZ) {
            return narrow(boxes, moveX, moveY, moveZ, 0);
        }

        BoxBuffer narrow(BoxBuffer boxes, double moveX, double moveY, double moveZ, double margin) {
            Vector3f extents = posed.extents();
            double reachX = frame.a00 * extents.x + frame.a01 * extents.y + frame.a02 * extents.z
                    + margin;
            double reachY = frame.a10 * extents.x + frame.a11 * extents.y + frame.a12 * extents.z
                    + margin;
            double reachZ = frame.a20 * extents.x + frame.a21 * extents.y + frame.a22 * extents.z
                    + margin;
            double minBoxX = posed.centerX() + Math.min(0, moveX) - reachX;
            double minBoxY = posed.centerY() + Math.min(0, moveY) - reachY;
            double minBoxZ = posed.centerZ() + Math.min(0, moveZ) - reachZ;
            double maxBoxX = posed.centerX() + Math.max(0, moveX) + reachX;
            double maxBoxY = posed.centerY() + Math.max(0, moveY) + reachY;
            double maxBoxZ = posed.centerZ() + Math.max(0, moveZ) + reachZ;
            near.clear();
            if (!boxes.anyWithin(minBoxX, minBoxY, minBoxZ, maxBoxX, maxBoxY, maxBoxZ)) {
                return near;
            }
            for (int i = 0, size = boxes.size(); i < size; i++) {
                if (boxes.maxX(i) <= minBoxX || boxes.minX(i) >= maxBoxX
                        || boxes.maxY(i) <= minBoxY || boxes.minY(i) >= maxBoxY
                        || boxes.maxZ(i) <= minBoxZ || boxes.minZ(i) >= maxBoxZ) {
                    continue;
                }
                near.add(boxes.minX(i), boxes.minY(i), boxes.minZ(i),
                        boxes.maxX(i), boxes.maxY(i), boxes.maxZ(i));
            }
            return near;
        }

    }

}
