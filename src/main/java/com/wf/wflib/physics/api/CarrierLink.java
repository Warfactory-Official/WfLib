package com.wf.wflib.physics.api;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.wf.wflib.geom.api.OBB;

import java.util.Arrays;
import java.util.List;

/**
 * One vehicle's frozen view of the carrier beneath it, for the tick it was taken.
 * Deck geometry is queried in the carrier's frame to avoid expensive rotations of large boxes.
 */
public final class CarrierLink implements DeckView {

    /** Slack margin around the gather bound to compensate for frame coordinate rounding. */
    private static final double SELECT_MARGIN = 1.0;
    /** |w| of a local rotation treated as none. */
    private static final float IDENTITY_W = 1 - 1e-6f;

    @Nullable
    private DeckBody carrier;

    /** The carrier's published pose, copied rather than referenced. */
    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf inverse = new Quaternionf();
    private double pivotX, pivotY, pivotZ;

    /** Carrier's frame. */
    private final BoxBuffer boxes = new BoxBuffer();
    /** {@link DeckSnapshot#frames}, {@link DeckSnapshot#hulls}, {@link DeckSnapshot#landing} of {@link #boxes}, same order. */
    private float[] frames = new float[DeckSnapshot.FRAME * 8];
    private ConvexHull[] hulls = new ConvexHull[8];
    private boolean[] landing = new boolean[8];

    /** The hull in the carrier's frame. Centre rewritten per cast; rotation fixed for the tick. */
    private final OBB localHull = new OBB(new Vector3f(), new Vector3f(), new Quaternionf());
    private final Quaternionf localRotation = new Quaternionf();
    private final OBB.SatFrame localFrame = new OBB.SatFrame();

    /** Unrotated pieces ({@link #boxes} exact); the rest cast in their own frame. */
    private final BoxBuffer axisBoxes = new BoxBuffer();
    private int[] oriented = new int[8];
    private int orientedCount;
    private final OBB pieceHull = new OBB(new Vector3f(), new Vector3f(), new Quaternionf());
    private final OBB.SatFrame pieceFrame = new OBB.SatFrame();
    private final BoxBuffer pieceBox = new BoxBuffer();
    private final Quaternionf pieceInverse = new Quaternionf();

    /** Trial pose for overlaps, testing a rotation not yet adopted. */
    private final OBB turnHull = new OBB(new Vector3f(), new Vector3f(), new Quaternionf());

    private final Vector3d scratchLocal = new Vector3d();
    private final Vector3f scratchMove = new Vector3f();
    private final Vector3f pieceMove = new Vector3f();

    /** Bottom-face contacts this solve found on the deck. Solve thread. */
    private int supportContacts;

    @Nullable
    private DeckBody supporting;

    /** Hull world rotation localRotation was derived from; turns after refresh re-derive it. */
    private final Quaternionf derivedFrom = new Quaternionf();

    @Override
    public boolean active() {
        return carrier != null && boxes.size() > 0;
    }

    @Nullable
    @Override
    public DeckBody carrier() {
        return carrier;
    }

    /** Tick thread. True only when the last flushed solve's support matches this tick's refreshed carrier. */
    public boolean supported() {
        return supporting != null && supporting == carrier;
    }

    /** Carrier the last flushed solve stood on; may differ from this tick's refresh. Tick thread. */
    @Nullable
    public DeckBody supportingCarrier() {
        return supporting;
    }

    /** Carrier frame. */
    @Override
    public int deckCount() {
        return boxes.size();
    }

    public BoxBuffer deckBoxes() {
        return boxes;
    }

    /** Box i's oriented frame, {@link DeckSnapshot#frames} layout. */
    @Override
    public float deckFrame(int i, int k) {
        return frames[i * DeckSnapshot.FRAME + k];
    }

    @Nullable
    @Override
    public ConvexHull deckHull(int i) {
        return hulls[i];
    }

    /** Box i stood on (landing subset); else wall / roof of the carrier. */
    @Override
    public boolean landing(int i) {
        return landing[i];
    }

    /** Carrier frame -> world rotation. */
    @Override
    public Quaternionf deckRotation() {
        return rotation;
    }

    @Override
    public double pivotX() {
        return pivotX;
    }

    @Override
    public double pivotY() {
        return pivotY;
    }

