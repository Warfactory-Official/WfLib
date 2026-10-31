package com.wf.wflib.physics.api;

import org.joml.Vector3f;

/** A joint to a leading body; geometry captured into {@link BodyState}, impulses warm-start across steps. */
public interface Coupling {

    PhysicsBody tractor();

    /** Axle row (grip) present. */
    boolean axle();

    /** Ball x/y/z (rope: x); read as warm start, written after the solve. */
    Vector3f impulse();

    float yawImpulse();

    float axleImpulse();

    float rollImpulse();

    void impulses(float yaw, float axle, float roll);

}
