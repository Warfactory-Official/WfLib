package com.wf.wflib.anomaly.api;

import com.wf.wflib.artifact.api.Element;
import com.wf.wflib.anomaly.api.AnomalyMix.Kind;
import com.wf.wflib.anomaly.api.AnomalyMix.Loot;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An anomaly site before any terrain: STALKER-scale (tens of blocks), a few anomalies set where the ground makes them
 * matter, a terrain motif and maybe a ruin. Pure, tested on its own ({@code SitePlanTest}).
 *
 * @param motif   what the ground does
 * @param overlay a ruin in it, or none
 * @param variant a free number the motif and overlay draw their own shape from (so one motif has many looks)
 */
public record SitePlan(int tier, Motif motif, @Nullable Overlay overlay, int radius, List<Kind> anomalies,
                       List<Loot> artefacts, long variant) {

    /**
     * The ground. Each leans to an element (its anomalies come up six times as often), opens at a tier, and offers
     * ceilings (branches, arches) for Burnt Fuzz and Grabbers or not.
     */
    public enum Motif {
        /** One big impact bowl, rim thrown up, sometimes a peak or a boulder in the middle. */
        CRATER(Element.GRAVITY, 1, false),
        /** A pocked field of small craters. */
        CRATER_FIELD(Element.GRAVITY, 1, false),
        /** A long, deep cleft with stepped ledges and maybe a natural bridge: the way across is the challenge. */
        RAVINE(Element.FIRE, 1, false),
        /** A scorched plain split by a network of narrow, hot cracks and smoking vents. */
        FISSURES(Element.FIRE, 1, false),
        /** Ground fused flat by discharges: scorch streaks, glassy spikes. */
        GLASS_FIELD(Element.ELECTRIC, 1, false),
        /** A sodden hollow: mud, stagnant pools, reeds of dead bush, bones. */
        BOG(Element.CHEMICAL, 1, false),
        /** A maze of boulders and jagged spires; the anomalies sit in the gaps. */
        BOULDER_FIELD(Element.GRAVITY, 2, false),
        /** A collapse: steep walls down to a flooded floor, rubble slopes. */
        SINKHOLE(Element.CHEMICAL, 2, false),
        /** Bark-stripped dead trees and fallen trunks; Burnt Fuzz hangs from the boughs. */
        DEAD_FOREST(Element.CHEMICAL, 2, true),
        /** Ground torn loose: tilted slabs, boulders hanging over the pits they left. */
        UPHEAVAL(Element.GRAVITY, 3, false),
        /** Rock arches thrown over a hollow, things hanging under them. */
        ARCHES(Element.CHEMICAL, 3, true),
        /** The Zone's heart: a shaft into the dark, ringed with weeping obsidian. */
        PIT(Element.EXOTIC, 4, false);

        public final Element element;
        public final int minTier;
        public final boolean ceilings;

        Motif(Element element, int minTier, boolean ceilings) {
            this.element = element;
            this.minTier = minTier;
            this.ceilings = ceilings;
        }
    }

    /** Ruins: what people left before the anomalies came. */
    public enum Overlay {
        /** A few houses of the biome's village, rotted and mossed; anomalies in the street. */
        VILLAGE_RUIN(1),
        /** A stalkers' camp that did not make it: tents, a cold fire, crates. */
        CAMP(1),
        /** A wooden watchtower, half fallen. */
        WATCHTOWER(1),
        /** A derailed train: a stretch of track, wagons on their sides. */
        TRAIN_WRECK(2),
        /** A rusted radio mast, guys and all. */
        RADIO_MAST(2),
        /** A temple: the jungle's or the desert's in their biomes, else a mossy stone ruin of steps and pillars. */
        TEMPLE(3),
        /** A concrete bunker hatch going down. */
        BUNKER(3);

        public final int minTier;

        Overlay(int minTier) {
            this.minTier = minTier;
        }
    }

    public static final int TIERS = AnomalyMix.TIERS;
    private static final int[] RADIUS_MIN = {16, 22, 28, 36};
    private static final int[] RADIUS_MAX = {24, 32, 40, 48};
    private static final int[] COUNT_MIN = {2, 3, 4, 5};
    private static final int[] COUNT_MAX = {3, 5, 6, 8};
    /** Chance a site has a ruin in it, by tier. */
    private static final float[] RUIN_CHANCE = {0.35F, 0.45F, 0.5F, 0.4F};
    /** Widest a site gets, blocks: its pieces' bounding boxes are this plus a margin round the middle. */
    public static final int MAX_RADIUS = 48;

    public static SitePlan roll(RandomSource r, int tier, SiteStyle style, @Nullable Motif motif,
                                @Nullable Overlay overlay) {
        int t = Math.max(1, Math.min(TIERS, tier));
        Motif m = motif != null ? motif : pickMotif(r, t, style);
        Overlay o = overlay != null ? overlay : r.nextFloat() < RUIN_CHANCE[t - 1] ? pickOverlay(r, t, m) : null;
        int radius = RADIUS_MIN[t - 1] + r.nextInt(RADIUS_MAX[t - 1] - RADIUS_MIN[t - 1] + 1);
        int count = COUNT_MIN[t - 1] + r.nextInt(COUNT_MAX[t - 1] - COUNT_MIN[t - 1] + 1);
        List<Kind> kinds = AnomalyMix.rollKinds(r, t, m.element, m.ceilings, count);
        return new SitePlan(t, m, o, radius, kinds, AnomalyMix.rollLoot(r, t, kinds), r.nextLong());
    }

    /** Open at the tier, a tier's own twice as likely as those it inherits, leaning to what suits the biome. */
    private static Motif pickMotif(RandomSource r, int tier, SiteStyle style) {
        double total = 0.0D;
        double[] w = new double[Motif.values().length];
        for (Motif m : Motif.values()) {
            w[m.ordinal()] = m.minTier > tier ? 0.0D : (m.minTier == tier && tier > 1 ? 2.0D : 1.0D) * style.lean(m);
            total += w[m.ordinal()];
        }
        double u = r.nextDouble() * total;
        for (Motif m : Motif.values()) {
            u -= w[m.ordinal()];
            if (u < 0.0D) {
                return m;
            }
        }
        return Motif.CRATER;
    }

    private static Overlay pickOverlay(RandomSource r, int tier, Motif motif) {
        List<Overlay> open = new ArrayList<>();
        for (Overlay o : Overlay.values()) {
            // A pit's heart and a ravine's cleft leave no room for a village.
            boolean fits = !(o == Overlay.VILLAGE_RUIN && (motif == Motif.PIT || motif == Motif.RAVINE
                    || motif == Motif.SINKHOLE));
            if (o.minTier <= tier && fits) {
                open.add(o);
            }
        }
        return open.get(r.nextInt(open.size()));
    }
}
