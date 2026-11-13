package com.wf.wflib.damage.api;

/** Kinetic round class: armour-pen velocity exponent and {@link Threat} variables. */
public enum Penetrator {
    SMALL_ARMS(2.0f, 70, 1.3f, 1.0f),
    AP(1.43f, 70, 1.3f, 1.0f),
    APDS(1.2f, 75, 1.2f, 1.0f),
    APFSDS(1.0f, 82, 1.0f, 0);

    /** Armour pen = {@code pen * (v / v0)^penExponent}. */
    public final float penExponent;
    public final float ricochetDeg;
    public final float slopeExp;
    public final float overmatchTD;

    Penetrator(float penExponent, float ricochetDeg, float slopeExp, float overmatchTD) {
        this.penExponent = penExponent;
        this.ricochetDeg = ricochetDeg;
        this.slopeExp = slopeExp;
        this.overmatchTD = overmatchTD;
    }
}
