package com.wf.wflib.physics.api;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

public final class VehicleParenting {

    /**
     * How long a rider keeps its grip with nothing under it; twelve ticks covers a vanilla jump with margin.
     */
    public static final int GRIP_GRACE_TICKS = 12;

    /**
     * Slack around the hull bound within which an entity is considered near enough to parent.
     */
    private static final double REGION_MARGIN = 0.5;

    /**
     * How many ticks a rider may be missing before it is released.
     */
    private static final int MAX_MISS_TICKS = 2;

    /**
     * Depenetration passes to run when geometry arrives on top of an entity.
     */
    private static final int DEPENETRATION_PASSES = 4;

    /**
     * Velocity handed to a rider leaving a moving vehicle, capped here. Units: blocks/tick.
     */
    private static final double MAX_HANDOFF_SPEED = 4.0;

    /**
     * Maximum displacement per tick; above this, a pose change is read as a discontinuity.
     */
    private static final double MAX_CARRY_PER_TICK = 16.0;

    private VehicleParenting() {
    }

    // ---------------------------------------------------------------- frame conversions

    /**
     * Converts world coordinates to vehicle-local, preserving precision far from
     * the origin by subtracting in double before narrowing to float.
     */
    public static Vector3f toLocal(DeckSnapshot deck,
                                   double worldX, double worldY, double worldZ, Vector3f dest) {
        dest.set((float) (worldX - deck.pivotX()),
                (float) (worldY - deck.pivotY()),
                (float) (worldZ - deck.pivotZ()));
        deck.inverse().transform(dest);
        return dest;
    }

    public static Vector3d toLocal(DeckSnapshot deck,
                                   double worldX, double worldY, double worldZ, Vector3d dest) {
        dest.set(worldX - deck.pivotX(), worldY - deck.pivotY(), worldZ - deck.pivotZ());
        deck.inverse().transform(dest);
        return dest;
    }

    public static Vec3 toWorld(DeckSnapshot deck, Vector3d local, Vector3d scratch) {
        Vector3d point = deck.rotation().transform(scratch.set(local));
        return new Vec3(deck.pivotX() + point.x, deck.pivotY() + point.y, deck.pivotZ() + point.z);
    }

    // ---------------------------------------------------------------- attachment access

    @Nullable
    public static DeckAttachment attachmentOf(Entity entity) {
        return ((DeckRider) entity).wflib$deckAttachment();
    }

    /**
     * True when the attachment still names a vehicle that can carry this entity.
     */
    public static boolean isUsable(@Nullable DeckAttachment attachment, Entity entity) {
        if (attachment == null) {
            return false;
        }
        DeckBody vehicle = attachment.vehicle();
        return vehicle != null
                && !vehicle.isRemoved()
                && vehicle.collides()
                && vehicle.level() == entity.level()
                && vehicle.mainPiece() != null
                && vehicle.deckSnapshot().count() > 0;
    }

    // ---------------------------------------------------------------- what a rider walked

    /**
     * How far a rider actually walked this tick, measured in the hull's frame.
     *
     * <p>The step vanilla animates from is a world-space one, and on a ship under way the hull's own
     * movement is nearly all of it: a rider standing still plays a full walk cycle at fifteen blocks a
     * second. Subtracting the carry from the world step only lines up when the carry lands inside the
     * rider's own tick, and whether it does depends on which of the two ticked first. The hull's frame
     * does not care: a rider that has not moved on the deck has not moved in it, however its world
     * position came to be where it is.
     *
     * @param includeHeight whether a vertical step counts, which is vanilla's flying-animal case
     */
    public static float walkStep(Entity entity, DeckAttachment attachment, boolean includeHeight) {
        DeckBody vehicle = attachment.vehicle();
        if (vehicle == null) {
            return 0;
        }
        Vector3d now = toLocal(vehicle.deckSnapshot(),
                entity.getX(), entity.getY(), entity.getZ(), attachment.scratchAnim);
        float step = 0;
        if (attachment.animValid()) {
            Vector3d was = attachment.animLocal();
            step = (float) Mth.length(now.x - was.x,
                    includeHeight ? now.y - was.y : 0.0,
                    now.z - was.z);
        }
        attachment.setAnimLocal(now);
        return step;
    }

