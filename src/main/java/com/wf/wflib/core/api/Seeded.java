package com.wf.wflib.core.api;

import java.util.UUID;

/** Stateless seeded uniforms; bit-identical on client and server. */
public final class Seeded {
    private Seeded() {
    }

    /** SplitMix64 finaliser. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, 1), 24 bits. */
    public static float unit(long seed, long a, long b) {
        return (mix(seed ^ mix(a * 0x9E3779B97F4A7C15L + b)) >>> 40) * 0x1.0p-24f;
    }

    public static long playerSeed(UUID id) {
        return mix(id.getMostSignificantBits() ^ mix(id.getLeastSignificantBits()));
    }
}
