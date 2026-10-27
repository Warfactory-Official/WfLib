package com.wf.wflib.radiation;

/**
 * Calls {@link RadiationSystemNT#sanitize} rather than re-deriving it, so it stays the reference the vector arm
 * is held to rather than a second transcription.
 */
final class UniformDecayScalar implements UniformDecay {

    @Override
    public void decay(double[] uniform, int off, int n, double retention, double minBound, double fogRad,
                      double destroyRad, long[] out) {
        long changed = 0L, zeroed = 0L, fog = 0L, destroy = 0L;
        for (int i = 0; i < n; i++) {
            double prev = uniform[off + i];
            double next = RadiationSystemNT.sanitize(prev * retention, minBound);
            if (next != prev) {
                uniform[off + i] = next;
                changed |= 1L << i;
            }
            if (next == 0.0D) {
                zeroed |= 1L << i;
            } else {
                if (next > fogRad) fog |= 1L << i;
                if (next >= destroyRad) destroy |= 1L << i;
            }
        }
        out[CHANGED] = changed;
        out[ZEROED] = zeroed;
        out[FOG] = fog;
        out[DESTROY] = destroy;
    }
}
