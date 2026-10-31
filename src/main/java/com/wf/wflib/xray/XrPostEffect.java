package com.wf.wflib.xray;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An X-Ray post-process effector file ({@code .ppe}, {@code CPostprocessAnimator::Load}): envelopes for base, add and
 * gray colour (a base value plus r, g, b), gray amount, blur, duality h / v, noise intensity / grain / fps and (v2)
 * colour-map influence. {@link #sample} gives its values at a time, as its {@code Process} adds them onto the
 * identity before the factor lerp.
 */
public final class XrPostEffect {
    /** {@code SPPInfo}: what one effector or the sum of them asks of the screen. */
    public static final class Pp {
        public final float[] base = new float[3];
        public final float[] add = new float[3];
        public final float[] grayColor = new float[3];
        public float gray;
        public float blur;
        public float dualH;
        public float dualV;
        public float noise;
        public float grain;
        public float fps;

        /** X-Ray's {@code pp_identity}: nothing done. */
        public Pp identity() {
            base[0] = base[1] = base[2] = 0.5F;
            add[0] = add[1] = add[2] = 0.0F;
            grayColor[0] = grayColor[1] = grayColor[2] = 0.333F;
            gray = blur = dualH = dualV = noise = 0.0F;
            grain = 1.0F;
            fps = 30.0F;
            return this;
        }
    }

    private final XrEnvelope[] base = new XrEnvelope[3];
    private final XrEnvelope[] add = new XrEnvelope[3];
    private final XrEnvelope[] grayColor = new XrEnvelope[3];
    private final float baseBase;
    private final float addBase;
    private final float grayBase;
    private final XrEnvelope gray;
    private final XrEnvelope blur;
    private final XrEnvelope dualH;
    private final XrEnvelope dualV;
    private final XrEnvelope noise;
    private final XrEnvelope grain;
    private final XrEnvelope fps;
    private final float length;

    private XrPostEffect(ByteBuffer in) {
        int version = in.getInt();
        baseBase = color(in, base);
        addBase = color(in, add);
        grayBase = color(in, grayColor);
        gray = XrEnvelope.read(in);
        blur = XrEnvelope.read(in);
        dualH = XrEnvelope.read(in);
        dualV = XrEnvelope.read(in);
        noise = XrEnvelope.read(in);
        grain = XrEnvelope.read(in);
        fps = XrEnvelope.read(in);
        float len = 0.0F;
        for (XrEnvelope e : new XrEnvelope[] {base[0], base[1], base[2], add[0], add[1], add[2], grayColor[0],
                grayColor[1], grayColor[2], gray, blur, dualH, dualV, noise, grain, fps}) {
            len = Math.max(len, e.length());
        }
        if (version >= 2) {
            len = Math.max(len, XrEnvelope.read(in).length());
        }
        length = len;
    }

    public static XrPostEffect parse(byte[] bytes) {
        return new XrPostEffect(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
    }

    private static float color(ByteBuffer in, XrEnvelope[] rgb) {
        float b = in.getFloat();
        for (int i = 0; i < 3; i++) {
            rgb[i] = XrEnvelope.read(in);
        }
        return b;
    }

    /** Seconds: the longest envelope (one loop when looped). */
    public float length() {
        return length;
    }

    /**
     * Values at {@code t} ({@code m_EffectorParams} after {@code Update} and {@code Process}'s identity add): colours
     * are offsets onto the identity's, noise grain / fps the identity's where the file has no keys.
     */
    public Pp sample(float t, Pp out) {
        out.identity();
        for (int i = 0; i < 3; i++) {
            out.base[i] += base[i].evaluate(t);
            out.add[i] += add[i].evaluate(t);
            out.grayColor[i] += grayColor[i].evaluate(t);
        }
        out.gray = gray.evaluate(t);
        out.blur = blur.evaluate(t);
        out.dualH = dualH.evaluate(t);
        out.dualV = dualV.evaluate(t);
        out.noise = noise.evaluate(t);
        if (!grain.isEmpty()) {
            out.grain = grain.evaluate(t);
        }
        if (!fps.isEmpty()) {
            out.fps = fps.evaluate(t) * 100.0F;
        }
        return out;
    }
}
