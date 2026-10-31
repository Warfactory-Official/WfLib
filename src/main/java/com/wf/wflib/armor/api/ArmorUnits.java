package com.wf.wflib.armor.api;

/**
 * Condition is deliberately inflated: a plate is ~300,000 points rather than ~300.
 *
 * <p>At that granularity every wear calculation is exact integer arithmetic, which removes the
 * fractional accumulators, threshold-crossing flushes and rounding special cases a low-resolution
 * durability would otherwise force. {@code int} is ample: 2.1 billion holds a 300,000-point plate
 * four thousand times over. Foreign durability need not fit: GT electric armour MAX_DAMAGE =
 * {@code Integer.MAX_VALUE} => {@link #durabilityPoints} saturates.
 *
 * <p>Vanilla durability is bypassed for a different reason than range. A vanilla stack carries
 * exactly one damage value, and a carrier with three inserts needs three independent conditions.
 */
public final class ArmorUnits {

    /** Condition points per point of absorbed damage. */
    public static final int POINTS_PER_DAMAGE = 100;

    /** Fraction of absorbed damage that still wears a layer the hit failed to reach the hardness of. */
    public static final double SCUFF_RATE = 0.05D;

    /**
     * Ceiling on summed DR. Never 1.0: a type a player cannot ever hurt is indistinguishable from a
     * bug, and the whole model is built so that armour is overwhelmable.
     */
    public static final double MAX_RESISTANCE = 0.95D;

    private ArmorUnits() {
    }

    /** Durability (vanilla damage units) -> condition points, saturating at {@code Integer.MAX_VALUE}. */
    public static int durabilityPoints(int durability) {
        return (int) Math.min((long) durability * POINTS_PER_DAMAGE, Integer.MAX_VALUE);
    }

    public static int toPoints(double damage) {
        if (damage <= 0.0D) {
            return 0;
        }
        double points = damage * POINTS_PER_DAMAGE;
        return points >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(points);
    }
}