    // ---------------------------------------------------------------- the two tick hooks

    /** Called at the top of the vehicle tick. */
    public static void capture(DeckBody vehicle) {
        List<Entity> riders = vehicle.deckRiders();
        if (riders.isEmpty()) {
            return;
        }
        DeckSnapshot deck = vehicle.deckSnapshot();
        for (int i = riders.size() - 1; i >= 0; i--) {
            Entity entity = riders.get(i);
            DeckAttachment attachment = attachmentOf(entity);
            if (attachment == null || attachment.vehicle() != vehicle
                    || entity.isRemoved() || entity.level() != vehicle.level()) {
                riders.remove(i);
                continue;
            }
            attachment.capture(entity, deck);
        }
    }

    /** Called at the bottom of the vehicle tick, after pose settlement. */
    public static void tick(DeckBody vehicle) {
        List<Entity> riders = vehicle.deckRiders();
        if (!DeckSettings.current().parenting() || vehicle.isRemoved()
                || !vehicle.collides() || vehicle.mainPiece() == null
                || vehicle.deckSnapshot().count() == 0) {
            releaseAll(vehicle, false);
            return;
        }

        DeckSnapshot deck = vehicle.deckSnapshot();
        scan(vehicle, deck, riders);

        boolean clientSide = vehicle.level().isClientSide();
        long gameTime = TickClock.now(vehicle.level());
        for (int i = riders.size() - 1; i >= 0; i--) {
            Entity entity = riders.get(i);
            DeckAttachment attachment = attachmentOf(entity);
            if (attachment == null || attachment.vehicle() != vehicle
                    || entity.isRemoved() || entity.level() != vehicle.level()
                    || entity.isPassenger()) {
                if (attachment != null && attachment.vehicle() == vehicle) {
                    release(entity, attachment, true);
                }
                riders.remove(i);
                continue;
            }
            if (!attachment.seen() && attachment.incrementMissTicks() > MAX_MISS_TICKS) {
                release(entity, attachment, true);
                riders.remove(i);
                continue;
            }
            attachment.clearSeen();
            // A rider the client doesn't move never runs a collision clip (support's usual source);
            // probe here instead, or its grip lapses past grace and its carry goes stale.
            if (clientSide && !mayMove(vehicle, entity)
                    && DeckCollision.supportedBelow(entity, deck, attachment)) {
                attachment.markSupported();
            }
            attachment.advanceSupport();
            if (attachment.gripped()) {
                // A carry the entity has not yet ticked past is held until it has.
                if (attachment.lastEntityTick() == gameTime) {
                    applyCarry(vehicle, deck, entity, attachment);
                    settle(vehicle, deck, entity, attachment);
                } else {
                    // Catch up anything the entity never ticked past
                    if (attachment.pendingCarryTick() != Long.MIN_VALUE) {
                        applyCarry(vehicle, deck, entity, attachment);
                        settle(vehicle, deck, entity, attachment);
                    }
                    attachment.setPendingCarryTick(gameTime);
                }
            } else {
                // Only when no carry is outstanding. Re-capturing before a deferred carry runs
                // resolves the local position to where the entity already is, cancelling the carry.
                settle(vehicle, deck, entity, attachment);
            }
        }
    }

