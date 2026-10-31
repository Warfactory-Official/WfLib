package com.wf.wflib.physics.api;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A simulated body. Tick thread unless noted; island-task calls read only what {@link #capture} froze.
 * Flush: id order, carriers ({@link DeckView#carrier}) before riders.
 */
public interface PhysicsBody {

    /** Sort key, pair-cache key. */
    int getId();

    Level level();

    boolean isRemoved();

    /** False => skipped this step (no hull yet). */
    boolean steppable();

    /** Engine-owned per-body state; one instance per body, never replaced. */
    BodyState bodyState();

    /**
     * Fills {@code s}: pose, mass, velocity, shapes, coupling, gather and world binding ({@link BodyState#sections},
     * {@link BodyState#cursor}). {@link BodyState#predicting} is set by the engine before.
     */
    void capture(BodyState s, boolean async);

    HullPiece mainPiece();

    CompoundBody compound();

    /** Deck of the carrier beneath, frozen at capture. */
    DeckView deck();

    /** Bodies whose bounds meet {@code bounds}; order is part of the step's determinism. */
    void neighbours(AABB bounds, List<PhysicsBody> out);

    /** Bodies coupled behind this one (their {@link BodyState#coupling} names this body). */
    List<? extends PhysicsBody> coupledBehind();

    /** Not registered this tick, inside a stepping body's reach: true => joins this step. */
    boolean wakeForStep();

    /** Hull-vs-hull contact allowed (false: deck, carry or coupling answers the pair). */
    boolean collidesWith(PhysicsBody other);

    /** Live islands only: open provider sessions near {@code bounds}; {@code base[s]} = session s's first box. */
    void providerBoxes(AABB bounds, List<CollisionProvider.Session> sessions, IntArrayList base, OrientedBoxes out);

    /** Island task, sprung bodies: suspension rows over {@code surface}. */
    void springs(SpringRows rows, CellTops surface);

    /** Island task, after the solve: {@code r} complete except {@link StepResult#touched}. */
    void integrate(StepResult r);

    /** Tick thread, flush. */
    void complete(StepResult r);

    /** Island failed: no motion this tick. */
    void hold();

}
