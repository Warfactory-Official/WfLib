package com.wf.wflib.damage.api;

/**
 * @param caliberMm     body diameter
 * @param penetrationMm RHA the round defeats at normal incidence; 0 = no armour-model figure
 * @param wearFactor    WFLib armour: plate/insert wear points x this (thermal effects); 1 = as absorbed
 * @param ricochetDeg   critical angle from the surface normal past which the round skates
 * @param slopeExp      effective thickness = {@code t / cos^n}
 * @param overmatchTD   caliber / plate mm at or above which no ricochet; 0 = never
 * @param fillerKg      post-penetration burst (APHE); 0 = none
 */
public record Threat(ThreatKind kind, float caliberMm, float penetrationMm, float wearFactor, float ricochetDeg,
                     float slopeExp, float overmatchTD, float fillerKg) {

    public static final float SMALL_ARMS_LIMIT_MM = 20.0f;

    public Threat {
        if (kind == ThreatKind.KINETIC && caliberMm < SMALL_ARMS_LIMIT_MM) {
            kind = ThreatKind.SMALL_ARMS;
        }
        if (!(ricochetDeg > 0 && ricochetDeg <= 90) || !(slopeExp > 0) || !(overmatchTD >= 0) || !(fillerKg >= 0)) {
            throw new IllegalArgumentException("ricochetDeg " + ricochetDeg + " slopeExp " + slopeExp
                    + " overmatchTD " + overmatchTD + " fillerKg " + fillerKg);
        }
    }

    /** Variables = {@code kind}'s defaults. */
    public Threat(ThreatKind kind, float caliberMm, float penetrationMm, float wearFactor) {
        this(kind, caliberMm, penetrationMm, wearFactor, kind.ricochetDeg, kind.slopeExp, kind.overmatchTD, 0);
    }

    public Threat(ThreatKind kind, float caliberMm, float penetrationMm) {
        this(kind, caliberMm, penetrationMm, 1.0f);
    }

    public Threat withPenetration(float mm) {
        return new Threat(kind, caliberMm, mm, wearFactor, ricochetDeg, slopeExp, overmatchTD, fillerKg);
    }

    public Threat withWearFactor(float factor) {
        return new Threat(kind, caliberMm, penetrationMm, factor, ricochetDeg, slopeExp, overmatchTD, fillerKg);
    }
}
