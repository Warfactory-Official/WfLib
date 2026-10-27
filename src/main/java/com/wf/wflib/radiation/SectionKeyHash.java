package com.wf.wflib.radiation;

import it.unimi.dsi.fastutil.longs.LongHash;

/**
 * Designed for x22 | z22 | y20 SectionPos encoding.
 */
public final class SectionKeyHash {
    public static final LongHash.Strategy STRATEGY = new LongHash.Strategy() {
        @Override
        public int hashCode(long k) {
            return hash(k);
        }

        @Override
        public boolean equals(long a, long b) {
            return a == b;
        }
    };

    private SectionKeyHash() {
    }

    // java.util.SplittableRandom.mix32
    // 32 high bits of Stafford variant 4 mix64
    public static int hash(long z) {
        z = (z ^ (z >>> 33)) * 0x62a9d9ed799705f5L;
        return (int) (((z ^ (z >>> 28)) * 0xcb24d0a5c88c35b3L) >>> 32);
    }
}
