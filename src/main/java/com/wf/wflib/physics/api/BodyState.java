package com.wf.wflib.physics.api;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Step inputs, written by {@link PhysicsBody#capture} on the tick thread, read by the island task. */
public final class BodyState {

    /** Coulomb friction of hull contacts. */
    public static final float FRICTION = 0.5f;
    /** Landing-gear foot box half size, blocks. */
    public static final float FOOT = 0.15f;
    /** Hull up.y above which a sprung belly is frictionless. cos 45 deg; past it the hull falls free. */
    public static final float BELLY_SPIN_UP = 0.707f;

    /** Client prediction: live world, no providers. */
    public boolean predicting;
    /** Island on the tick thread: live world (providers, trace, sync config). */
    public boolean live;

    public double pivotX, pivotY, pivotZ;
    public double comX, comY, comZ;
    public double hullX, hullY, hullZ;
    public final Quaternionf entityRotation = new Quaternionf();
    public final Quaternionf hullRotation = new Quaternionf();
    public final Quaternionf hullLocalRotation = new Quaternionf();
    /** Entity frame, from pivot: hull centre, COM. */
    public final Vector3f hullLocal = new Vector3f();
    public final Vector3f comLocal = new Vector3f();
    public final Vector3f hullExtents = new Vector3f();

    public float inverseMass;
    public final Matrix3f inverseInertia = new Matrix3f();
    public final Vector3f velocity = new Vector3f();
    public final Vector3f spin = new Vector3f();
    public final Vector3f acceleration = new Vector3f();
    public float spinRetention;
    public float maxSpin;
    /** Speculative reach, blocks. */
    public float margin;
    public float restitution;
    public boolean sprung;
    /** Rolls on landing gear (unsprung). */
    public boolean gear;
    /** Weight carried (afloat): frictionless world contacts. */
    public boolean carried;
    public AABB gather;

    /** Active OBBs (vehicle-vehicle shapes), hull frame: centre (3), rotation (4), extents (3) each. */
    public float[] pieceFrames = new float[0];
    public int pieceCount;
    /** Wheel boxes as world shapes, same layout. */
    public float[] wheelFrames = new float[0];
    public int wheelCount;
    /** Landing gear points, hull frame (x, y, z): {@link #FOOT} boxes resting on each. */
    public float[] feet = new float[0];
    public int footCount;

    /** Coupled this tick; null => free. */
    @Nullable
    public Coupling coupling;
    /** World points: coupling, hitch, axle patch. */
    public final double[] couplingPoints = new double[9];
    public final Vector3f couplingLateral = new Vector3f();
    /** Rope length, < 0 ball; articulation (rad, +y) and its stop (NaN none); fifth-wheel roll (rad, NaN free). */
    public float couplingLength, couplingYaw, couplingYawLimit, couplingRoll;
    public final Vector3f couplingRollAxis = new Vector3f();
    public float couplingGrip;

    /** World binding: pinned (async) or live sections. */
    public SectionSource sections;
    public ChunkCollisionCache.Cursor cursor;

    /** Engine-owned; bodies never touch it. */
    public Object engine;

}
