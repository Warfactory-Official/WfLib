package com.wf.wflib.explosion.api;

import org.jetbrains.annotations.Nullable;

/**
 * What a warhead delivers, by mechanism. A component is {@code null} when the warhead has none; at least one is
 * required. {@link BlastTarget}s answer each component themselves.
 */
public record BlastPayload(@Nullable Blast blast, @Nullable Fragments fragments, @Nullable Jet jet,
                           @Nullable Efp efp, @Nullable Hesh hesh, @Nullable Thermal thermal) {

    /** {@code kg = KG_PER_SIZE_CUBED * size^3}: charge scales with the cube of blast radius. Estimate. */
    public static final float KG_PER_SIZE_CUBED = 0.02f;

    /** Over-pressure charge, TNT-equivalent. */
    public record Blast(float tntKg) {
        public Blast {
            if (!(tntKg > 0f)) {
                throw new IllegalArgumentException("blast tntKg " + tntKg);
            }
        }
    }

    /** Fragment set: {@code count} pieces of {@code massG} grams leaving at {@code speed} m/s. */
    public record Fragments(int count, float massG, float speed) {
        public Fragments {
            if (count <= 0 || !(massG > 0f) || !(speed > 0f)) {
                throw new IllegalArgumentException("fragments " + count + " x " + massG + " g @ " + speed);
            }
        }
    }

    /** Shaped-charge jet: {@code penMm} RHAe at zero standoff loss, cone {@code halfAngleDeg}. */
    public record Jet(float penMm, float halfAngleDeg) {
        public Jet {
            if (!(penMm > 0f) || !(halfAngleDeg > 0f && halfAngleDeg < 90f)) {
                throw new IllegalArgumentException("jet " + penMm + " mm @ " + halfAngleDeg + " deg");
            }
        }
    }

    /** Explosively formed penetrator: {@code penMm} RHAe, slug diameter {@code slugMm}. */
    public record Efp(float penMm, float slugMm) {
        public Efp {
            if (!(penMm > 0f) || !(slugMm > 0f)) {
                throw new IllegalArgumentException("efp " + penMm + " mm, slug " + slugMm + " mm");
            }
        }
    }

    /** High-explosive squash head: contact charge of shell diameter {@code calibreMm}. */
    public record Hesh(float calibreMm) {
        public Hesh {
            if (!(calibreMm > 0f)) {
                throw new IllegalArgumentException("hesh calibre " + calibreMm);
            }
        }
    }

    /** Fireball / gas heat, reach {@code radius} blocks. */
    public record Thermal(float radius) {
        public Thermal {
            if (!(radius > 0f)) {
                throw new IllegalArgumentException("thermal radius " + radius);
            }
        }
    }

    public BlastPayload {
        if (blast == null && fragments == null && jet == null && efp == null && hesh == null && thermal == null) {
            throw new IllegalArgumentException("payload without a component");
        }
    }

    public static float kgForSize(float size) {
        return KG_PER_SIZE_CUBED * size * size * size;
    }

    /** Size-only blast: the {@code blast} component alone. */
    public static BlastPayload ofSize(float size) {
        return blast(kgForSize(size));
    }

    public static BlastPayload blast(float tntKg) {
        return new BlastPayload(new Blast(tntKg), null, null, null, null, null);
    }

    public BlastPayload withBlast(float tntKg) {
        return new BlastPayload(new Blast(tntKg), fragments, jet, efp, hesh, thermal);
    }

    public BlastPayload withFragments(int count, float massG, float speed) {
        return new BlastPayload(blast, new Fragments(count, massG, speed), jet, efp, hesh, thermal);
    }

    public BlastPayload withJet(float penMm, float halfAngleDeg) {
        return new BlastPayload(blast, fragments, new Jet(penMm, halfAngleDeg), efp, hesh, thermal);
    }

    public BlastPayload withEfp(float penMm, float slugMm) {
        return new BlastPayload(blast, fragments, jet, new Efp(penMm, slugMm), hesh, thermal);
    }

    public BlastPayload withHesh(float calibreMm) {
        return new BlastPayload(blast, fragments, jet, efp, new Hesh(calibreMm), thermal);
    }

    public BlastPayload withThermal(float radius) {
        return new BlastPayload(blast, fragments, jet, efp, hesh, new Thermal(radius));
    }

    /**
     * A carrier's payload from its warhead's declared one plus the carrier's own figures: {@code sizeOverride > 0}
     * replaces the blast charge, {@code jetPenMm > 0} the penetration of a declared jet. No declared payload =>
     * size-only; neither => null.
     */
    @Nullable
    public static BlastPayload resolve(@Nullable BlastPayload declared, float sizeOverride, float jetPenMm) {
        BlastPayload p = declared;
        if (sizeOverride > 0f) {
            p = p == null ? ofSize(sizeOverride) : p.withBlast(kgForSize(sizeOverride));
        }
        if (jetPenMm > 0f && p != null && p.jet != null) {
            p = p.withJet(jetPenMm, p.jet.halfAngleDeg);
        }
        return p;
    }
}
