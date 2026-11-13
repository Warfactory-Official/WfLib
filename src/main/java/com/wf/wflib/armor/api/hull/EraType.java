package com.wf.wflib.armor.api.hull;

import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.explosion.api.BlastPayload;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Explosive reactive brick type. Pure data + the strike rule; pack-loaded into {@link EraTypes}.
 *
 * <ul>
 *   <li>{@code *Cut}: fraction of a threat's penetration removed at {@code >= 60 deg} obliquity.</li>
 *   <li>{@code kineticInitiationMm} / {@code heInitiationMm}: smallest kinetic / HE calibre that sets a brick off;
 *       {@link com.wf.wflib.damage.api.ThreatKind#SMALL_ARMS} never does.</li>
 *   <li>{@code fragmentInitiationJ}: energy of one fragment that sets a brick off.</li>
 *   <li>{@code thickness}: blocks, along the panel normal.</li>
 *   <li>{@code backPlateMm}: struck plate thinner than this (RHAe, KE) takes the {@code flyer} plate ({@link #flyerFor}).</li>
 *   <li>{@code sympathetic}: chance each neighbouring cell goes off with a detonating one.</li>
 *   <li>{@code blastKg}, {@code fragments}: what a detonating brick throws outward.</li>
 * </ul>
 */
public record EraType(String id, float heatCut, float kineticCut, float tandemCut, float kineticInitiationMm,
                      float heInitiationMm, float fragmentInitiationJ, float thickness, float backPlateMm,
                      float sympathetic, float blastKg, @Nullable BlastPayload.Fragments fragments,
                      @Nullable BlastPayload.Fragments flyer) {

    /** ERA plates move across the jet: effect ~ sin(obliquity), full from 60 deg. */
    private static final float FULL_EFFECT_SIN = Mth.sin((float) Math.toRadians(60));
    private static final float MIN_OBLIQUITY_EFFECT = 0.3f;
    /** Hit within this fraction of a cell side from its edge: half the plate area engages. */
    public static final float EDGE_BAND = 0.12f;
    private static final float EDGE_EFFECT = 0.5f;

    public EraType {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("era type needs an id");
        }
        for (float cut : new float[] {heatCut, kineticCut, tandemCut, sympathetic}) {
            if (!(cut >= 0 && cut <= 1)) {
                throw new IllegalArgumentException("era type " + id + ": cut / chance " + cut + " outside 0..1");
            }
        }
        if (!(blastKg > 0)) {
            throw new IllegalArgumentException("era type " + id + ": blast_kg must be > 0");
        }
        if (!(thickness > 0)) {
            throw new IllegalArgumentException("era type " + id + ": thickness must be > 0");
        }
    }

    /** @param initiated brick set off; {@code keep} = fraction of the penetration left behind it */
    public record Strike(boolean initiated, float keep) {
        public static final Strike NONE = new Strike(false, 1.0f);
    }

    /** Does {@code threat} set a brick off on a direct hit? */
    public boolean initiates(Threat threat) {
        return switch (threat.kind()) {
            case HEAT, TANDEM_HEAT -> true;
            case KINETIC, EFP -> threat.caliberMm() >= kineticInitiationMm;
            case HE, HESH -> threat.caliberMm() >= heInitiationMm;
            case SMALL_ARMS -> false;
        };
    }

    /**
     * @param cosIncidence    shot . brick normal
     * @param edgeFraction    distance of the hit from the brick's nearest edge, as a share of the cell side
     */
    public Strike strike(Threat threat, float cosIncidence, float edgeFraction) {
        if (!initiates(threat)) {
            return Strike.NONE;
        }
        float cut = switch (threat.kind()) {
            case HEAT -> heatCut;
            case KINETIC, EFP -> kineticCut;
            case TANDEM_HEAT -> tandemCut;
            case HE, HESH, SMALL_ARMS -> 0.0f;
        };
        float cos = Mth.clamp(Math.abs(cosIncidence), 0, 1);
        float sin = Mth.sqrt(1 - cos * cos);
        float effect = Mth.clamp(sin / FULL_EFFECT_SIN, MIN_OBLIQUITY_EFFECT, 1.0f);
        if (edgeFraction < EDGE_BAND) {
            effect *= EDGE_EFFECT;
        }
        return new Strike(true, 1.0f - cut * effect);
    }

    /**
     * What one detonating brick throws: its blast, and outward fragments; on a plate thinner than
     * {@link #backPlateMm} (RHAe, KE, {@code nominalKeMm}) the flyer plate is the fragment set instead.
     */
    public BlastPayload payload(float nominalKeMm) {
        BlastPayload p = BlastPayload.blast(blastKg);
        BlastPayload.Fragments f = flyer != null && nominalKeMm < backPlateMm ? flyer : fragments;
        return f == null ? p : p.withFragments(f.count(), f.massG(), f.speed());
    }
}
