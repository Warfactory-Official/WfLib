package com.wf.wflib.explosion.nuke;

public final class NukeConfig {

    /** ms/tick the MK5 spends on chunk admission + publication. */
    public static final long mk5 = 50;

    /** 1 = binary carve; 2 = per-cell damage accumulation. NTM-next's 0 (batched) not ported. */
    public static final int explosionAlgorithm = 2;

    /** Bomb pool workers; {@code <= 0} => processors + value. */
    public static final int maxThreads = -1;

    private NukeConfig() {
    }

    public static int chunksInFlight() {
        return Math.max(64, Runtime.getRuntime().availableProcessors() * 8);
    }
}