    @Override
    public double pivotZ() {
        return pivotZ;
    }

    /** Solve: bottom contacts found on the deck. */
    @Override
    public void supportContacts(int count) {
        supportContacts = count;
    }

    /** Tick thread, after a solve is applied. */
    public void publish() {
        supporting = supportContacts > 0 ? carrier : null;
    }

    public void clear() {
        reset();
        supporting = null;
    }

    private void reset() {
        carrier = null;
        boxes.clear();
        axisBoxes.clear();
        orientedCount = 0;
        supportContacts = 0;
        // Frame changes per bind => next derive must run.
        derivedFrom.set(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
    }

    /**
     * Picks this tick's carrier and freezes everything a solve will read from it. Tick thread only.
     * @param gather the region the tick's physics may reach, in world space
     * @return whether a carrier is in range
     */
    public boolean refresh(DeckBody vehicle, AABB gather) {
        reset();
        // Client vehicles may tick before display data arrives and have no hull yet.
        if (!DeckSettings.current().carrierDecks() || !vehicle.collides()
                || vehicle.mainPiece() == null
                || !CarrierDecks.any(vehicle.level())) {
            return false;
        }
        // Most bound overlap first; next candidate when one has no deck box in reach.
        List<DeckBody> candidates = CarrierDecks.overlapping(vehicle, gather);
        for (int i = 0, size = candidates.size(); i < size; i++) {
            if (bind(vehicle, candidates.get(i), gather)) {
                return true;
            }
        }
        return false;
    }

    private boolean bind(DeckBody vehicle, DeckBody found, AABB gather) {
        DeckSnapshot deck = found.deckSnapshot();
        if (deck.deckCount() == 0) {
            return false;
        }
        rotation.set(deck.rotation());
        inverse.set(deck.inverse());
        pivotX = deck.pivotX();
        pivotY = deck.pivotY();
        pivotZ = deck.pivotZ();

        // Transform the gather bound through the frame change.
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            DeckFrame.toLocal(inverse, pivotX, pivotY, pivotZ,
                    (i & 1) == 0 ? gather.minX : gather.maxX,
                    (i & 2) == 0 ? gather.minY : gather.maxY,
                    (i & 4) == 0 ? gather.minZ : gather.maxZ, scratchLocal);
            if (scratchLocal.x < minX) minX = scratchLocal.x;
            if (scratchLocal.y < minY) minY = scratchLocal.y;
            if (scratchLocal.z < minZ) minZ = scratchLocal.z;
            if (scratchLocal.x > maxX) maxX = scratchLocal.x;
            if (scratchLocal.y > maxY) maxY = scratchLocal.y;
            if (scratchLocal.z > maxZ) maxZ = scratchLocal.z;
        }
        // Every solid piece: a rider leaves the carrier pair (broadphase), so walls collide here or never.
        float[] all = deck.boxes(), allFrames = deck.frames();
        for (int i = 0, count = deck.count(); i < count; i++) {
            int o = i * 6;
            if (all[o] > maxX + SELECT_MARGIN || all[o + 3] < minX - SELECT_MARGIN
                    || all[o + 1] > maxY + SELECT_MARGIN || all[o + 4] < minY - SELECT_MARGIN
                    || all[o + 2] > maxZ + SELECT_MARGIN || all[o + 5] < minZ - SELECT_MARGIN) {
                continue;
            }
            int n = boxes.size(), f = n * DeckSnapshot.FRAME;
            if (hulls.length <= n) {
                frames = Arrays.copyOf(frames, frames.length * 2);
                hulls = Arrays.copyOf(hulls, hulls.length * 2);
                landing = Arrays.copyOf(landing, landing.length * 2);
            }
            System.arraycopy(allFrames, i * DeckSnapshot.FRAME, frames, f, DeckSnapshot.FRAME);
            hulls[n] = deck.hulls()[i];
            landing[n] = deck.landing()[i];
            boxes.add(all[o], all[o + 1], all[o + 2], all[o + 3], all[o + 4], all[o + 5]);
            if (Math.abs(frames[f + 9]) >= IDENTITY_W) {
                axisBoxes.add(all[o], all[o + 1], all[o + 2], all[o + 3], all[o + 4], all[o + 5]);
            } else {
                if (orientedCount == oriented.length) {
                    oriented = Arrays.copyOf(oriented, orientedCount * 2);
                }
                oriented[orientedCount++] = n;
            }
        }
        if (boxes.isEmpty()) {
            return false;
        }

        derive(vehicle.mainPiece().obb().rotation());
        carrier = found;
        return true;
    }