    /**
     * Re-applies carries when a harness-parented vehicle is moved by its carrier.
     * Skips scan and bookkeeping to preserve grip grace.
     */
    public static void reapply(DeckBody vehicle) {
        List<Entity> riders = vehicle.deckRiders();
        if (riders.isEmpty() || !DeckSettings.current().parenting()) {
            return;
        }
        DeckSnapshot deck = vehicle.deckSnapshot();
        if (deck.count() == 0) {
            return;
        }
        long gameTime = TickClock.now(vehicle.level());
        for (int i = riders.size() - 1; i >= 0; i--) {
            Entity entity = riders.get(i);
            DeckAttachment attachment = attachmentOf(entity);
            if (attachment == null || attachment.vehicle() != vehicle || entity.isRemoved()
                    || entity.isPassenger() || entity.level() != vehicle.level()) {
                continue;
            }
            if (!attachment.gripped()) {
                continue;
            }
            // Same deferral as tick: a pose written before the rider's own tick is copied into its old pose.
            if (attachment.lastEntityTick() == gameTime) {
                applyCarry(vehicle, deck, entity, attachment);
                settle(vehicle, deck, entity, attachment);
            } else {
                if (attachment.pendingCarryTick() != Long.MIN_VALUE && attachment.pendingCarryTick() != gameTime) {
                    applyCarry(vehicle, deck, entity, attachment);
                    settle(vehicle, deck, entity, attachment);
                }
                attachment.setPendingCarryTick(gameTime);
            }
        }
    }

    /**
     * Entity-tick hook: runs the carry left pending for this entity, if any.
     */
    public static void onEntityTicked(Entity entity) {
        DeckAttachment attachment = attachmentOf(entity);
        if (attachment == null) {
            return;
        }
        long gameTime = TickClock.now(entity.level());
        attachment.setLastEntityTick(gameTime);
        boolean usable = isUsable(attachment, entity);
        // Emit effects even for already-carried riders, as many carries per tick
        // is common.
        if (usable) {
            DeckEffects.emit(entity, attachment.vehicle(), attachment);
        }
        if (attachment.pendingCarryTick() != gameTime) {
            return;
        }
        attachment.clearPendingCarry();
        if (!usable) {
            // Also drops attachments to removed vehicles, so a discarded hull is not kept alive.
            ((DeckRider) entity).wflib$setDeckAttachment(null);
            return;
        }
        if (entity.isPassenger()) {
            return;
        }
        DeckBody vehicle = attachment.vehicle();
        // Use live snapshot since this runs after the vehicle's tick.
        DeckSnapshot deck = vehicle.deckSnapshot();
        applyCarry(vehicle, deck, entity, attachment);
        settle(vehicle, deck, entity, attachment);
    }

    private static void scan(DeckBody vehicle, DeckSnapshot deck, List<Entity> riders) {
        boolean clientSide = vehicle.level().isClientSide();
        AABB region = vehicle.getBoundingBox().inflate(REGION_MARGIN);
        // pushableBy answers false for everything but the local player on a client, since a client
        // pushes nothing; see eligible for why the client still wants the rest attached.
        List<Entity> candidates = vehicle.level().getEntities(vehicle.entity(), region,
                clientSide ? EntitySelector.NO_SPECTATORS : EntitySelector.pushableBy(vehicle.entity()));
        for (int i = 0, size = candidates.size(); i < size; i++) {
            Entity entity = candidates.get(i);
            if (!eligible(vehicle, entity, clientSide)) {
                continue;
            }
            DeckAttachment attachment = attachmentOf(entity);
            if (attachment != null && attachment.vehicle() == vehicle) {
                attachment.markSeen();
                continue;
            }
            // When two hulls overlap, the one supporting the entity keeps it.
            if (attachment != null && isUsable(attachment, entity) && attachment.gripped()) {
                continue;
            }
            attachment = adopt(vehicle, deck, entity, attachment);
            // Force grip if deck is already underneath; prevents slip-back on landing.
            if (DeckCollision.supportedBelow(entity, deck, attachment)) {
                attachment.forceGrip();
            }
        }
    }

    private static DeckAttachment adopt(DeckBody vehicle, DeckSnapshot deck, Entity entity,
                                        @Nullable DeckAttachment attachment) {
        if (attachment != null) {
            DeckBody previous = attachment.vehicle();
            if (previous != null) {
                previous.deckRiders().remove(entity);
            }
            attachment.setVehicle(vehicle);
        } else {
            attachment = new DeckAttachment(vehicle);
            ((DeckRider) entity).wflib$setDeckAttachment(attachment);
        }
        attachment.capture(entity, deck);
        attachment.markSeen();
        vehicle.deckRiders().add(entity);
        return attachment;
    }

