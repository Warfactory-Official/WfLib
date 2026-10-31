package com.wf.wflib.radiation;

import org.slf4j.LoggerFactory;

/** Vectorizable post-sweep decay over contiguous uniform-section columns; it does not require FMA. */
public interface UniformDecay {

    int CHANGED = 0;
    int ZEROED = 1;
    int FOG = 2;
    int DESTROY = 3;

    UniformDecay INSTANCE = choose();

    void decay(double[] uniform, int off, int n, double retention, double minBound, double fogRad,
               double destroyRad, long[] out);

    private static UniformDecay choose() {
        VectorBackend backend = VectorApi.backend();
        // UniformDecayVector first loads inside this branch; see VectorApi's linkage contract.
        if (backend.arithmetic() && backend.masks()) {
            try {
                return new UniformDecayVector();
            } catch (Throwable t) {
                LoggerFactory.getLogger("NTM").warn("Vector uniform decay unavailable; using scalar", t);
            }
        }
        return new UniformDecayScalar();
    }
}
