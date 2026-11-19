package com.wf.wflib.photo;

/** Wire and storage limits shared by both ends. A client C2S custom payload is capped near 32 KiB, so uploads fragment. */
public final class PhotoLimits {
    public static final int MAX_BYTES = 250_000;
    public static final int UP_CHUNK = 30_000;
    public static final int MAX_CHUNKS = (MAX_BYTES + UP_CHUNK - 1) / UP_CHUNK;
    /** Server-to-client chunk; the S2C cap is far larger. */
    public static final int DOWN_CHUNK = 60_000;
    public static final int MAX_DOWN_CHUNKS = (MAX_BYTES + DOWN_CHUNK - 1) / DOWN_CHUNK;
    public static final int MAX_WIDTH = 1280;
    public static final int MAX_HEIGHT = 720;
    public static final int MIN_SIDE = 64;
    /** Vertical field of view every photo is rendered at, degrees; also what {@code PhotoTakenEvent} reports. */
    public static final float FOV = 70.0f;
    /** Bounds of a shot's vertical field of view (a long zoom to a wide lens), degrees. */
    public static final float MIN_FOV = 4.0f;
    public static final float MAX_FOV = 110.0f;
    /** Width the client scales a capture to before encoding. */
    public static final int CAPTURE_WIDTH = 960;
    public static final float JPEG_QUALITY = 0.82f;
    /** Ticks a shutter token stays valid for the upload. */
    public static final int TOKEN_TICKS = 400;

    private PhotoLimits() {
    }
}
