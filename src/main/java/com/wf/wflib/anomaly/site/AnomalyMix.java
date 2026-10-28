package com.wf.wflib.anomaly.site;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which anomalies and artefacts a site holds. Tiers follow STALKER's way into the Zone: 1 the Cordon's lone
 * Springboards and Burners, 2 the Garbage's tangles, 3 the Red Forest and Yantar (Vortexes, Comets, Burnt Fuzz, the
 * fields), 4 the centre (Mephistopheles, Grabbers, Flash). A tier's hardest anomalies are its own rank; easier ones
 * fill in round them. Pure (ids, not registry objects).
 */
public final class AnomalyMix {
    private AnomalyMix() {
    }

    /** What an anomaly works with; a design leans to one, and artefacts grow where their element is. */
    public enum Element { GRAVITY, ELECTRIC, FIRE, CHEMICAL, EXOTIC }

    /** Where an anomaly goes: on the ground, hanging from something overhead, or one per cluster at its middle. */
    public enum Spot { FLOOR, CEILING, FIELD }

    /** The anomalies a site draws from: block id, rank (the lowest tier it shows up in), element, spot, cap. */
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
        /** Most of it in one site: roamers and fields would crowd each other. */
        public final int cap;

        Kind(String id, int rank, Element element, Spot spot, int cap) {
            this.id = id;
            this.rank = rank;
            this.element = element;
            this.spot = spot;
            this.cap = cap;
        }
    }

    /** Artefacts by element (wflib item ids): what a cluster can grow. */
    /** Artefacts a site can grow, each with its element and lowest tier ({@link com.wf.wflib.artifact.Artifact}). */
    public enum Loot {
        BATTERY(com.wf.wflib.artifact.Artifact.BATTERY),
        SOUL(com.wf.wflib.artifact.Artifact.SOUL),
        CRYSTAL(com.wf.wflib.artifact.Artifact.CRYSTAL),
        ITCHER(com.wf.wflib.artifact.Artifact.ITCHER),
        SPONGE(com.wf.wflib.artifact.Artifact.SPONGE),
        FIREBALL(com.wf.wflib.artifact.Artifact.FIREBALL),
        SPARKLER(com.wf.wflib.artifact.Artifact.SPARKLER),
        GLASS_BEADS(com.wf.wflib.artifact.Artifact.GLASS_BEADS),
        MOONLIGHT(com.wf.wflib.artifact.Artifact.MOONLIGHT),
        FLASH(com.wf.wflib.artifact.Artifact.FLASH),
        ICE(com.wf.wflib.artifact.Artifact.ICE),
        RING(com.wf.wflib.artifact.Artifact.RING),
        DUMMY(com.wf.wflib.artifact.Artifact.DUMMY),
        STONE_FLOWER(com.wf.wflib.artifact.Artifact.STONE_FLOWER);

        public final String id;
        public final Element element;
        public final int minTier;

        Loot(com.wf.wflib.artifact.Artifact artifact) {
            this.id = artifact.id();
            this.element = artifact.element();
            this.minTier = artifact.minTier();
        }
    }

    public static final int TIERS = 4;
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
     * {@code count} anomalies for a tier, the first (the headline) of the tier's own rank, leaning to {@code lean}
     * (its kinds six times as likely); ceiling kinds only where there are ceilings.
     */
    public static List<Kind> rollKinds(RandomSource r, int tier, Element lean, boolean ceilings, int count) {
        List<Kind> kinds = new ArrayList<>(count);
        Kind headline = pick(r, tier, lean, ceilings, kinds, true);
        if (headline != null) {
            kinds.add(headline);
        }
        while (kinds.size() < count) {
            Kind k = pick(r, tier, lean, ceilings, kinds, false);
            if (k == null) {
                break;
            }
            kinds.add(k);
        }
        return List.copyOf(kinds);
    }

    public static List<Loot> rollLoot(RandomSource r, int tier, List<Kind> kinds) {
        List<Loot> loot = new ArrayList<>();
        for (float chance : ARTEFACT_CHANCE[tier - 1]) {
            if (r.nextFloat() >= chance) {
                break;
            }
            loot.add(pickLoot(r, tier, kinds));
        }
        return List.copyOf(loot);
    }

    @Nullable
    private static Kind pick(RandomSource r, int tier, Element lean, boolean ceilings, List<Kind> taken,
                             boolean headline) {
        int total = 0;
        int[] w = new int[Kind.values().length];
        for (Kind k : Kind.values()) {
            int weight = weight(k, tier, lean, ceilings, taken, headline);
            w[k.ordinal()] = weight;
            total += weight;
        }
        if (total == 0) {
            return headline ? pick(r, tier, lean, ceilings, taken, false) : null;
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

    static int weight(Kind k, int tier, Element lean, boolean ceilings, List<Kind> taken, boolean headline) {
        if (k.rank > tier || (headline && k.rank != tier) || (k.spot == Spot.CEILING && !ceilings)) {
            return 0;
        }
        long have = taken.stream().filter(q -> q == k).count();
        boolean fieldTaken = taken.stream().anyMatch(q -> q.spot == Spot.FIELD);
        if (have >= k.cap || (k.spot == Spot.FIELD && fieldTaken)) {
            return 0;
        }
        int w = k.rank == tier ? 3 : k.rank == tier - 1 ? 2 : 1;
        // Fields fill the air of the whole site: one in a few, not one in every.
        if (k.spot == Spot.FIELD) {
            w = 1;
        }
        return k.element == lean ? w * 6 : w;
    }

    /** An artefact grown at this tier, likelier of the elements the site holds most of. */
    static Loot pickLoot(RandomSource r, int tier, List<Kind> kinds) {
        int total = 0;
        int[] w = new int[Loot.values().length];
        for (Loot l : Loot.values()) {
            int n = 0;
            if (l.minTier <= tier) {
                n = 1;
                for (Kind k : kinds) {
                    n += k.element == l.element ? 3 : 0;
                }
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
