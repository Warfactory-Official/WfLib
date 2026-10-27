package com.wf.wflib.radiation;

import com.wf.wflib.radiation.SectionKeyHash;
import it.unimi.dsi.fastutil.HashCommon;

import java.util.Arrays;

/**
 * One chunk's staged writes for the next step, keyed by {@link WorldRadiationData#editKey}.
 *
 * <p>The three kinds compose order-free except between sets: the winning set by sequence replaces the base,
 * then every source relaxes against that base and every plain add lands on top. The backend resolves a batch
 * identically.
 */
final class EditTable {
    static final float LOAD_FACTOR = 0.6f;
    static final byte HAS_SET = 1;

    static final int NO_SOURCE = -1;

    long[] keys, setSeq;
    double[] addAcc, setVal;
    int[] stamps, slots;
    byte[] flags;
    int touchedSyMask, mask, size, epoch, slotSize;

    /**
     * Sources at a key, as an intrusive singly-linked list into the side arrays below. A list rather than an
     * accumulated pair: each source gates on the density it acts on, so two saturations cannot be pre-summed.
     */
    int[] srcHead, srcTail;
    double[] srcEmission, srcSaturation;
    int[] srcNext;
    int srcCount;

    EditTable(int cap) {
        keys = new long[cap];
        addAcc = new double[cap];
        setVal = new double[cap];
        setSeq = new long[cap];
        flags = new byte[cap];
        stamps = new int[cap];
        srcHead = new int[cap];
        srcTail = new int[cap];
        srcEmission = new double[16];
        srcSaturation = new double[16];
        srcNext = new int[16];
        slots = new int[16];
        mask = cap - 1;
        epoch = 1;
        touchedSyMask = 0;
    }

    boolean isEmpty() {
        return slotSize == 0;
    }

    void clear() {
        size = 0;
        slotSize = 0;
        srcCount = 0;
        int e = epoch + 1;
        if (e == 0) {
            Arrays.fill(stamps, 0);
            e = 1;
        }
        epoch = e;
        touchedSyMask = 0;
    }

    void ensureCapacityForAdd() {
        if (size + 1 <= (int) (keys.length * LOAD_FACTOR)) return;
        rehash(HashCommon.nextPowerOfTwo(keys.length + (keys.length >>> 1) + 16));
    }

    int findOrInsert(long k) {
        ensureCapacityForAdd();
        int pos = SectionKeyHash.hash(k) & mask;
        while (true) {
            if (stamps[pos] != epoch) {
                stamps[pos] = epoch;
                keys[pos] = k;
                addAcc[pos] = 0.0d;
                setVal[pos] = 0.0d;
                setSeq[pos] = 0L;
                flags[pos] = 0;
                srcHead[pos] = NO_SOURCE;
                srcTail[pos] = NO_SOURCE;
                size++;
                int i = slotSize;
                if (i == slots.length) slots = Arrays.copyOf(slots, slots.length + (slots.length >>> 1) + 16);
                slots[i] = pos;
                slotSize = i + 1;
                return pos;
            }
            if (keys[pos] == k) return pos;
            pos = (pos + 1) & mask;
        }
    }

    void putSet(long k, double v, long seq, int slot) {
        int pos = findOrInsert(k);
        flags[pos] |= HAS_SET;
        setVal[pos] = v;
        setSeq[pos] = seq;
        touchedSyMask |= 1 << slot;
    }

    void addTo(long k, double dv, int slot) {
        int pos = findOrInsert(k);
        addAcc[pos] += dv;
        touchedSyMask |= 1 << slot;
    }

    /** Appends at the tail, so the sums run in the order the sources were staged, as the backend's do. */
    void addSource(long k, double emission, double saturation, int slot) {
        int pos = findOrInsert(k);
        if (srcCount == srcEmission.length) {
            int n = srcCount + (srcCount >>> 1) + 16;
            srcEmission = Arrays.copyOf(srcEmission, n);
            srcSaturation = Arrays.copyOf(srcSaturation, n);
            srcNext = Arrays.copyOf(srcNext, n);
        }
        int at = srcCount++;
        srcEmission[at] = emission;
        srcSaturation[at] = saturation;
        srcNext[at] = NO_SOURCE;
        int tail = srcTail[pos];
        if (tail == NO_SOURCE) srcHead[pos] = at;
        else srcNext[tail] = at;
        srcTail[pos] = at;
        touchedSyMask |= 1 << slot;
    }

    void rehash(int newCap) {
        long[] newKeys = new long[newCap];
        double[] newAdd = new double[newCap];
        double[] newSetV = new double[newCap];
        long[] newSetS = new long[newCap];
        byte[] newFlags = new byte[newCap];
        int[] newStamps = new int[newCap];
        int[] newSrcHead = new int[newCap];
        int[] newSrcTail = new int[newCap];

        int newMask = newCap - 1;
        int[] newSlots = new int[Math.max(slots.length, slotSize)];
        int ns = 0;

        int e = epoch;
        for (int i = 0; i < slotSize; i++) {
            int oldPos = slots[i];
            if (stamps[oldPos] != e) continue;
            long k = keys[oldPos];

            int pos = SectionKeyHash.hash(k) & newMask;
            while (newStamps[pos] == e) pos = (pos + 1) & newMask;

            newStamps[pos] = e;
            newKeys[pos] = k;
            newAdd[pos] = addAcc[oldPos];
            newSetV[pos] = setVal[oldPos];
            newSetS[pos] = setSeq[oldPos];
            newFlags[pos] = flags[oldPos];
            newSrcHead[pos] = srcHead[oldPos];
            newSrcTail[pos] = srcTail[oldPos];
            newSlots[ns++] = pos;
        }

        keys = newKeys;
        addAcc = newAdd;
        setVal = newSetV;
        setSeq = newSetS;
        flags = newFlags;
        stamps = newStamps;
        srcHead = newSrcHead;
        srcTail = newSrcTail;
        mask = newMask;
        slots = newSlots;
        slotSize = ns;
        size = ns;
    }
}
