package com.wf.wflib.explosion.api;

import net.minecraft.world.phys.Vec3;

/**
 * An entity that resolves a blast itself. {@link EntityProcessorCross} and {@link EntityProcessorCone} call
 * {@link #blast} once per blast in range and skip their generic damage for it; knockback still applies.
 */
public interface BlastTarget {

    /**
     * @param payload {@link ExplosionAEF#payload()}: the explicit one, else the size-only synthesis
     * @param centre detonation point
     */
    void blast(ExplosionAEF explosion, BlastPayload payload, Vec3 centre);

    /** False: the generic knockback of the entity stage skips this target (it prices the impulse itself). */
    default boolean pushedByBlast() {
        return true;
    }
}