    /** Parents an entity already placed on the deck, gripped from this tick. */
    static void attach(DeckBody vehicle, DeckSnapshot deck, Entity entity) {
        adopt(vehicle, deck, entity, attachmentOf(entity)).forceGrip();
    }

    private static boolean eligible(DeckBody vehicle, Entity entity, boolean clientSide) {
        if (entity == vehicle || entity instanceof PhysicsBody) {
            return false;
        }
        if (entity.isRemoved() || entity.noPhysics || entity.isSpectator() || entity.isPassenger()) {
            return false;
        }
        // The client parents anything that walks, so a rider's step can be measured in the deck's frame
        // rather than the world's. It moves only the local player; see mayMove.
        return !clientSide || entity instanceof LivingEntity;
    }

    private static boolean mayMove(DeckBody vehicle, Entity entity) {
        if (vehicle.level().isClientSide()) {
            // The client owns the local player's position and nothing else's: a remote rider's position
            // is the server's, and moving it here would fight the interpolation rather than help it.
            return entity instanceof Player player && player.isLocalPlayer();
        }
        return !(entity instanceof Player);
    }

    static void applyCarry(DeckBody vehicle, DeckSnapshot deck, Entity entity,
                           DeckAttachment attachment) {
        if (!attachment.localValid()) {
            return;
        }
        // Relative carry preserves the entity's own walk between capture and apply.
        Vector3d target = deck.rotation()
                .transform(attachment.scratchWorld.set(attachment.localPos()));
        double targetX = deck.pivotX() + target.x;
        double targetY = deck.pivotY() + target.y;
        double targetZ = deck.pivotZ() + target.z;
        double dx = targetX - attachment.carriedWorldX();
        double dy = targetY - attachment.carriedWorldY();
        double dz = targetZ - attachment.carriedWorldZ();
        // On hull discontinuity, re-derive local position; rider stays put and re-grips.
        if (dx * dx + dy * dy + dz * dz > MAX_CARRY_PER_TICK * MAX_CARRY_PER_TICK) {
            attachment.capture(entity, deck);
            attachment.setLastCarry(0, 0, 0);
            return;
        }
        float deltaYaw = Mth.wrapDegrees(deck.yaw() - attachment.carriedYaw());
        attachment.setCarried(targetX, targetY, targetZ, deck.yaw());
        // Record the carry even where not applied; the server uses it to widen its movement check.
        attachment.setLastCarry(dx, dy, dz);
        boolean moved = dx * dx + dy * dy + dz * dz > 1.0E-14;
        if (moved && mayMove(vehicle, entity)) {
            entity.setPos(entity.getX() + dx, entity.getY() + dy, entity.getZ() + dz);
            // Tracker sends position on its own interval (3 ticks for most mobs); client interpolating
            // that against a hull reporting every tick renders the rider ticks behind. hasImpulse is
            // vanilla's "moved by something else", and puts the rider on the hull's cadence.
            entity.hasImpulse = true;
        }
        if (deltaYaw != 0 && mayMove(vehicle, entity)) {
            carryRotation(entity, deltaYaw);
        }
    }

