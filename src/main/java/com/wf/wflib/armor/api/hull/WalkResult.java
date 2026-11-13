package com.wf.wflib.armor.api.hull;

import java.util.List;

/**
 * Outcome of a {@link PlateWalk}. Layer index = solid layers only, in walk order; gaps have no index.
 *
 * <ul>
 *   <li>{@code residualPen}: mm RHA the threat still has after the last layer; 0 when stopped.</li>
 *   <li>{@code needed}: entry penetration that just defeats everything walked ({@code pen >= needed} {@code <=>}
 *       through); equals the summed effective thickness without gaps.</li>
 *   <li>{@code interiorPen} / {@code interiorNeeded}: the same two figures at {@link PlateWalk#mark} (the entry
 *       plate done): what reaches behind the armour; unmarked = the final figures.</li>
 *   <li>{@code transmit}: HESH blast transmission, product of layer {@code hesh}; 0 behind a gap.</li>
 *   <li>{@code layers}: solid layers entered, the stopping one included; {@code defeated}: of those, passed;
 *       {@code stoppedAt}: index of the stopping layer, -1 = none.</li>
 * </ul>
 */
public final class WalkResult {

    public static final WalkResult NONE = new WalkResult(0, 0, 0, 0, 1, 0, 0, -1, new float[0], new float[0], new int[0], new float[0], new boolean[0], List.of());

    private final float residualPen;
    private final float needed;
    private final float interiorPen;
    private final float interiorNeeded;
    private final float transmit;
    private final int layers;
    private final int defeated;
    private final int stoppedAt;
    private final float[] residualAfter;
    private final float[] spall;
    private final int[] crossing;
    private final float[] crossingAt;
    private final boolean[] crossingIn;
    private final List<EraHit> eraHits;

    WalkResult(float residualPen, float needed, float interiorPen, float interiorNeeded, float transmit, int layers,
               int defeated, int stoppedAt, float[] residualAfter, float[] spall, int[] crossing, float[] crossingAt, boolean[] crossingIn,
               List<EraHit> eraHits) {
        this.residualPen = residualPen;
        this.needed = needed;
        this.interiorPen = interiorPen;
        this.interiorNeeded = interiorNeeded;
        this.transmit = transmit;
        this.layers = layers;
        this.defeated = defeated;
        this.stoppedAt = stoppedAt;
        this.residualAfter = residualAfter;
        this.spall = spall;
        this.crossing = crossing;
        this.crossingAt = crossingAt;
        this.crossingIn = crossingIn;
        this.eraHits = eraHits;
    }

    /** Integral brick the walk set off: {@code cell} of {@code plate}. */
    public record EraHit(String plate, int cell, EraType type) {
    }

    /** Bricks of integral arrays the threat set off, walk order; the walk spent none of them. */
    public List<EraHit> eraHits() {
        return eraHits;
    }

    public float residualPen() {
        return residualPen;
    }

    public float needed() {
        return needed;
    }

    public float interiorPen() {
        return interiorPen;
    }

    public float interiorNeeded() {
        return interiorNeeded;
    }

    public float transmit() {
        return transmit;
    }

    public int layers() {
        return layers;
    }

    public int defeated() {
        return defeated;
    }

    public int stoppedAt() {
        return stoppedAt;
    }

    /** Passed every layer with penetration left. */
    public boolean through() {
        return stoppedAt < 0 && residualPen > 0;
    }

    /** mm RHA left after solid layer {@code i}; 0 for the stopping layer. */
    public float residualAfter(int i) {
        return residualAfter[i];
    }

    /** {@link PlateMaterial#spall()} of layer {@code i}. */
    public float spall(int i) {
        return spall[i];
    }

    /** Crossing ({@link PlateWalk#nextCrossing()} count, from 0) layer {@code i} belongs to. */
    public int crossing(int i) {
        return crossing[i];
    }

    /** Crossings walked ({@link PlateWalk#nextCrossing} count + 1), 0 for {@link #NONE}. */
    public int crossings() {
        return crossingAt.length;
    }

    /** Distance from the line's start of walked crossing {@code k}, m; set by {@link PlateWalk#at}. */
    public float crossingAt(int k) {
        return crossingAt[k];
    }

    /** Walked crossing {@code k} takes the line into the volume its surface bounds; false = out of it. */
    public boolean crossingEntering(int k) {
        return crossingIn[k];
    }

    /**
     * mm RHA left at distance {@code metres} along the line: after the last solid layer whose crossing lies at or
     * before it, {@code entry} before any. Gap losses after a layer are not applied until the next layer.
     */
    public float residualAt(float metres, float entry) {
        float left = entry;
        for (int i = 0; i < layers; i++) {
            if (crossingAt[crossing[i]] > metres) {
                break;
            }
            left = residualAfter[i];
        }
        return left;
    }
}
