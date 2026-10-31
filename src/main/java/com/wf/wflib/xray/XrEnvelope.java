package com.wf.wflib.xray;

import java.nio.ByteBuffer;

/**
 * An X-Ray envelope ({@code CEnvelope}, LightWave's): keys with TCB / Hermite / Bezier / linear / stepped shapes and
 * pre / post behaviours, as {@code .ppe} and {@code .anm} store them ({@code Load_2}). Time in seconds.
 */
public final class XrEnvelope {
    static final int TCB = 0;
    static final int HERM = 1;
    static final int BEZI = 2;
    static final int LINE = 3;
    static final int STEP = 4;
    static final int BEZ2 = 5;
    static final int RESET = 0;
    static final int CONSTANT = 1;
    static final int REPEAT = 2;
    static final int OSCILLATE = 3;
    static final int OFFSET = 4;
    static final int LINEAR = 5;

    record Key(float value, float time, int shape, float tension, float continuity, float bias, float[] param) {
    }

    private final Key[] keys;
    private final int pre;
    private final int post;

    XrEnvelope(Key[] keys, int pre, int post) {
        this.keys = keys;
        this.pre = pre;
        this.post = post;
    }

    /** {@code CEnvelope::Load_2}; {@code in} little-endian. */
    public static XrEnvelope read(ByteBuffer in) {
        int pre = in.get() & 0xFF;
        int post = in.get() & 0xFF;
        Key[] keys = new Key[in.getShort() & 0xFFFF];
        for (int i = 0; i < keys.length; i++) {
            float value = in.getFloat();
            float time = in.getFloat();
            int shape = in.get() & 0xFF;
            float[] q = new float[7];
            if (shape != STEP) {
                for (int k = 0; k < q.length; k++) {
                    q[k] = -32.0F + 64.0F * (in.getShort() & 0xFFFF) / 65535.0F;
                }
            }
            keys[i] = new Key(value, time, shape, q[0], q[1], q[2], new float[] {q[3], q[4], q[5], q[6]});
        }
        return new XrEnvelope(keys, pre, post);
    }

    public boolean isEmpty() {
        return keys.length == 0;
    }

    /** Last key's time minus the first's. */
    public float length() {
        return keys.length == 0 ? 0.0F : keys[keys.length - 1].time - keys[0].time;
    }

    /** {@code evalEnvelope} (xrEngine interp.cpp). */
    public float evaluate(float time) {
        if (keys.length == 0) {
            return 0.0F;
        }
        if (keys.length == 1) {
            return keys[0].value;
        }
        Key skey = keys[0];
        Key ekey = keys[keys.length - 1];
        float offset = 0.0F;
        if (time < skey.time || time > ekey.time) {
            int beh = time < skey.time ? pre : post;
            switch (beh) {
                case RESET -> {
                    return 0.0F;
                }
                case CONSTANT -> {
                    return time < skey.time ? skey.value : ekey.value;
                }
                case REPEAT -> time = range(time, skey.time, ekey.time)[0];
                case OSCILLATE -> {
                    float[] r = range(time, skey.time, ekey.time);
                    time = ((int) r[1]) % 2 != 0 ? ekey.time - skey.time - r[0] : r[0];
                }
                case OFFSET -> {
                    float[] r = range(time, skey.time, ekey.time);
                    time = r[0];
                    offset = r[1] * (ekey.value - skey.value);
                }
                case LINEAR -> {
                    if (time < skey.time) {
                        float out = outgoing(null, skey, keys[1]) / (keys[1].time - skey.time);
                        return out * (time - skey.time) + skey.value;
                    }
                    Key ekeyP = keys[keys.length - 2];
                    float in = incoming(ekeyP, ekey, null) / (ekey.time - ekeyP.time);
                    return in * (time - ekey.time) + ekey.value;
                }
                default -> {
                }
            }
        }
        int k = 0;
        while (k + 2 < keys.length && time > keys[k + 1].time) {
            k++;
        }
        Key key0 = keys[k];
        Key key1 = keys[k + 1];
        Key key0p = k > 0 ? keys[k - 1] : null;
        Key key1n = k + 2 < keys.length ? keys[k + 2] : null;
        if (time == key0.time) {
            return key0.value + offset;
        }
        if (time == key1.time) {
            return key1.value + offset;
        }
        float t = (time - key0.time) / (key1.time - key0.time);
        return switch (key1.shape) {
            case TCB, BEZI, HERM -> {
                float out = outgoing(key0p, key0, key1);
                float in = incoming(key0, key1, key1n);
                float t2 = t * t;
                float t3 = t * t2;
                float h2 = 3.0F * t2 - t3 - t3;
                float h1 = 1.0F - h2;
                float h4 = t3 - t2;
                float h3 = h4 - t2 + t;
                yield h1 * key0.value + h2 * key1.value + h3 * out + h4 * in + offset;
            }
            case BEZ2 -> bez2(key0, key1, time) + offset;
            case LINE -> key0.value + t * (key1.value - key0.value) + offset;
            case STEP -> key0.value + offset;
            default -> offset;
        };
    }

