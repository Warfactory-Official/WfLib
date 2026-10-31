package com.wf.wflib.armor.api.hull;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Hit surface of a {@link HullBody} with per-node rest frames. */
public interface HullMesh {

    /** One surface crossing. */
    interface Strike {
        int node();

        Vec3 point();

        /** Struck surface, world frame, either sign. */
        Vector3f normal();

        /** mm of a real plate at the struck face, 0 = none. */
        float armor();
    }

    /** Nearest live surface along {@code from -> to}, or null. */
    @Nullable
    Strike surface(Vec3 from, Vec3 to);

    /** Part owning {@code node}, null = hull. */
    @Nullable
    String partOf(int node);

    /** World point -> model frame with {@code node}'s part at rest. */
    Vector3f restPoint(int node, Vec3 world);

    /** World direction -> model frame with {@code node}'s part at rest. */
    Vector3f restDirection(int node, Vector3f world);

    /** Inverse of {@link #restDirection}. */
    Vector3f worldDirection(int node, Vector3f rest);
}
