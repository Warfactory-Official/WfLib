package com.wf.wflib.core.api;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/** Direct-memory access helper backing the Torex renderer's fast vertex path. */
public final class UnsafeHolder {
    public static final Unsafe U = getUnsafe();
    static final long IA_BASE = U.arrayBaseOffset(int[].class);
    static final int IA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(int[].class));
    static final long JA_BASE = U.arrayBaseOffset(long[].class);
    static final int JA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(long[].class));
    static final long BA_BASE = U.arrayBaseOffset(byte[].class);
    static final int BA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(byte[].class));
    static final long ZA_BASE = U.arrayBaseOffset(boolean[].class);
    static final int ZA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(boolean[].class));
    static final long SA_BASE = U.arrayBaseOffset(short[].class);
    static final int SA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(short[].class));
    static final long CA_BASE = U.arrayBaseOffset(char[].class);
    static final int CA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(char[].class));
    static final long FA_BASE = U.arrayBaseOffset(float[].class);
    static final int FA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(float[].class));
    static final long DA_BASE = U.arrayBaseOffset(double[].class);
    static final int DA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(double[].class));
    static final long RA_BASE = U.arrayBaseOffset(Object[].class);
    static final int RA_SHIFT = Integer.numberOfTrailingZeros(U.arrayIndexScale(Object[].class));

    private UnsafeHolder() {
    }

    private static Unsafe getUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not obtain sun.misc.Unsafe", e);
        }
    }

    static long offInt(int i) {
        return ((long) i << IA_SHIFT) + IA_BASE;
    }

    static long offLong(int i) {
        return ((long) i << JA_SHIFT) + JA_BASE;
    }

    static long offByte(int i) {
        return ((long) i << BA_SHIFT) + BA_BASE;
    }

    static long offBoolean(int i) {
        return ((long) i << ZA_SHIFT) + ZA_BASE;
    }

    static long offShort(int i) {
        return ((long) i << SA_SHIFT) + SA_BASE;
    }

    static long offChar(int i) {
        return ((long) i << CA_SHIFT) + CA_BASE;
    }

    static long offFloat(int i) {
        return ((long) i << FA_SHIFT) + FA_BASE;
    }

    static long offDouble(int i) {
        return ((long) i << DA_SHIFT) + DA_BASE;
    }

    static long offReference(int i) {
        return ((long) i << RA_SHIFT) + RA_BASE;
    }

    static <T> T allocateInstance(Class<? extends T> clz) {
        try {
            //noinspection unchecked
            return (T) U.allocateInstance(clz);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
