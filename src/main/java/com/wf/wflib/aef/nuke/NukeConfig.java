package com.wf.wflib.aef.nuke;

public final class NukeConfig {

    /** ms/tick the MK5 spends on chunk admission + publication. */
    public static long mk5 = 50;

    /** 0 = auto. */
    public static int blastChunksInFlight = 0;

    /** 1 = binary carve; 2 = per-cell damage accumulation. NTM-next's 0 (batched) not ported. */
    public static int explosionAlgorithm = 2;

    /** Bomb pool workers; {@code <= 0} => processors + value. */
    public static int maxThreads = -1;

    private NukeConfig() {
    }

    public static int chunksInFlight() {
        int configured = blastChunksInFlight;
        return configured > 0 ? configured : Math.max(64, Runtime.getRuntime().availableProcessors() * 8);
    }
}
