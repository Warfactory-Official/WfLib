package com.wf.wflib.anomaly;

import org.jetbrains.annotations.Nullable;

/**
 * How an anomaly looks (client only reads it): its GemRender lens, particles, light and sounds.
 *
 * @param style           which particles and sounds ({@link Style})
 * @param lensRadius      idle bubble radius, blocks
 * @param lensHeight      bubble centre above the ground centre
 * @param idleStrength    idle shimmer, screen-UV offset
 * @param idleTurn        idle turn of the shimmer, radians/second
 * @param blowoutStrength added by the time of the explosion (squared ramp), held through the blowout
 * @param blowoutTurn     radians added by then (squared ramp): the spin-up
 * @param idleDebris      idle particles per tick (as a chance when below 1)
 * @param blowoutDebris   per tick through the blowout
 * @param idleSpin        gravity debris orbit, blocks/tick, idle
 * @param blowoutSpin     the same through the blowout
 * @param flashRgb        blowout_light colour (also the idle glow's)
 * @param flashRange      blowout_light range, blocks
 * @param flashTicks      light_time
 * @param idleLight       idle_light range, blocks; 0 = dark when idle
 * @param spentPresence   how much of the idle look stays after firing (0: gone, as the mincers and Electro are)
 * @param idleParticles   STALKER particle group looped while present (idle_particles), or null
 * @param blowoutParticles STALKER particle group played at the explosion (blowout_particles), or null
 * @param particleScale   those groups' spread across: this anomaly's radius over its STALKER zone's
 * @param particleHeight  their spread up (and their sprites' size), kept lower so a wider anomaly is not taller
 */
public record AnomalyLook(Style style, float lensRadius, double lensHeight, float idleStrength, float idleTurn,
                          float blowoutStrength, float blowoutTurn, float idleDebris, int blowoutDebris,
                          double idleSpin, double blowoutSpin, int flashRgb, float flashRange, int flashTicks,
                          float idleLight, float spentPresence, @Nullable String idleParticles,
                          @Nullable String blowoutParticles, float particleScale, float particleHeight) {

    /** This look with {@code group} as its idle particles. */
    public AnomalyLook idle(String group) {
        return new AnomalyLook(style, lensRadius, lensHeight, idleStrength, idleTurn, blowoutStrength, blowoutTurn,
                idleDebris, blowoutDebris, idleSpin, blowoutSpin, flashRgb, flashRange, flashTicks, idleLight,
                spentPresence, group, blowoutParticles, particleScale, particleHeight);
    }

    /** Particles and sounds: STALKER's sets, with vanilla stand-ins. */
    public enum Style {
        /** Orbiting grit, a rumble, a thump or a rev-up and boom. */
        GRAVITY,
        /** Sparks arcing over the ground, a crackle, a thunderclap. */
        ELECTRIC,
        /** Heat haze; a standing column of flame and smoke. */
        FIRE,
        /** Green bubbles; a hissing green splash. */
        ACID,
        /** Rust-brown strands hanging from the block (Burnt Fuzz, Grabber), down to {@code BlastTuning#HAIRS_DROP}. */
        FUZZ
    }
}