    /**
     * Turns the rider with the hull and rotates velocity to prevent sideways drift
     * on yawing decks.
     */
    private static void carryRotation(Entity entity, float deltaYaw) {
        entity.setYRot(entity.getYRot() + deltaYaw);
        entity.yRotO += deltaYaw;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot += deltaYaw;
            living.yBodyRotO += deltaYaw;
            living.yHeadRot += deltaYaw;
            living.yHeadRotO += deltaYaw;
        }
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.x != 0 || velocity.z != 0) {
            // Matches rotYXZ's convention, which composes yaw as rotateY(-yRot).
            double radians = Math.toRadians(-deltaYaw);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            entity.setDeltaMovement(velocity.x * cos + velocity.z * sin, velocity.y,
                    -velocity.x * sin + velocity.z * cos);
        }
    }

    /**
     * Runs the depenetration safety net and re-grips anything the hull pushed upward.
     */
    private static void settle(DeckBody vehicle, DeckSnapshot deck, Entity entity,
                               DeckAttachment attachment) {
        if (!mayMove(vehicle, entity)) {
            // The client runs its own settle; correcting a player server-side would cause packet rejection.
            return;
        }
        if (!DeckCollision.depenetrate(entity, deck, attachment, DEPENETRATION_PASSES)) {
            return;
        }
        Vector3d correction = attachment.scratchWorld;
        entity.setPos(entity.getX() + correction.x,
                entity.getY() + correction.y,
                entity.getZ() + correction.z);
        attachment.capture(entity, deck);
        if (correction.y > 0) {
            entity.setDeltaMovement(entity.getDeltaMovement().x,
                    Math.max(0, entity.getDeltaMovement().y),
                    entity.getDeltaMovement().z);
            entity.setOnGround(true);
            entity.fallDistance = 0;
            attachment.forceGrip();
        }
    }

    // ---------------------------------------------------------------- release

    /**
     * Detaches a rider and transfers platform velocity including rotational terms.
     */
    public static void release(Entity entity, DeckAttachment attachment, boolean handoff) {
        if (handoff && attachment.gripped()) {
            double speed = Math.sqrt(attachment.lastCarrySpeedSqr());
            if (speed > 1.0E-4) {
                double scale = speed > MAX_HANDOFF_SPEED ? MAX_HANDOFF_SPEED / speed : 1.0;
                entity.setDeltaMovement(entity.getDeltaMovement().add(
                        attachment.lastCarryX() * scale,
                        attachment.lastCarryY() * scale,
                        attachment.lastCarryZ() * scale));
            }
        }
        ((DeckRider) entity).wflib$setDeckAttachment(null);
    }

    /**
     * Hull unloading ahead of its riders: gripped non-players wait for it rather than fall. A player
     * keeps the hull's chunk loaded, so one left behind is out of its range and is let go.
     */
    public static void unloadAll(DeckBody vehicle) {
        List<Entity> riders = vehicle.deckRiders();
        for (int i = 0, size = riders.size(); i < size; i++) {
            Entity entity = riders.get(i);
            DeckAttachment attachment = attachmentOf(entity);
            if (attachment == null || attachment.vehicle() != vehicle) {
                continue;
            }
            boolean waits = attachment.gripped() && entity instanceof LivingEntity && !(entity instanceof Player)
                    && !entity.isRemoved() && !entity.isPassenger();
            release(entity, attachment, !waits);
            if (waits) {
                DeckRejoin.leaveWaiting(entity, vehicle);
            }
        }
        riders.clear();
    }

    /**
     * Lets go of every rider, e.g. when the vehicle is discarded or parenting is switched off.
     */
    public static void releaseAll(DeckBody vehicle, boolean handoff) {
        List<Entity> riders = vehicle.deckRiders();
        if (riders.isEmpty()) {
            return;
        }
        for (int i = 0, size = riders.size(); i < size; i++) {
            Entity entity = riders.get(i);
            DeckAttachment attachment = attachmentOf(entity);
            if (attachment != null && attachment.vehicle() == vehicle) {
                release(entity, attachment, handoff);
            }
        }
        riders.clear();
    }

    // ---------------------------------------------------------------- the collide hook

    /**
     * Clips an entity's movement against the hull it stands on, called at the end of vanilla collision.
     */
    public static Vec3 clipMovement(Entity entity, Vec3 worldMove) {
        DeckAttachment attachment = attachmentOf(entity);
        if (attachment == null) {
            return worldMove;
        }
        if (!isUsable(attachment, entity) || !DeckSettings.current().parenting()) {
            ((DeckRider) entity).wflib$setDeckAttachment(null);
            return worldMove;
        }
        if (entity.noPhysics || entity.isPassenger()) {
            return worldMove;
        }
        return DeckCollision.clip(entity, attachment.vehicle().deckSnapshot(), worldMove, attachment);
    }

}
