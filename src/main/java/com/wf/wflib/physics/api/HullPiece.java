package com.wf.wflib.physics.api;

import com.wf.wflib.geom.api.OBB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One collision piece of a body. Frame of {@link #localCenter}/{@link #localRotation} = body-local. */
public interface HullPiece {

    OBB obb();

    /** Real shape; null => {@link #obb} is the shape. */
    @Nullable
    ConvexHull hull();

    HullTag tag();

    /** Read-only. */
    Vector3f localCenter();

    /** Read-only. */
    Quaternionf localRotation();

    boolean isSolid();

}