    /** Hull rotation in the carrier's frame. Substeps only translate; a turn between refresh and solve re-derives. */
    private void derive(Quaternionf worldRotation) {
        if (worldRotation.equals(derivedFrom)) {
            return;
        }
        derivedFrom.set(worldRotation);
        DeckFrame.toLocalRotation(inverse, worldRotation, localRotation);
        localFrame.set(localRotation);
        localHull.rotation().set(localRotation);
    }

    /** Box i's frame <- hull (carrier frame): {@link #pieceHull}, {@link #pieceFrame}, {@link #pieceBox}, {@link #pieceInverse}. */
    private void toPiece(int i, OBB hull) {
        int f = i * DeckSnapshot.FRAME;
        pieceInverse.set(frames[f + 6], frames[f + 7], frames[f + 8], frames[f + 9]).conjugate();
        pieceInverse.transform(scratchLocal.set(hull.centerX() - frames[f], hull.centerY() - frames[f + 1],
                hull.centerZ() - frames[f + 2]));
        pieceHull.setCenter(scratchLocal.x, scratchLocal.y, scratchLocal.z);
        pieceHull.extents().set(hull.extents());
        pieceInverse.mul(hull.rotation(), pieceHull.rotation());
        pieceFrame.set(pieceHull.rotation());
        pieceBox.clear();
        pieceBox.add(-frames[f + 3], -frames[f + 4], -frames[f + 5], frames[f + 3], frames[f + 4], frames[f + 5]);
    }

    /**
     * How much of a cast the deck allows, as a fraction in [0, 1].
     * Combined with the world cast result, the hull stops at the nearer contact.
     */
    public double timeOfImpact(OBB worldHull, double moveX, double moveY, double moveZ) {
        if (!active()) {
            return 1.0;
        }
        derive(worldHull.rotation());
        // Full precision: a deck contact is meaningless if the hull's own position was rounded
        // to a float grid before it was rotated into the carrier's frame.
        DeckFrame.toLocal(inverse, pivotX, pivotY, pivotZ,
                worldHull.centerX(), worldHull.centerY(), worldHull.centerZ(), scratchLocal);
        localHull.setCenter(scratchLocal.x, scratchLocal.y, scratchLocal.z);
        localHull.extents().set(worldHull.extents());
        // Transform movement to the carrier's frame.
        scratchMove.set((float) moveX, (float) moveY, (float) moveZ);
        inverse.transform(scratchMove);
        double toi = SweptHull.timeOfImpact(localHull, axisBoxes,
                scratchMove.x, scratchMove.y, scratchMove.z, null, localFrame);
        for (int k = 0; k < orientedCount && toi > 0; k++) {
            toPiece(oriented[k], localHull);
            Vector3f move = pieceInverse.transform(pieceMove.set(scratchMove));
            toi = Math.min(toi, SweptHull.timeOfImpact(pieceHull, pieceBox, move.x, move.y, move.z, null, pieceFrame));
        }
        return toi;
    }

    /**
     * Whether a trial pose would put the hull inside the deck.
     * Builds its own SAT frame because the pose being tested has not been adopted yet.
     */
    public boolean overlaps(OBB worldHull) {
        if (!active()) {
            return false;
        }
        // Full precision, as above.
        DeckFrame.toLocal(inverse, pivotX, pivotY, pivotZ,
                worldHull.centerX(), worldHull.centerY(), worldHull.centerZ(), scratchLocal);
        turnHull.setCenter(scratchLocal.x, scratchLocal.y, scratchLocal.z);
        turnHull.extents().set(worldHull.extents());
        DeckFrame.toLocalRotation(inverse, worldHull.rotation(), turnHull.rotation());
        if (SweptHull.firstOverlappingBox(turnHull, axisBoxes) >= 0) {
            return true;
        }
        for (int k = 0; k < orientedCount; k++) {
            toPiece(oriented[k], turnHull);
            if (SweptHull.firstOverlappingBox(pieceHull, pieceBox) >= 0) {
                return true;
            }
        }
        return false;
    }

}
