package com.wf.wflib.physics.api;

import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * An entity's parent frame on a vehicle.
 * Stores the entity's position in the vehicle's local frame, updated every tick from the vehicle's pose.
 */
public final class DeckAttachment {

    /** Verify with VehicleParenting.isUsable before use. */
    private DeckBody vehicle;

    /** Double for precision. */
    private final Vector3d localPos = new Vector3d();

    /** True after first capture; prevents garbage pose on fresh attachments. */
    private boolean localValid;

    /** Hysteresis counter to survive brief air time during jumps. */
    private int airTicks = Integer.MAX_VALUE;

    /** Set by collision clip when a downward move hits this vehicle's geometry. */
    private boolean supportedThisTick;

    /** Set by vehicle scan when entity is found; cleared after processing. */
    private boolean seen;

    private int missTicks;

    /** Velocity plus rotational component; kept on detach for handoff. */
    private double lastCarryX;
    private double lastCarryY;
    private double lastCarryZ;

    /** Kept apart from {@link #localPos}, which the carry rewrites every tick. */
    private final Vector3d animLocal = new Vector3d();
    private boolean animValid;

    /** Carry is applied as delta against these. */
    private double carriedWorldX;
    private double carriedWorldY;
    private double carriedWorldZ;
    private float carriedYaw;

    private long lastEntityTick = Long.MIN_VALUE;
    private long pendingCarryTick = Long.MIN_VALUE;

    /** Allocated once per attachment, reused. */
    final Vector3f scratchCentre = new Vector3f();
    final Vector3f scratchMove = new Vector3f();
    final Vector3f scratchGrounded = new Vector3f();
    final Vector3d scratchWorld = new Vector3d();
    final Vector3d scratchAnim = new Vector3d();
    final double[] scratchOut = new double[3];
    private float[] scratchNear = new float[8 * 6];

    /** Grown as needed for the hull. */
    float[] nearBuffer(int boxCount) {
        if (scratchNear.length < boxCount * 6) {
            scratchNear = new float[boxCount * 6];
        }
        return scratchNear;
    }

    public DeckAttachment(DeckBody vehicle) {
        this.vehicle = vehicle;
    }

    public DeckBody vehicle() {
        return vehicle;
    }

    public void setVehicle(DeckBody vehicle) {
        if (this.vehicle != vehicle) {
            this.vehicle = vehicle;
            this.localValid = false;
            this.animValid = false;
            this.airTicks = Integer.MAX_VALUE;
            setLastCarry(0, 0, 0);
        }
        this.missTicks = 0;
    }

    /** Valid only when animValid. */
    Vector3d animLocal() {
        return animLocal;
    }

    boolean animValid() {
        return animValid;
    }

    void setAnimLocal(Vector3d local) {
        animLocal.set(local);
        animValid = true;
    }

    /** Read-only: caller must not mutate the returned vector. */
    public Vector3d localPos() {
        return localPos;
    }

    public boolean localValid() {
        return localValid;
    }

    public void capture(Entity entity, DeckSnapshot deck) {
        VehicleParenting.toLocal(deck, entity.getX(), entity.getY(), entity.getZ(), localPos);
        setCarried(entity.getX(), entity.getY(), entity.getZ(), deck.yaw());
        localValid = true;
    }

    public double carriedWorldX() {
        return carriedWorldX;
    }

    public double carriedWorldY() {
        return carriedWorldY;
    }

    public double carriedWorldZ() {
        return carriedWorldZ;
    }

    public float carriedYaw() {
        return carriedYaw;
    }

    public void setCarried(double x, double y, double z, float yaw) {
        this.carriedWorldX = x;
        this.carriedWorldY = y;
        this.carriedWorldZ = z;
        this.carriedYaw = yaw;
    }

    public long lastEntityTick() {
        return lastEntityTick;
    }

    public void setLastEntityTick(long gameTime) {
        this.lastEntityTick = gameTime;
    }

    public long pendingCarryTick() {
        return pendingCarryTick;
    }

    public void setPendingCarryTick(long gameTime) {
        this.pendingCarryTick = gameTime;
    }

    public void clearPendingCarry() {
        this.pendingCarryTick = Long.MIN_VALUE;
    }

    public int airTicks() {
        return airTicks;
    }

    public boolean gripped() {
        return localValid && airTicks <= VehicleParenting.GRIP_GRACE_TICKS;
    }

    public void markSupported() {
        supportedThisTick = true;
    }

    public void advanceSupport() {
        if (supportedThisTick) {
            airTicks = 0;
            supportedThisTick = false;
        } else if (airTicks != Integer.MAX_VALUE) {
            airTicks++;
        }
    }

    /** Skips collision detection; immediate grip. */
    public void forceGrip() {
        airTicks = 0;
    }

    public boolean seen() {
        return seen;
    }

    public void markSeen() {
        this.seen = true;
        this.missTicks = 0;
    }

    public void clearSeen() {
        this.seen = false;
    }

    public int incrementMissTicks() {
        return ++missTicks;
    }

    public double lastCarryX() {
        return lastCarryX;
    }

    public double lastCarryY() {
        return lastCarryY;
    }

    public double lastCarryZ() {
        return lastCarryZ;
    }

    /** Units: (blocks/tick)^2. */
    public double lastCarrySpeedSqr() {
        return lastCarryX * lastCarryX + lastCarryY * lastCarryY + lastCarryZ * lastCarryZ;
    }

    public void setLastCarry(double x, double y, double z) {
        this.lastCarryX = x;
        this.lastCarryY = y;
        this.lastCarryZ = z;
    }

    // ---------------------------------------------------------------- surface effects

    /** Distance walked since last footfall; queued for sound playback on tick thread. */
    private double walkDistance;
    private boolean fallSoundPending;

    void addWalk(double distance) {
        walkDistance += distance;
    }

    void queueFallSound() {
        fallSoundPending = true;
    }

    boolean takeStep(double interval) {
        if (walkDistance < interval) {
            return false;
        }
        // Cap at one step/tick: a rider flung along a deck should not machine-gun footsteps.
        // Leftover beyond 2x interval is worthless, so dropped rather than banked.
        walkDistance = walkDistance > interval * 2 ? 0 : walkDistance - interval;
        return true;
    }

    boolean takeFallSound() {
        boolean pending = fallSoundPending;
        fallSoundPending = false;
        return pending;
    }

    /** Called when discarding an attachment. */
    void clearPendingSounds() {
        walkDistance = 0;
        fallSoundPending = false;
    }

}
