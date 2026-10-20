package com.wf.wflib.client.sight;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Collimated reticle, view space (eye at origin, -Z forward): image at infinity along the sight axis {@code a}, seen
 * through a window disc in a plane normal to {@code a}. Pure.
 */
public final class ReticleMath {
    public static final double MOA_RAD = Math.PI / (180 * 60);
    /** Image drawn >= this wide, window px per 1080 px of height: 2 MOA dot at 1x = 0.5 px. */
    public static final float MIN_PX_1080 = 16;
    /** Eased ADS where the reticle starts / ends fading in. */
    public static final float FADE_FROM = 0.5F, FADE_TO = 0.9F;

    private ReticleMath() {
    }

    /** Image width, window px: {@code tan(S/2) / tan(fov/2) * h}; {@code fovDeg} vertical, zoom included. */
    public static float imagePx(float halfTan, float fovDeg, float heightPx) {
        return halfTan / (float) Math.tan(Math.toRadians(fovDeg) / 2) * heightPx;
    }

    /** Tangent of the image half-width: {@code sizeMoa} wide, raised to {@link #MIN_PX_1080}. */
    public static float halfTan(float sizeMoa, float fovDeg) {
        return Math.max((float) Math.tan(sizeMoa * MOA_RAD / 2),
                MIN_PX_1080 / 1080 * (float) Math.tan(Math.toRadians(fovDeg) / 2));
    }

    /**
     * Image coordinates {@code {u, v}} (top-left origin) of the eye ray through view point {@code q}: tangent of its
     * angle from {@code a} along image axes {@code x} / {@code y} (unit, normal to {@code a}). Plane normal to {@code a}
     * => {@code q.a} constant => linear over the plane (interpolation exact).
     */
    public static void uv(Vector3fc q, Vector3fc a, Vector3fc x, Vector3fc y, float halfTan, float[] out) {
        float d = q.dot(a);
        out[0] = 0.5F + q.dot(x) / d / (2 * halfTan);
        out[1] = 0.5F - q.dot(y) / d / (2 * halfTan);
    }

    /** Eased ADS -> reticle alpha, smoothstep over {@link #FADE_FROM}..{@link #FADE_TO}. */
    public static float fade(float adsEased) {
        float t = Math.clamp((adsEased - FADE_FROM) / (FADE_TO - FADE_FROM), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** No lens node: window radius about the axis ({@code x = y = 0}) inscribed in the sight bounds; <= 0 => off. */
    public static float inscribed(Vector3fc lo, Vector3fc hi) {
        return Math.min(Math.min(-lo.x(), hi.x()), Math.min(-lo.y(), hi.y()));
    }

    /**
     * Sight axis {@code a} through the eye meets the window (centre {@code c}, semi-axes {@code u}, {@code v}: ellipse
     * in the plane normal to {@code a})? False => reticle centre clipped (eye off the sight line).
     */
    public static boolean centreInWindow(Vector3fc c, Vector3fc u, Vector3fc v, Vector3fc a, Vector3f tmp) {
        float t = c.dot(a);
        if (!(t > 0)) {
            return false;
        }
        a.mul(t, tmp).sub(c);
        // tmp = p - c in the plane: coordinates on (u, v) by the 2x2 Gram system.
        float uu = u.dot(u), uv = u.dot(v), vv = v.dot(v), pu = tmp.dot(u), pv = tmp.dot(v);
        float det = uu * vv - uv * uv;
        if (!(det > 0)) {
            return false;
        }
        float s = (pu * vv - pv * uv) / det, r = (pv * uu - pu * uv) / det;
        return s * s + r * r <= 1;
    }
}
