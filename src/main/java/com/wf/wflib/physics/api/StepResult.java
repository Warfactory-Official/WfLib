package com.wf.wflib.physics.api;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** One body's step outcome: island task -> {@link PhysicsBody#integrate}, flush -> {@link PhysicsBody#complete}. */
public final class StepResult {

    /** Island failed (held): no motion. */
    public boolean failed;
    /** Joined without its own tick ({@link PhysicsBody#wakeForStep}). */
    public boolean woken;
    public boolean grounded;
    /** Some world contact pushed. */
    public boolean belly;
    /** Solver body's pose delta, velocity, spin; valid during {@link PhysicsBody#integrate} only. */
    public Vector3f dp, velocity, spin;
    public Quaternionf dq;
    /** Spring load, upward-contact load (impulse units). */
    public float wheelLoad, bellyLoad;
    /**
     * Per suspension row slot (the {@code SpringRows} order of this step), first {@link #springRows} valid: the solver's
     * last evaluation of {@code compression - ratio * (dp + dq r - r) . axis}, blocks, positive = compressed, measured
     * from the step-start ground sample; NaN for ungrounded rows. Valid from {@link PhysicsBody#integrate} on.
     */
    public float[] springCompression = new float[0];
    public int springRows;
    /** Load-weighted spring anchor height; NaN none. */
    public double groundY;
    /** |dv| the contacts cost beyond free flight, blocks/tick. */
    public double velocityDiff;
    /** Pushing contacts' impulse sum and its weighted centroid (world); weight 0 => none. */
    public double impactWeight, impactX, impactY, impactZ;
    /** Cells under non-support contacts; fastest approach into them. */
    public final LongArrayList blockingCells = new LongArrayList();
    public double ramSpeed;
    /** Hull-touching neighbours (pairs with normal impulse). */
    public final List<PhysicsBody> touched = new ArrayList<>();

}
