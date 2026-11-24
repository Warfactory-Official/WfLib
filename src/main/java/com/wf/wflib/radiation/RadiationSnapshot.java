package com.wf.wflib.radiation;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Immutable per-world copy of the field read by region workers; built at the end of a sweep, published by a
 * volatile write. Read: O(1), allocation-free.
 */
final class RadiationSnapshot {

    /** Returned once per section per snapshot when a rebuild must be requested; the caller maps it to 0. */
    static final double REBUILD = Double.NaN;

    private final Long2IntOpenHashMap coordToId;
    private final long[] cks;
    private final long[] kinds;
    private final double[] uniform;
    private final short[][] pocketData;
    private final double[][] multi;
    private final Object[] refs;
    private final int sections, words;
    private final AtomicLongArray requested;

    RadiationSnapshot(Long2IntOpenHashMap coordToId, long[] cks, long[] kinds, double[] uniform,
                      short[][] pocketData, double[][] multi, Object[] refs, int sections, int words) {
        this.coordToId = coordToId;
        this.cks = cks;
        this.kinds = kinds;
        this.uniform = uniform;
        this.pocketData = pocketData;
        this.multi = multi;
        this.refs = refs;
        this.sections = sections;
        this.words = words;
        this.requested = new AtomicLongArray((uniform.length + 63) >>> 6);
    }

    /** Sweep joined: field and structure stable. Unchanged {@code SectionRef}s share {@code prev}'s pocket map. */
    static RadiationSnapshot of(WorldRadiationData d, RadiationSnapshot prev) {
        int n = d.nextId, s = d.sectionsPerChunk, w = d.wordsPerChunk;
        int total = n * s;
        short[][] pd = new short[total][];
        double[][] multi = new double[total][];
        Object[] refs = new Object[total];
        WorldRadiationData.SectionRef[] secs = d.complexSecs;
        for (int i = 0; i < total; i++) {
            WorldRadiationData.SectionRef ref = secs[i];
            if (ref == null) continue;
            refs[i] = ref;
            if (ref.pocketCount <= 0) continue;
            pd[i] = prev != null && i < prev.refs.length && prev.refs[i] == ref
                    ? prev.pocketData[i] : ref.pocketData.clone();
            if (ref instanceof WorldRadiationData.MultiSectionRef m) multi[i] = m.data.clone();
        }
        return new RadiationSnapshot(d.coordToId.clone(), Arrays.copyOf(d.cks, n),
                Arrays.copyOf(d.chunkKinds, n * w), Arrays.copyOf(d.uniformRads, total), pd, multi, refs, s, w);
    }

    /** {@code posLong} is a {@code BlockPos.asLong}; unknown chunk => 0. */
    double read(long ck, int slot, long posLong) {
        int id = coordToId.get(ck);
        if (id < 0 || id >= cks.length || cks[id] != ck) return 0.0D;
        int secIdx = id * sections + slot;
        int kind = (int) ((kinds[words == 1 ? id : id * words + (slot >>> 5)] >>> ((slot & 31) << 1)) & 3);
        if (kind == RadiationSystemNT.KIND_UNI) return uniform[secIdx];
        if (kind == RadiationSystemNT.KIND_NONE) return rebuild(secIdx);
        short[] pd = pocketData[secIdx];
        if (pd == null) return rebuild(secIdx);
        int pocket = pd[SectionKeys.blockPosToLocal(posLong)];
        if (pocket < 0) return rebuild(secIdx);
        if (kind == RadiationSystemNT.KIND_SINGLE) return uniform[secIdx];
        double[] m = multi[secIdx];
        return m == null ? rebuild(secIdx) : m[pocket << 1];
    }

    private double rebuild(int secIdx) {
        int word = secIdx >>> 6;
        long bit = 1L << secIdx;
        while (true) {
            long old = requested.get(word);
            if ((old & bit) != 0L) return 0.0D;
            if (requested.compareAndSet(word, old, old | bit)) return REBUILD;
        }
    }
}
