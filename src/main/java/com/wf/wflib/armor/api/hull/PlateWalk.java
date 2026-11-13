package com.wf.wflib.armor.api.hull;

import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One penetration budget walked through ordered layers; pure, no world.
 *
 * <ul>
 *   <li>Solid layer: cost {@code mm * factor * thicknessScale / cos^slopeExp}, factor = material {@code ce} for
 *       shaped charges, else {@code ke}; defeated iff budget {@code >} cost (strict: {@code residual > 0 <=>
 *       through}). A layer that is not defeated ends the walk.</li>
 *   <li>Gap, shaped charge: budget x {@code 1 / (1 + STANDOFF_LOSS * gap)}; tandem exempt.</li>
 *   <li>Gap, {@link ThreatKind#SMALL_ARMS}: budget x {@link #yaw} once a solid layer was defeated.</li>
 *   <li>Gap, anything else: budget unchanged.</li>
 *   <li>HESH {@link WalkResult#transmit}: x material {@code hesh} per solid layer, 0 behind any gap. Budget runs as
 *       for kinetic threats.</li>
 * </ul>
 */
public final class PlateWalk {

    /** Per metre of standoff. */
    public static final float STANDOFF_LOSS = 0.6f;
    /** Budget fraction lost to yaw at most. */
    private static final float YAW_MAX = 0.5f;
    /** Gap in calibres at which 1 - 1/e of {@link #YAW_MAX} is lost. */
    private static final float YAW_CALIBRES = 20.0f;
    private static final float MIN_COS = 0.05f;

    private final boolean shaped;
    private final boolean tandem;
    private final boolean smallArms;
    private final float slopeExp;
    private final float caliberMm;
    private final Threat threat;

    private float budget;
    /** Budget = {@code entry * gain - scaled}: costs rescale with every gap factor. */
    private float gain = 1.0f;
    private float scaled;
    private float transmit = 1.0f;
    private int layers;
    private int defeated;
    private int stoppedAt = -1;
    private int crossing;
    private boolean passed;
    private float markResidual = Float.NaN;
    private float markNeeded;
    private float[] residualAfter = new float[4];
    private float[] spall = new float[4];
    private int[] crossingOf = new int[4];
    private float[] crossingAt = new float[4];
    private boolean[] crossingIn = new boolean[4];
    private List<WalkResult.EraHit> era;

    public PlateWalk(Threat threat) {
        ThreatKind kind = threat.kind();
        this.shaped = kind.isShapedCharge();
        this.tandem = kind == ThreatKind.TANDEM_HEAT;
        this.smallArms = kind == ThreatKind.SMALL_ARMS;
        this.slopeExp = threat.slopeExp();
        this.caliberMm = threat.caliberMm();
        this.threat = threat;
        this.budget = threat.penetrationMm();
    }

    /** Budget fraction a small-calibre round keeps after crossing {@code gapMetres} of air once yawed. */
    public static float yaw(float caliberMm, float gapMetres) {
        float calibres = gapMetres * 1000.0f / Math.max(caliberMm, 1.0f);
        return 1.0f - YAW_MAX * (1.0f - (float) Math.exp(-calibres / YAW_CALIBRES));
    }

    /** False once a layer stopped the threat. */
    public boolean alive() {
        return stoppedAt < 0;
    }

    /** Layers after this call belong to the next surface crossing. */
    public PlateWalk nextCrossing() {
        crossing++;
        return this;
    }

    /**
     * Where the current crossing lies: distance along the line, m, and whether the line goes into the volume
     * ({@link WalkResult#crossingAt}, {@link WalkResult#crossingEntering}).
     */
    public PlateWalk at(float metres, boolean entering) {
        if (crossing == crossingAt.length) {
            crossingAt = Arrays.copyOf(crossingAt, crossing * 2);
            crossingIn = Arrays.copyOf(crossingIn, crossing * 2);
        }
        crossingAt[crossing] = metres;
        crossingIn[crossing] = entering;
        return this;
    }

    public PlateWalk solid(float mm, PlateMaterial material, float thicknessScale, float cos) {
        return rhae(mm * material.ke(), mm * material.ce(), material.spall(), material.hesh(), thicknessScale, cos);
    }

    /**
     * Layer given as RHA-equivalent normal thickness.
     *
     * @param ke    mm RHA vs kinetic threats
     * @param ce    mm RHA vs shaped charges
     * @param spall {@link PlateMaterial#spall()}
     * @param hesh  {@link PlateMaterial#hesh()}
     * @param cos   incidence cosine, clamped to {@value #MIN_COS}
     */
    public PlateWalk rhae(float ke, float ce, float spall, float hesh, float thicknessScale, float cos) {
        if (stoppedAt >= 0) {
            return this;
        }
        float c = Math.max(cos, MIN_COS);
        float slope = slopeExp == 1.0f ? c : (float) Math.pow(c, slopeExp);
        float cost = (shaped ? ce : ke) * thicknessScale / slope;
        int i = layers++;
        if (i == residualAfter.length) {
            residualAfter = Arrays.copyOf(residualAfter, i * 2);
            this.spall = Arrays.copyOf(this.spall, i * 2);
            crossingOf = Arrays.copyOf(crossingOf, i * 2);
        }
        this.spall[i] = spall;
        crossingOf[i] = crossing;
        transmit *= hesh;
        scaled += cost;
        if (budget > cost) {
            budget -= cost;
            defeated++;
            passed = true;
            residualAfter[i] = budget;
        } else {
            stoppedAt = i;
            budget = 0.0f;
            residualAfter[i] = 0.0f;
        }
        return this;
    }

    /** A layer that keeps {@code keep} of the budget without stopping anything: a brick's flyer plate, a crushed jet. */
    public PlateWalk cut(float keep) {
        if (stoppedAt >= 0 || keep >= 1.0f) {
            return this;
        }
        float k = Math.max(keep, 0.0f);
        budget *= k;
        gain *= k;
        scaled *= k;
        return this;
    }

    /**
     * Integral reactive brick {@code cell} of {@code plate}. A live brick the threat sets off ({@link
     * EraType#strike}) cuts the budget and is recorded ({@link WalkResult#eraHits}); the walk never spends it, the
     * caller does once the shot is final.
     */
    public PlateWalk era(String plate, int cell, EraType type, boolean live, float cos, float edgeFraction) {
        if (stoppedAt >= 0 || !live) {
            return this;
        }
        EraType.Strike s = type.strike(threat, cos, edgeFraction);
        if (!s.initiated()) {
            return this;
        }
        if (era == null) {
            era = new ArrayList<>(2);
        }
        era.add(new WalkResult.EraHit(plate, cell, type));
        return cut(s.keep());
    }

    /** Air of {@code metres} along the line. */
    public PlateWalk gap(float metres) {
        if (stoppedAt >= 0 || !(metres > 0.0f)) {
            return this;
        }
        transmit = 0.0f;
        float f = shaped ? (tandem ? 1.0f : 1.0f / (1.0f + STANDOFF_LOSS * metres))
                : smallArms && passed ? yaw(caliberMm, metres) : 1.0f;
        budget *= f;
        gain *= f;
        scaled *= f;
        return this;
    }

    /** Records the budget and {@link WalkResult#needed} as they stand: what the layers so far leave behind them. */
    public PlateWalk mark() {
        markResidual = budget;
        markNeeded = needed();
        return this;
    }

    private float needed() {
        return layers == 0 ? 0.0f : gain > 0.0f ? scaled / gain : Float.POSITIVE_INFINITY;
    }

    public WalkResult result() {
        float needed = needed();
        return new WalkResult(budget, needed, Float.isNaN(markResidual) ? budget : markResidual,
                Float.isNaN(markResidual) ? needed : markNeeded, transmit, layers, defeated, stoppedAt, residualAfter,
                spall, crossingOf, Arrays.copyOf(crossingAt, crossing + 1), Arrays.copyOf(crossingIn, crossing + 1),
                era == null ? List.of() : List.copyOf(era));
    }
}
