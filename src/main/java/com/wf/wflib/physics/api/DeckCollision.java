package com.wf.wflib.physics.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Entity-vs-hull collision, run in the vehicle's own reference frame.
 */
public final class DeckCollision {

    /**
     * How far the depenetration net may push in one call (and so how much slack its box gather
     * needs). Deeper than this: entity is buried well inside a hull, one more pass will not help.
     */
    private static final double MAX_CORRECTION = 4.0;

    /** Past ~60 degrees lean, a hull is on its beam ends; vertical-only correction stops seating a rider. */
    private static final double MIN_UP_AXIS = 0.5;

    private DeckCollision() {}

    /**
     * Clips movement against the hull in world space. Movement already settled by vanilla block collision.
     * @param attachment marked supported when downward movement was stopped
     */
    public static Vec3 clip(Entity entity, DeckSnapshot deck, Vec3 worldMove,
                            DeckAttachment attachment) {
        float[] boxes = deck.boxes();
        int count = deck.count();
        if (count == 0) {
            return worldMove;
        }
        // Read-only shares, not copies: runs per rider per move; building two quaternions to
        // conjugate one was the largest single allocator in the system.
        Quaternionf rotation = deck.rotation();
        Quaternionf inverse = deck.inverse();

        AABB bounds = entity.getBoundingBox();
        Vector3f centre = VehicleParenting.toLocal(deck,
                (bounds.minX + bounds.maxX) * 0.5,
                (bounds.minY + bounds.maxY) * 0.5,
                (bounds.minZ + bounds.maxZ) * 0.5,
                attachment.scratchCentre);
        Vector3f move = inverse.transform(attachment.scratchMove.set(
                (float) worldMove.x, (float) worldMove.y, (float) worldMove.z));
        // World-vertical part removed: entity's own horizontal intent, in-frame. See
        // DeckClip.sweep for what a grounded rider does with it.
        Vector3f grounded = inverse.transform(attachment.scratchGrounded.set(
                (float) worldMove.x, 0, (float) worldMove.z));

        double hx = (bounds.maxX - bounds.minX) * 0.5;
        double hy = (bounds.maxY - bounds.minY) * 0.5;
        double hz = (bounds.maxZ - bounds.minZ) * 0.5;
        // Gathered once; the axis passes below then cost what the contact is, not what the hull is.
        float[] near = attachment.nearBuffer(count);
        int nearCount = DeckClip.narrow(boxes, count, centre.x, centre.y, centre.z, hx, hy, hz,
                move.x, move.y, move.z, grounded.x, grounded.z, entity.maxUpStep(), near);
        if (nearCount == 0) {
            return worldMove;
        }

        double[] allowed = attachment.scratchOut;
        boolean blockedDown = DeckClip.sweep(near, nearCount,
                centre.x, centre.y, centre.z, hx, hy, hz,
                move.x, move.y, move.z,
                grounded.x, grounded.z,
                entity.maxUpStep(), entity.onGround(),
                allowed);

        if (blockedDown) {
            attachment.markSupported();
            // Deck-relative is the only reading that means anything: a rider standing still while
            // the carrier moves has not taken a step. Emitted from the tick thread by DeckEffects.
            DeckEffects.walked(entity, attachment, allowed[0], allowed[2]);
        }
        if (allowed[0] == move.x && allowed[1] == move.y && allowed[2] == move.z) {
            return worldMove;
        }
        // Entity.move infers a wall from wanted != collided and zeroes horizontal velocity (kills
        // player sprint) on any difference; 1 degree of hull tilt already crosses its 1e-5
        // threshold. Correcting world Y alone reproduces the deck-frame height to ~1e-7 and
        // leaves X/Z bit-identical, so that inference is never triggered by a stopped descent alone.
        boolean sideBlocked = (allowed[0] != move.x && allowed[0] != grounded.x)
                || (allowed[2] != move.z && allowed[2] != grounded.z);
        if (!sideBlocked) {
            // Deck up-axis dot world up = rotation's yy term (shared with its transpose), converting
            // a height in either frame to the other.
            double upAxis = 1.0 - 2.0 * (rotation.x * rotation.x + rotation.z * rotation.z);
            if (upAxis > MIN_UP_AXIS) {
                return new Vec3(worldMove.x,
                        worldMove.y + (allowed[1] - move.y) / upAxis,
                        worldMove.z);
            }
        }
        // Correction, not a re-transform: untouched axes come back bit-identical. Entity.move's
        // verticalCollision is an exact "wanted.y != collided.y"; float-rotation round-trip perturbs
        // Y by ~1e-8 whenever anything clips, which would falsely stop falling. No pitch/roll =>
        // vertical correction rotates to pure vertical, horizontal survives untouched.
        Vector3d correction = rotation.transform(attachment.scratchWorld.set(
                allowed[0] - move.x, allowed[1] - move.y, allowed[2] - move.z));
        return new Vec3(worldMove.x + correction.x,
                worldMove.y + correction.y,
                worldMove.z + correction.z);
    }

