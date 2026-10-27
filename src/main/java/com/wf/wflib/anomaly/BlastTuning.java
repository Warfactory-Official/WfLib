package com.wf.wflib.anomaly;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A blast on contact (X-Ray {@code CMosquitoBald}: Springboard, Electro, Burner, acid): something alive steps within
 * {@code radius}, it wakes (awaking_time) and from {@code explosionTick} to the end of its blowout ({@code
 * blowoutTicks}: a Burner's flame stands 7 s) hits whatever is inside once, harder toward the middle, throwing it
 * ({@code launch}) in a random, mostly upward direction ({@code Affect}: x, z in -0.5..0.5, y in 0..1). Then it
 * recharges for {@code cooldownTicks} (accamulate_time) and, if anything alive is still inside, goes again.
 * (disable_time is not a per-target immunity: X-Ray only stops non-living things counting after it.)
 *
 * @param radius        blocks ($def_sphere)
 * @param explosionTick awaking_time + blowout_explosion_time
 * @param blowoutTicks  awaking_time + blowout_time
 * @param cooldownTicks accamulate_time
 * @param maxPower      max_start_power: the hit at the centre, in X-Ray hits ({@link MincerTuning#FULL_HIT} each)
 * @param launch        throw at the centre, blocks/tick (hit_impulse_scale, tuned by feel); a third of it at the rim
 * @param smallTrips    !ignore_small: small living things set it off too (items never do, unlike STALKER's bolt)
 * @param igniteSeconds sets what it hits on fire this long (a Burner)
 */
public record BlastTuning(double radius, int explosionTick, int blowoutTicks, int cooldownTicks, double maxPower,
                          double launch, boolean smallTrips, int igniteSeconds)
        implements AnomalyBehaviour {

    /** {@code CCustomZone::RelativePower}, attenuation 1: {@code 1 - (d / R)^2}, 0 outside. */
    public double relativePower(double dist) {
        double x = dist / radius;
        return x >= 1.0D ? 0.0D : 1.0D - x * x;
    }

    @Override
    public boolean trips(double dist) {
        return dist <= radius;
    }

    @Override
    public float hit(double dist, boolean player) {
        return (float) (maxPower * relativePower(dist) * MincerTuning.FULL_HIT);
    }

    /** The throw at {@code dist}; zero outside, and from anomalies that do not throw. */
    public Vec3 throwAt(double dist, RandomSource random) {
        double r = relativePower(dist);
        if (r <= 0.0D || launch <= 0.0D) {
            return Vec3.ZERO;
        }
        Vec3 d = new Vec3(random.nextDouble() - 0.5D, random.nextDouble(), random.nextDouble() - 0.5D);
        // A dead-flat roll still goes up: never into the ground.
        d = d.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : d.normalize();
        return d.scale(launch * (1.0D / 3.0D + 2.0D / 3.0D * r));
    }
}
