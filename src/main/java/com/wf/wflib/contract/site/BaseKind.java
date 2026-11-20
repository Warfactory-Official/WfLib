package com.wf.wflib.contract.site;

import java.util.Locale;

/**
 * The base archetypes. Each is a footprint and a list of what stands in it; the guidelines the layout follows are in
 * {@code docs/field-ops.md}. A bigger contract (more soldiers, a higher tier) gets a bigger base, so that the soldiers have
 * somewhere to be: patrols along a wall, a gate, overwatch on a tower, a reserve in a barracks, a few outposts out in the
 * approaches, and vehicles in a bay.
 *
 * @param radius footprint radius that must be survey-clean (the grading and the pad)
 * @param wall half-size of the perimeter wall square, 0 = no wall (sandbag ring instead)
 * @param wallHeight blocks
 * @param towers corner and gate towers
 * @param barracks sleeping halls inside
 * @param bunkers sunken bunkers inside
 * @param nests machine-gun nests among the approaches
 * @param trenches trench runs outside the wall
 * @param outposts small garrisoned camps out in the approaches
 * @param bays vehicle bays
 * @param maxRange steepest ground across the footprint the builder will grade flat
 * @param minSoldiers fewest soldiers that make sense for it (below it a smaller archetype is chosen)
 */
public enum BaseKind {
    OUTPOST(16, 0, 0, 1, 0, 0, 2, 0, 0, 6, 0),
    COMPOUND(28, 18, 4, 4, 2, 0, 3, 2, 1, 1, 8),
    FORTRESS(44, 28, 6, 8, 3, 2, 6, 4, 3, 2, 18);

    public final int radius, wall, wallHeight, towers, barracks, bunkers, nests, trenches, outposts, bays, minSoldiers;

    /** Steepest allowed range is a function of the kind: bigger bases grade more ground. */
    public int maxRange() {
        return this == OUTPOST ? 5 : this == COMPOUND ? 7 : 9;
    }

    BaseKind(int radius, int wall, int wallHeight, int towers, int barracks, int bunkers, int nests, int trenches, int outposts, int bays, int minSoldiers) {
        this.radius = radius;
        this.wall = wall;
        this.wallHeight = wallHeight;
        this.towers = towers;
        this.barracks = barracks;
        this.bunkers = bunkers;
        this.nests = nests;
        this.trenches = trenches;
        this.outposts = outposts;
        this.bays = bays;
        this.minSoldiers = minSoldiers;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The archetype a force of {@code soldiers} wants: the largest whose minimum it meets. */
    public static BaseKind forForce(int soldiers) {
        BaseKind best = OUTPOST;
        for (BaseKind k : values()) if (soldiers >= k.minSoldiers) best = k;
        return best;
    }

    public static BaseKind byId(String id) {
        for (BaseKind k : values()) if (k.id().equals(id)) return k;
        throw new IllegalArgumentException("unknown base '" + id + "'");
    }
}
