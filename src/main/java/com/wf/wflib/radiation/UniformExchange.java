package com.wf.wflib.radiation;

import org.slf4j.LoggerFactory;

/**
 * One chunk column's worth of uniform-section exchange, the sweep's hot loop. Every section is
 * {@code KIND_UNI} in practice, so both operands are a contiguous run of {@code sectionsPerChunk} doubles,
 * which is what makes a lane form possible at all. The kernel needs arithmetic, a compare,
 * {@code VectorMask.toLong} and the ternary {@code FMA} and no conversion, gather or transcendental, so it
 * runs on both JIT backends.
 */
public interface UniformExchange {

    UniformExchange INSTANCE = choose();

    /** @return bit i set where that pair changed, which is what the caller ORs into both chunks' active words */
    long exchange(double[] uniform, int offA, int offB, int n, double uuE);

    private static UniformExchange choose() {
        VectorBackend backend = VectorApi.backend();
        // UniformExchangeVector first loads inside this branch; see VectorApi's linkage contract.
        if (backend.arithmetic() && backend.masks() && backend.fma()) {
            try {
                return new UniformExchangeVector();
            } catch (Throwable t) {
                LoggerFactory.getLogger("NTM").warn("Vector uniform exchange unavailable; using scalar", t);
            }
        }
        return new UniformExchangeScalar();
    }
}
