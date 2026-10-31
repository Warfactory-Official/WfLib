package com.wf.wflib.sight.api.client;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * ADS correction {@code T(-c * ads) slerp(I, q, ads)}, model units. Eye frame (model space; -Z = line of sight, +Y =
 * up) => {@code q} turns it onto the view axis upright, {@code c} = its origin after {@code q}: eye at the camera.
 * No frame => {@code q = Rz(cant)}, {@code c = 0}. Not thread-safe (scratch).
 */
public final class SightAlign {
    private final Quaternionf qa = new Quaternionf();
    private final Quaternionf qb = new Quaternionf();
    private final Vector3f pa = new Vector3f();
    private final Vector3f pb = new Vector3f();
    /** Rotation about the eye and eye origin after it, this solve. */
    public final Quaternionf q = new Quaternionf();
    public final Vector3f c = new Vector3f();

    public SightAlign solve(@Nullable Matrix4fc frame, float cantDeg) {
        rotation(frame, cantDeg, q);
        origin(frame, c);
        q.transform(c);
        return this;
    }

    /** Active {@code w} / previous {@code 1 - w}: rotations nlerped, origins weighted over present frames. */
    public SightAlign blend(@Nullable Matrix4fc frameA, float cantA, @Nullable Matrix4fc frameB, float cantB,
                            float w) {
        if (w >= 1) {
            return solve(frameA, cantA);
        }
        rotation(frameA, cantA, qa);
        rotation(frameB, cantB, qb);
        qb.nlerp(qa, w, q);
        float wa = frameA != null ? w : 0;
        float wb = frameB != null ? 1 - w : 0;
        c.zero();
        if (wa + wb > 0) {
            origin(frameA, pa);
            origin(frameB, pb);
            q.transform(c.set(pa).mul(wa).fma(wb, pb).div(wa + wb));
        }
        return this;
    }

    private static void rotation(@Nullable Matrix4fc frame, float cantDeg, Quaternionf out) {
        if (frame != null) {
            frame.getUnnormalizedRotation(out).normalize().conjugate();
        } else {
            out.rotationZ(cantDeg * ((float) Math.PI / 180F));
        }
    }

    private static void origin(@Nullable Matrix4fc frame, Vector3f out) {
        if (frame != null) {
            frame.getTranslation(out);
        } else {
            out.zero();
        }
    }

    /** {@code out = T(-c * ads) slerp(I, q, ads)}. */
    public Matrix4f apply(float ads, Matrix4f out) {
        qa.identity().slerp(q, ads);
        return out.translation(-c.x * ads, -c.y * ads, -c.z * ads).rotate(qa);
    }
}
