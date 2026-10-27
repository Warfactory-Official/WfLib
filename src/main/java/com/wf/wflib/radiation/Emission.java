package com.wf.wflib.radiation;

/**
 * The emission relaxation, {@code delta = |e| * (s - c) / |s|} over the sources active on a pocket. The
 * magnitude lives in the coefficient and the sign in the gate: written as the literal {@code e * (1 - c/s)}
 * the coefficient on c exceeds 1 for a negative emission against a positive saturation and the step pushes
 * away from s, where under this form emitter, ceiling absorber and floor absorber are one stabilising arm.
 * 1.7: a divergence, 1.7 reaching its cap exactly where this asymptotes; the native
 * {@code EmissionAccumulator} carries the same op sequence and a parity gate binds the two.
 */
final class Emission {

    private Emission() {
    }

    /**
     * Whether a source acts on a pocket currently at {@code c}. A source at equilibrium must stay out of the
     * sums as well as out of the step, or it drags {@code c*}.
     */
    static boolean active(double emission, double saturation, double c) {
        if (emission == 0.0D || saturation == 0.0D) return false;
        return emission * (saturation - c) > 0.0D;
    }

    static double weight(double emission, double saturation) {
        return magnitude(emission) / magnitude(saturation);
    }

    static double numeratorTerm(double emission, double saturation) {
        return saturation < 0.0D ? -magnitude(emission) : magnitude(emission);
    }

    /**
     * The combined step as a DELTA, {@code N - c*W}. The algebraically equal fixed-point form
     * {@code c* + A(c - c*)} is unusable: a saturation far above the field underflows the weight, so
     * {@code A} rounds to 1.0 and the step cancels to zero where the answer is the full rate.
     */
    static double relax(double c, double weightSum, double numerator) {
        if (weightSum <= 0.0D) return c;
        // Past unit weight the coefficient on c turns negative and the step would oscillate rather than settle.
        if (weightSum > 1.0D) return numerator / weightSum;
        return c + (numerator - c * weightSum);
    }

    static double single(double c, double emission, double saturation) {
        if (!active(emission, saturation, c)) return c;
        return relax(c, weight(emission, saturation), numeratorTerm(emission, saturation));
    }

    private static double magnitude(double v) {
        return v < 0.0D ? -v : v;
    }
}
