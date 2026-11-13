package com.wf.wflib.damage.api;

/** What defeats a round: the mechanism add-on armour answers, not its damage. */
public enum ThreatKind {
    /** Rifle and machine-gun ammunition, below 20 mm. */
    SMALL_ARMS(70, 1.3f, 1.0f),
    /** Penetrator or AP shot: armour is beaten by mass and velocity. */
    KINETIC(70, 1.3f, 1.0f),
    /** Single shaped charge: a jet formed at the nose; ricochet = fuze graze. */
    HEAT(80, 1.0f, 0),
    /** Precursor charge + main charge. */
    TANDEM_HEAT(80, 1.0f, 0),
    /** Blast and fragments; no penetrator. */
    HE(70, 1.0f, 0),
    /** Squash head: contact blast, no penetration. */
    HESH(85, 1.0f, 0),
    /** Explosively formed slug. */
    EFP(80, 1.0f, 0);

    /** Defaults of {@link Threat}: critical angle from the surface normal, slope exponent, T/D overmatch (0 = none). */
    public final float ricochetDeg;
    public final float slopeExp;
    public final float overmatchTD;

    ThreatKind(float ricochetDeg, float slopeExp, float overmatchTD) {
        this.ricochetDeg = ricochetDeg;
        this.slopeExp = slopeExp;
        this.overmatchTD = overmatchTD;
    }

    public boolean isShapedCharge() {
        return this == HEAT || this == TANDEM_HEAT;
    }

    /** The warhead's jet or slug, not the round's own hit, resolves the armour ({@code BlastPayload.jet / efp}). */
    public boolean ownsJet() {
        return isShapedCharge() || this == EFP;
    }
}