    public static boolean supportedBelow(Entity entity, DeckSnapshot deck,
                                        DeckAttachment attachment) {
        float[] boxes = deck.boxes();
        int count = deck.count();
        if (count == 0) {
            return false;
        }
        AABB bounds = entity.getBoundingBox();
        Vector3f centre = VehicleParenting.toLocal(deck,
                (bounds.minX + bounds.maxX) * 0.5,
                (bounds.minY + bounds.maxY) * 0.5,
                (bounds.minZ + bounds.maxZ) * 0.5,
                attachment.scratchCentre);
        double reach = -(DeckClip.SKIN + 0.02);
        return DeckClip.clipY(boxes, count, centre.x, centre.y, centre.z,
                (bounds.maxX - bounds.minX) * 0.5,
                (bounds.maxY - bounds.minY) * 0.5,
                (bounds.maxZ - bounds.minZ) * 0.5,
                reach) > reach;
    }

    /**
     * Probes whether the deck supports an offset box, answering the question
     * Player.canFallAtLeast asks of the world.
     */
    /** Answers what Player.canFallAtLeast asks of the world. */
    public static boolean supportsOffset(Entity entity, DeckSnapshot deck,
                                         DeckAttachment attachment,
                                         double dx, double dz, double depth) {
        float[] boxes = deck.boxes();
        int count = deck.count();
        if (count == 0) {
            return false;
        }
        AABB bounds = entity.getBoundingBox();
        // Vanilla's thin slab: footprint, offset, spanning depth below the feet.
        Vector3f centre = VehicleParenting.toLocal(deck,
                (bounds.minX + bounds.maxX) * 0.5 + dx,
                bounds.minY - depth * 0.5,
                (bounds.minZ + bounds.maxZ) * 0.5 + dz,
                attachment.scratchCentre);
        return DeckClip.anyBoxNear(boxes, count, centre.x, centre.y, centre.z,
                (bounds.maxX - bounds.minX) * 0.5,
                depth * 0.5,
                (bounds.maxZ - bounds.minZ) * 0.5,
                0, 0, 0);
    }

    /**
     * Pushes an entity out of hull geometry when it arrives on top. Safety net, not the main clip mechanism.
     * @return true when correction was applied
     */
    public static boolean depenetrate(Entity entity, DeckSnapshot deck,
                                      DeckAttachment attachment, int maxPasses) {
        float[] boxes = deck.boxes();
        int count = deck.count();
        if (count == 0) {
            return false;
        }
        Quaternionf rotation = deck.rotation();

        AABB bounds = entity.getBoundingBox();
        Vector3f centre = VehicleParenting.toLocal(deck,
                (bounds.minX + bounds.maxX) * 0.5,
                (bounds.minY + bounds.maxY) * 0.5,
                (bounds.minZ + bounds.maxZ) * 0.5,
                attachment.scratchCentre);

        double hx = (bounds.maxX - bounds.minX) * 0.5;
        double hy = (bounds.maxY - bounds.minY) * 0.5;
        double hz = (bounds.maxZ - bounds.minZ) * 0.5;
        // Grown by MAX_CORRECTION so a box the entity is pushed toward is still in the set next pass.
        float[] near = attachment.nearBuffer(count);
        int nearCount = DeckClip.narrow(boxes, count, centre.x, centre.y, centre.z,
                hx + MAX_CORRECTION, hy + MAX_CORRECTION, hz + MAX_CORRECTION,
                0, 0, 0, 0, 0, 0, near);
        if (nearCount == 0) {
            return false;
        }

        double[] correction = attachment.scratchOut;
        // Hull overhanging a rider on terrain (dismount beside the hull): down would bury it.
        boolean mayDescend = entity.level().noCollision(entity, bounds.move(0, -DeckClip.SKIN * 2, 0));
        if (!DeckClip.depenetrate(near, nearCount,
                centre.x, centre.y, centre.z, hx, hy, hz,
                mayDescend, maxPasses, correction)) {
            return false;
        }
        rotation.transform(attachment.scratchWorld.set(correction[0], correction[1], correction[2]));
        return true;
    }

}
