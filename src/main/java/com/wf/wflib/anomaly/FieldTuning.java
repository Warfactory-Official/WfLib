package com.wf.wflib.anomaly;

/**
 * A field (X-Ray {@code CRadioactiveZone}: radiation, thermal, acid fields; also the no-gravity one): never blows
 * out, only works on the living inside it, every moment they stay: {@code perSecond} at the centre, falling to
 * {@code 1 - attenuation} of it at the rim ({@code RelativePower}). Radiation doses rads instead of hurting.
 *
 * @param radius      blocks ($def_sphere)
 * @param perSecond   max_start_power per second, in X-Ray hits ({@link MincerTuning#FULL_HIT} each)
 * @param attenuation how much of it is gone at the rim
 * @param radiation   doses rads (WFLib radiation) rather than hurting
 */
public record FieldTuning(double radius, double perSecond, double attenuation, boolean radiation)
        implements AnomalyBehaviour {
    /** Hurts every this often (Minecraft's invulnerability window swallows faster hits). */
    public static final int PULSE_TICKS = 10;
    /** An X-Ray radiation hit of 1.0 (deadly), in rads. */
    public static final double FULL_DOSE = 1000.0D;

    public double relativePower(double dist) {
        double x = dist / radius;
        return x >= 1.0D ? 0.0D : Math.max(0.0D, 1.0D - attenuation * x * x);
    }

    @Override
    public int explosionTick() {
        return 0;
    }

    @Override
    public int cooldownTicks() {
        return 0;
    }

    @Override
    public boolean trips(double dist) {
        return false;
    }

    /** One pulse's hurt at {@code dist}, health points. */
    @Override
    public float hit(double dist, boolean player) {
        return (float) (perSecond * relativePower(dist) * MincerTuning.FULL_HIT * PULSE_TICKS / 20.0D);
    }

    /** Rads per tick at {@code dist}. */
    public double dose(double dist) {
        return perSecond * relativePower(dist) * FULL_DOSE / 20.0D;
    }
}
