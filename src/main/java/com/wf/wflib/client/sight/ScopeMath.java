package com.wf.wflib.client.sight;

import org.joml.Vector3fc;

/**
 * PiP scope geometry, view space (eye at origin). Lens disc = unit disc of the scope picture; shadow = circular hole
 * in black, lens radii.
 */
public final class ScopeMath {
    /** Hole radius at perfect eye position: thin ring at the tube edge. */
    public static final float OPEN = 0.94F;
    /** Hole radius lost per unit of relative eye-relief error {@code |d - relief| / relief}. */
    public static final float RELIEF_LOSS = 1.5F;
    /** Lateral eye-box radius, lens radii: eye this far off the axis at the exit pupil => hole centre at the rim. */
    public static final float EYE_BOX = 0.35F;

    private ScopeMath() {
    }

    /** Absolute ADS FOV: user FOV above {@code refFovDeg} => extra divisor down to it (Arma). */
    public static float refZoom(float userFovDeg, float refFovDeg) {
        return Math.max(1, userFovDeg / refFovDeg);
    }

    /**
     * World FOV divisor: ADS FOV {@code min(user, ref) / sightZoom} ({@code refZoom} = {@link #refZoom}) eased by ADS,
     * x RMB hold divisor.
     */
    public static float worldDivisor(float sightZoom, float refZoom, float adsEased, float holdDivisor) {
        return (1 + (sightZoom * refZoom - 1) * adsEased) * holdDivisor;
    }

    /** Mouse-turn divisor: {@code shown} = scope picture drawn => magnification eased by ADS, never below world zoom. */
    public static float turnZoom(float worldZoom, float magnification, float adsEased, boolean shown) {
        return shown ? Math.max(worldZoom, 1 + (magnification - 1) * adsEased) : worldZoom;
    }

    /**
     * Narrowest square-picture FOV vanilla's culling survives: {@code Frustum.offsetToFullyIncludeCameraCube} backs the
     * camera off 4 blocks a step until its 8-block cell fits, no bound => a cell that never fits spins forever.
     * Sufficient for every camera position and direction: corner offsets {@code |c| <= D = 8 sqrt3} =>
     * {@code s_min <= D sqrt(1 + t^2) / t}, {@code s_max >= far - D}; window {@code >= STEP} =>
     * {@code t = D / sqrt(W^2 - D^2)}, {@code W = far - D - STEP}.
     */
    public static float minFovDeg(float far) {
        double w = far - CELL_DIAGONAL - STEP;
        return (float) Math.toDegrees(2 * Math.atan(CELL_DIAGONAL / Math.sqrt(w * w - CELL_DIAGONAL * CELL_DIAGONAL)));
    }

    public static final double CELL_DIAGONAL = 8 * Math.sqrt(3);
    /** Camera step {@code 4 (far + near) / (far - near)}, far >= 8, near 0.05. */
    private static final double STEP = 4.1;

    /** Picture FOV actually rendered: {@code fovDeg} raised to {@link #minFovDeg}. */
    public static float passFovDeg(float fovDeg, float far) {
        return Math.max(fovDeg, minFovDeg(far));
    }

    /** Centre part of a {@code passFovDeg} picture spanning {@code fovDeg}: magnification kept, fewer texels. */
    public static float crop(float fovDeg, float passFovDeg) {
        return (float) (Math.tan(Math.toRadians(fovDeg) / 2) / Math.tan(Math.toRadians(passFovDeg) / 2));
    }

    /**
     * Vertical FOV of the (square) scope picture filling the lens disc.
     *
     * @param lensTan tan of the lens angular radius under the hand projection
     * @param baseFovDeg naked-eye FOV {@code min(configured, adsReferenceFovDeg)}: magnification is relative to it
     */
    public static float fovDeg(float lensTan, float handFovDeg, float baseFovDeg, float zoom) {
        double world = lensTan * Math.tan(Math.toRadians(baseFovDeg) / 2) / Math.tan(Math.toRadians(handFovDeg) / 2);
        return (float) Math.toDegrees(2 * Math.atan(world / zoom));
    }

    /**
     * Shadow hole, lens radii: {@code out = {x, y, radius}} on picture axes {@code x}, {@code y}; {@code radius <= 0}
     * => black. Eye off the axis toward +x => hole moves -x (dark crescent on the eye's side).
     *
     * @param p exit pupil; {@code a} unit optical axis away from the eye; {@code x}, {@code y} unit picture axes;
     *          {@code r} lens radius; {@code relief} exit pupil to lens
     */
    public static void shadow(Vector3fc p, Vector3fc a, Vector3fc x, Vector3fc y, float r, float relief, float[] out) {
        float along = -p.dot(a);
        float lx = -p.x() - along * a.x();
        float ly = -p.y() - along * a.y();
        float lz = -p.z() - along * a.z();
        float box = EYE_BOX * r;
        out[0] = -(lx * x.x() + ly * x.y() + lz * x.z()) / box;
        out[1] = -(lx * y.x() + ly * y.y() + lz * y.z()) / box;
        out[2] = OPEN * Math.max(0, 1 - RELIEF_LOSS * Math.abs(along) / relief);
    }

    /** Magnification ratio per wheel notch. */
    public static final float ZOOM_STEP = 1.15F;

    /** {@code zoom} x {@link #ZOOM_STEP}^{@code notches}, clamped to the listed range. */
    public static float adjust(float zoom, java.util.List<Float> levels, double notches) {
        float lo = java.util.Collections.min(levels), hi = java.util.Collections.max(levels);
        return Math.min(Math.max(zoom * (float) Math.pow(ZOOM_STEP, notches), lo), hi);
    }

    /** Level {@code i} of {@code n} stepped by {@code dir}, wrapping. */
    public static int step(int i, int n, int dir) {
        return Math.floorMod(i + dir, n);
    }
}
