package com.wf.wflib.radiation;


/**
 * Calls {@link RadiationSystemNT#exchangeUniExactXZ} rather than re-deriving it, so it stays the reference the
 * vector arm is held to rather than a second transcription that could drift with it.
 */
final class UniformExchangeScalar implements UniformExchange {

    @Override
    public long exchange(double[] uniform, int offA, int offB, int n, double uuE) {
        long changed = 0L;
        for (int i = 0; i < n; i++) {
            if (RadiationSystemNT.exchangeUniExactXZ(uniform, offA + i, offB + i, uuE)) changed |= 1L << i;
        }
        return changed;
    }
}
