package com.wf.wflib.physics.api;

import org.joml.Vector3f;

/** A joint to a leading body; geometry captured into {@link BodyState}, impulses warm-start across steps. */
public interface Coupling {

    PhysicsBody tractor();

    /** Axle row (grip) present. */
    boolean axle();

    /** Ball x/y/z (rope: x); read as warm start, written after the solve. */
    Vector3f impulse();

    /** Extra rope legs sharing the hitch (bridle); warm start per leg, length = {@link BodyState#ropeCount}. */
    default Vector3f[] ropeImpulses() {
        return NO_ROPES;
    }

    Vector3f[] NO_ROPES = new Vector3f[0];

    float yawImpulse();

    float axleImpulse();

    float rollImpulse();

    void impulses(float yaw, float axle, float roll);

}
