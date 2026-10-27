package com.wf.wflib.anomaly;

/**
 * What an anomaly does, one per X-Ray zone class: {@link MincerTuning} ({@code CMincer}: pull, catch, tear) and
 * {@link BlastTuning} ({@code CMosquitoBald}: one hit on contact). Both run the same timeline: idle, tripped, a
 * blowout hitting at {@link #explosionTick} and showing until {@link #blowoutTicks}, then spent for
 * {@link #cooldownTicks}.
 */
public sealed interface AnomalyBehaviour permits MincerTuning, BlastTuning {
    double radius();

    int explosionTick();

    int cooldownTicks();

    /** Blowout shown until this; a mincer's ends in its explosion. */
    default int blowoutTicks() {
        return explosionTick();
    }

    /** Hit something, then leave it alone this long (disable_time). */
    default int immuneTicks() {
        return 0;
    }

    /** Small things (items, chickens, thrown bolts) set it off too. */
    default boolean smallTrips() {
        return false;
    }

    /** Close enough in to trip it. */
    boolean trips(double dist);

    /** The explosion's hit at {@code dist}, health points. */
    float hit(double dist, boolean player);

    /** A blowout nobody stays in winds down instead of firing. */
    default boolean fizzles() {
        return false;
    }
}
