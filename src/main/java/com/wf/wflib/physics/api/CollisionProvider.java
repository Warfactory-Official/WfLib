package com.wf.wflib.physics.api;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Collision geometry source for body physics (sub-levels, contraptions, addon geometry): oriented boxes, solid like
 * blocks. Provider itself: no per-vehicle state. Session: per-vehicle-per-tick mutable state. Tick thread throughout.
 */
public interface CollisionProvider {

    /**
     * @param hullBounds the step's reach this tick.
     * @return null skips this provider for this vehicle this tick.
     */
    @Nullable
    Session begin(PhysicsBody body, AABB hullBounds);

    interface Session {

        /** Every box meeting bounds, exact: contacts are taken on them. {@code out} shared across sessions; append only. */
        void collectBoxes(AABB bounds, OrientedBoxes out);

        /** Box {@code box} (0 = this session's first) bore load this tick. */
        default void touched(int box) {}

        default void end() {}

    }

}