    /** {@code {v in [lo, hi), cycles}}. */
    private static float[] range(float v, float lo, float hi) {
        float r = hi - lo;
        if (r == 0.0F) {
            return new float[] {lo, 0.0F};
        }
        float v2 = lo + v - r * (float) Math.floor(v / r);
        return new float[] {v2, -(int) ((v2 - v) / r + (v2 > v ? 0.5F : -0.5F))};
    }

    private static float bezier(float x0, float x1, float x2, float x3, float t) {
        float c = 3.0F * (x1 - x0);
        float b = 3.0F * (x2 - x1) - c;
        float a = x3 - x0 - c - b;
        return a * t * t * t + b * t * t + c * t + x0;
    }

    private static float bez2(Key key0, Key key1, float time) {
        float x = key0.shape == BEZ2 ? key0.time + key0.param[2] : key0.time + (key1.time - key0.time) / 3.0F;
        float t0 = 0.0F;
        float t1 = 1.0F;
        float t = 0.5F;
        for (int i = 0; i < 64; i++) {
            t = t0 + (t1 - t0) * 0.5F;
            float v = bezier(key0.time, x, key1.time + key1.param[0], key1.time, t);
            if (Math.abs(time - v) <= 0.0001F) {
                break;
            }
            if (v > time) {
                t1 = t;
            } else {
                t0 = t;
            }
        }
        float y = key0.shape == BEZ2 ? key0.value + key0.param[3] : key0.value + key0.param[1] / 3.0F;
        return bezier(key0.value, y, key1.param[1] + key1.value, key1.value, t);
    }

    private static float outgoing(Key key0p, Key key0, Key key1) {
        return switch (key0.shape) {
            case TCB -> {
                float a = (1.0F - key0.tension) * (1.0F + key0.continuity) * (1.0F + key0.bias);
                float b = (1.0F - key0.tension) * (1.0F - key0.continuity) * (1.0F - key0.bias);
                float d = key1.value - key0.value;
                yield key0p != null ? (key1.time - key0.time) / (key1.time - key0p.time)
                        * (a * (key0.value - key0p.value) + b * d) : b * d;
            }
            case LINE -> {
                float d = key1.value - key0.value;
                yield key0p != null ? (key1.time - key0.time) / (key1.time - key0p.time)
                        * (key0.value - key0p.value + d) : d;
            }
            case BEZI, HERM -> key0p != null ? key0.param[1] * (key1.time - key0.time) / (key1.time - key0p.time)
                    : key0.param[1];
            case BEZ2 -> Math.abs(key0.param[2]) > 1.0E-5F ? key0.param[3] * (key1.time - key0.time) / key0.param[2]
                    : key0.param[3] * (key1.time - key0.time) * 1.0E5F;
            default -> 0.0F;
        };
    }

    private static float incoming(Key key0, Key key1, Key key1n) {
        return switch (key1.shape) {
            case LINE -> {
                float d = key1.value - key0.value;
                yield key1n != null ? (key1.time - key0.time) / (key1n.time - key0.time)
                        * (key1n.value - key1.value + d) : d;
            }
            case TCB -> {
                float a = (1.0F - key1.tension) * (1.0F - key1.continuity) * (1.0F + key1.bias);
                float b = (1.0F - key1.tension) * (1.0F + key1.continuity) * (1.0F - key1.bias);
                float d = key1.value - key0.value;
                yield key1n != null ? (key1.time - key0.time) / (key1n.time - key0.time)
                        * (b * (key1n.value - key1.value) + a * d) : a * d;
            }
            case BEZI, HERM -> key1n != null ? key1.param[0] * (key1.time - key0.time) / (key1n.time - key0.time)
                    : key1.param[0];
            case BEZ2 -> Math.abs(key1.param[0]) > 1.0E-5F ? key1.param[1] * (key1.time - key0.time) / key1.param[0]
                    : key1.param[1] * (key1.time - key0.time) * 1.0E5F;
            default -> 0.0F;
        };
    }
}
