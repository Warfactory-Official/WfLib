package com.wf.wflib.anomaly.cluster;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What one anomaly cluster holds, before any terrain: its design, size, anomalies and artefacts. Pure (ids, not
 * registry objects), so it is tested on its own ({@code ClusterPlanTest}); {@link AnomalyClusterFeature} carves the
 * design and finds the anomalies their spots.
 *
 * <p>Tiers follow STALKER's way into the Zone: 1 the Cordon's lone Springboards and Burners, 2 the Garbage's
 * tangles, 3 the Red Forest and Yantar (Vortexes, Comets, Burnt Fuzz, the fields), 4 the centre (Mephistopheles,
 * Grabbers, Flash). A tier's hardest anomalies are its own rank; easier ones fill in round them.
 */
public record ClusterPlan(Design design, int tier, int radius, List<Kind> anomalies, List<Loot> artefacts) {

    /** What an anomaly works with; a design leans to one, and artefacts grow where their element is. */
    public enum Element { GRAVITY, ELECTRIC, FIRE, CHEMICAL, EXOTIC }

    /** Where an anomaly goes: on the ground, hanging from something overhead, or one per cluster at its middle. */
    public enum Spot { FLOOR, CEILING, FIELD }

    /** The anomalies a cluster draws from: block id, rank (the lowest tier it shows up in), element, spot, cap. */
    public enum Kind {
        SPRINGBOARD("springboard", 1, Element.GRAVITY, Spot.FLOOR, 99),
        WHIRLIGIG("whirligig", 2, Element.GRAVITY, Spot.FLOOR, 99),
        NO_GRAVITY("no_gravity", 2, Element.GRAVITY, Spot.FLOOR, 2),
        VORTEX("gravity_anomaly", 3, Element.GRAVITY, Spot.FLOOR, 2),
        REBOUNDER("rebounder", 3, Element.GRAVITY, Spot.FLOOR, 2),
        ELECTRO("electro", 1, Element.ELECTRIC, Spot.FLOOR, 99),
        NET("net", 2, Element.ELECTRIC, Spot.FLOOR, 99),
        BALL_LIGHTNING("ball_lightning", 2, Element.ELECTRIC, Spot.FLOOR, 3),
        GHOST("ghost", 2, Element.ELECTRIC, Spot.FLOOR, 2),
        MEPHISTOPHELES("mephistopheles", 4, Element.ELECTRIC, Spot.FLOOR, 1),
        BURNER("burner", 1, Element.FIRE, Spot.FLOOR, 99),
        STEAM("steam", 1, Element.FIRE, Spot.FLOOR, 99),
        COMET("comet", 3, Element.FIRE, Spot.FLOOR, 1),
        THERMAL_FIELD("thermal_field", 3, Element.FIRE, Spot.FIELD, 1),
        ACID("acid", 1, Element.CHEMICAL, Spot.FLOOR, 99),
        GREEN_DRAGON("green_dragon", 2, Element.CHEMICAL, Spot.FLOOR, 3),
        BURNT_FUZZ("burnt_fuzz", 2, Element.CHEMICAL, Spot.CEILING, 99),
        LIQUID_GOLD("liquid_gold", 3, Element.CHEMICAL, Spot.FLOOR, 2),
        ACID_FIELD("acid_field", 3, Element.CHEMICAL, Spot.FIELD, 1),
        GRABBER("grabber", 4, Element.CHEMICAL, Spot.CEILING, 2),
        RADIATION_FIELD("radiation_field", 2, Element.EXOTIC, Spot.FIELD, 1),
        FLASH("flash", 3, Element.EXOTIC, Spot.FLOOR, 1);

        public final String id;
        public final int rank;
        public final Element element;
        public final Spot spot;
        /** Most of it in one cluster: roamers and fields would crowd each other. */
        public final int cap;

        Kind(String id, int rank, Element element, Spot spot, int cap) {
            this.id = id;
            this.rank = rank;
            this.element = element;
            this.spot = spot;
            this.cap = cap;
        }
    }

    /**
     * The shapes the ground takes. Each leans to an element (its anomalies come up six times as often) and opens at
     * a tier; {@code ceilings}: it makes overhangs for Burnt Fuzz and Grabbers to hang from.
     */
    public enum Design {
        /** An impact bowl with a thrown-up rim: gravity's mark. */
        CRATER(Element.GRAVITY, 1, false),
        /** Jagged cracks split through scorched ground, hot at the bottom. */
        FISSURE(Element.FIRE, 1, false),
        /** A sunken, sodden hollow of mud and stagnant puddles, bones in it. */
        BOG(Element.CHEMICAL, 1, false),
        /** Ground fused flat by discharges: glassy spikes, ruined pylons. */
        STORM_SCAR(Element.ELECTRIC, 1, false),
        /** Dead, bark-stripped trees whose branches trail Burnt Fuzz. */
        DEAD_GROVE(Element.CHEMICAL, 2, true),
        /** Ground torn up and left hanging: boulders over the pits they came from, upturned spires. */
        UPHEAVAL(Element.GRAVITY, 2, false),
        /** A rock arch thrown over a hollow, things hanging under it. */
        ARCH(Element.CHEMICAL, 3, true),
        /** The Zone's heart: a shaft into the dark ringed with weeping obsidian. */
        PIT(Element.EXOTIC, 4, false);

        public final Element element;
        public final int minTier;
        public final boolean ceilings;

