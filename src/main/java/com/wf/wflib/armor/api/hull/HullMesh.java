package com.wf.wflib.armor.api.hull;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/** Hit surface of a {@link HullBody} with per-node rest frames. */
public interface HullMesh {

    /** One surface crossing. */
    interface Strike {
        int node();

        Vec3 point();

        /** Struck surface, world frame, either sign. */
        Vector3f normal();

        /** {@link HullProfile#plates} id bound to the struck face, null = unbound. */
        @Nullable
        String plate();
    }

    /** A {@link Strike} in a walk along a line. */
    interface Crossing extends Strike {
        /** The line goes into the volume the surface bounds; false = out of it. */
        boolean entering();
    }

    /** At most this many crossings per {@link #surfaces} query. */
    int MAX_CROSSINGS = 16;

    /** {@link #surfaces} step past a found surface, m. */
    double CROSSING_STEP = 1.0e-3;

    /** Nearest live surface along {@code from -> to}, or null. */
    @Nullable
    Strike surface(Vec3 from, Vec3 to);

    /**
     * Every live surface along {@code from -> to}, nearest first, at most {@link #MAX_CROSSINGS}; appended to
     * {@code out} (caller clears and reuses it). No closed-mesh requirement: ordering only. Faces coincident within
     * {@link #CROSSING_STEP} of a found one may be skipped.
     * <p>The default repeats {@link #surface} from just past each hit and alternates {@code entering} from true;
     * meshes with winding override both.
     *
     * @return crossings appended
     */
    default int surfaces(Vec3 from, Vec3 to, List<Crossing> out) {
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length <= CROSSING_STEP) {
            return 0;
        }
        Vec3 unit = line.scale(1.0 / length);
        Vec3 at = from;
        int n = 0;
        while (n < MAX_CROSSINGS) {
            Strike s = surface(at, to);
            if (s == null) {
                break;
            }
            out.add(new Ordered(s.node(), s.point(), s.normal(), s.plate(), n % 2 == 0));
            n++;
            at = s.point().add(unit.scale(CROSSING_STEP));
            if (at.subtract(from).dot(unit) >= length) {
                break;
            }
        }
        return n;
    }

    /** Plain {@link Crossing}. */
    record Ordered(int node, Vec3 point, Vector3f normal, @Nullable String plate, boolean entering)
            implements Crossing {
    }

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
