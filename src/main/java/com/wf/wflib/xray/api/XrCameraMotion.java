package com.wf.wflib.xray.api;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.wf.wflib.xray.XrEnvelope;

/**
 * An X-Ray object motion ({@code .anm}, {@code COMotion}, chunk {@code 0x1100}): position x y z and rotation heading /
 * pitch / bank envelopes, seconds; camera effectors play it as an offset in camera space.
 */
public final class XrCameraMotion {
    private static final int OMOTION = 0x1100;

    /** x, y, z, heading, pitch, bank ({@code EChannelType} order). */
    private final XrEnvelope[] channels = new XrEnvelope[6];
    private final float start;
    private final float end;

    private XrCameraMotion(ByteBuffer in) {
        while (in.get() != 0) {
            // name, zero-terminated
        }
        int frameStart = in.getInt();
        int frameEnd = in.getInt();
        float fps = in.getFloat();
        start = frameStart / fps;
        end = frameEnd / fps;
        int version = in.getShort() & 0xFFFF;
        if (version == 4) {
            // x y z, then pitch, heading, bank
            for (int ch : new int[] {0, 1, 2, 4, 3, 5}) {
                channels[ch] = XrEnvelope.read(in);
            }
        } else if (version == 5) {
            for (int ch = 0; ch < 6; ch++) {
                channels[ch] = XrEnvelope.read(in);
            }
        } else {
            throw new IllegalArgumentException("object motion version " + version + " not read");
        }
    }

    public static XrCameraMotion parse(byte[] bytes) {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        while (in.remaining() >= 8) {
            int id = in.getInt() & 0x7FFFFFFF;
            int size = in.getInt();
            if (id == OMOTION) {
                return new XrCameraMotion(in.slice(in.position(), size).order(ByteOrder.LITTLE_ENDIAN));
            }
            in.position(in.position() + size);
        }
        throw new IllegalArgumentException("no object motion chunk");
    }

    /** Seconds the motion runs. */
    public float length() {
        return end - start;
    }

    /** {@code out} = x, y, z (m), heading, pitch, bank (radians) at {@code t} seconds in. */
    public float[] sample(float t, float[] out) {
        for (int ch = 0; ch < 6; ch++) {
            out[ch] = channels[ch].evaluate(start + t);
        }
        return out;
    }
}