        Design(Element element, int minTier, boolean ceilings) {
            this.element = element;
            this.minTier = minTier;
            this.ceilings = ceilings;
        }
    }

    /** Artefacts by element (wflib item ids): what a cluster can grow. */
    public enum Loot {
        BATTERY("battery", Element.ELECTRIC),
        SOUL("soul", Element.CHEMICAL);

        public final String id;
        public final Element element;

        Loot(String id, Element element) {
            this.id = id;
            this.element = element;
        }
    }

    public static final int TIERS = 4;
    /**
     * Widest radius, blocks: a feature may write one chunk round its own, and the middle sits at most 2 off the
     * chunk's centre, with 2 more cleared beyond the rim (8 + 2 + 18 + 2 < 32).
     */
    public static final int MAX_RADIUS = 18;

    private static final int[] RADIUS_MIN = {7, 9, 12, 15};
    private static final int[] RADIUS_MAX = {9, 12, 15, 18};
    private static final int[] COUNT_MIN = {2, 4, 6, 9};
    private static final int[] COUNT_MAX = {4, 6, 9, 13};
    /** Chance of an artefact, and of a second (only once there is a first) and a third. */
    private static final float[][] ARTEFACT_CHANCE = {{0.15F}, {0.35F, 0.1F}, {0.6F, 0.25F}, {0.9F, 0.45F, 0.15F}};

    /** Expected artefacts for a tier, for docs and tests. */
    public static float expectedArtefacts(int tier) {
        float p = 1.0F;
        float sum = 0.0F;
        for (float c : ARTEFACT_CHANCE[tier - 1]) {
            p *= c;
            sum += p;
        }
        return sum;
    }

    /**
     * @param tier   1..4
     * @param design null: picked from those open at the tier
     */
    public static ClusterPlan roll(RandomSource r, int tier, @Nullable Design design) {
        int t = Math.max(1, Math.min(TIERS, tier));
        Design d = design != null ? design : pickDesign(r, t);
        int radius = RADIUS_MIN[t - 1] + r.nextInt(RADIUS_MAX[t - 1] - RADIUS_MIN[t - 1] + 1);
        int count = COUNT_MIN[t - 1] + r.nextInt(COUNT_MAX[t - 1] - COUNT_MIN[t - 1] + 1);
        List<Kind> kinds = new ArrayList<>(count);
        // The headline: one of the tier's own rank, of the design's element when it has one.
        kinds.add(pick(r, t, d, kinds, true));
        while (kinds.size() < count) {
            Kind k = pick(r, t, d, kinds, false);
            if (k == null) {
                break;
            }
            kinds.add(k);
        }
        kinds.remove(null);
        List<Loot> loot = new ArrayList<>();
        for (float chance : ARTEFACT_CHANCE[t - 1]) {
            if (r.nextFloat() >= chance) {
                break;
            }
            loot.add(pickLoot(r, kinds));
        }
        return new ClusterPlan(d, t, radius, List.copyOf(kinds), List.copyOf(loot));
    }

    private static Design pickDesign(RandomSource r, int tier) {
        List<Design> open = new ArrayList<>();
        for (Design d : Design.values()) {
            // A tier's own designs come up twice as often as those it inherits.
            for (int n = d.minTier == tier && tier > 1 ? 2 : 1; n > 0 && d.minTier <= tier; n--) {
                open.add(d);
            }
        }
        return open.get(r.nextInt(open.size()));
    }

    @Nullable
    private static Kind pick(RandomSource r, int tier, Design d, List<Kind> taken, boolean headline) {
        int total = 0;
        int[] w = new int[Kind.values().length];
        for (Kind k : Kind.values()) {
            int weight = weight(k, tier, d, taken, headline);
            w[k.ordinal()] = weight;
            total += weight;
        }
        if (total == 0 && headline) {
            return pick(r, tier, d, taken, false);
        }
        if (total == 0) {
            return null;
        }
        int u = r.nextInt(total);
        for (Kind k : Kind.values()) {
            u -= w[k.ordinal()];
            if (u < 0) {
                return k;
            }
        }
        throw new AssertionError();
    }

    static int weight(Kind k, int tier, Design d, List<Kind> taken, boolean headline) {
        if (k.rank > tier || (headline && k.rank != tier) || (k.spot == Spot.CEILING && !d.ceilings)) {
            return 0;
        }
        long have = taken.stream().filter(q -> q == k).count();
        boolean fieldTaken = taken.stream().anyMatch(q -> q.spot == Spot.FIELD);
        if (have >= k.cap || (k.spot == Spot.FIELD && fieldTaken)) {
            return 0;
        }
        int w = k.rank == tier ? 3 : k.rank == tier - 1 ? 2 : 1;
        // Fields fill the air of the whole cluster: one in a few, not one in every.
        if (k.spot == Spot.FIELD) {
            w = 1;
        }
        return k.element == d.element ? w * 6 : w;
    }

    /** An artefact, likelier of the elements the cluster holds most of. */
    static Loot pickLoot(RandomSource r, List<Kind> kinds) {
        int total = 0;
        int[] w = new int[Loot.values().length];
        for (Loot l : Loot.values()) {
            int n = 1;
            for (Kind k : kinds) {
                n += k.element == l.element ? 3 : 0;
            }
            w[l.ordinal()] = n;
            total += n;
        }
        int u = r.nextInt(total);
        for (Loot l : Loot.values()) {
            u -= w[l.ordinal()];
            if (u < 0) {
                return l;
            }
        }
        throw new AssertionError();
    }
}
