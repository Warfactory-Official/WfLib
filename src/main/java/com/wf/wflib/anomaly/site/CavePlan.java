package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.site.AnomalyMix.Element;
import com.wf.wflib.anomaly.site.AnomalyMix.Kind;
import com.wf.wflib.anomaly.site.AnomalyMix.Loot;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A cave anomaly field before any rock: chamber size, its anomalies (floor and ceiling kinds both) and exactly the
 * artefacts the world's budget gave it. Pure.
 *
 * @param height dome height at the middle, blocks
 */
public record CavePlan(int tier, int radius, int height, List<Kind> anomalies, List<Loot> artefacts, long variant) {
    private static final int[] RADIUS_MIN = {18, 22, 26, 30};
    private static final int[] RADIUS_MAX = {24, 28, 32, 38};
    private static final int[] HEIGHT = {9, 10, 12, 14};
    private static final int[] COUNT_MIN = {3, 4, 5, 6};
    private static final int[] COUNT_MAX = {4, 6, 8, 10};
    /** Widest a field gets, blocks. */
    public static final int MAX_RADIUS = RADIUS_MAX[3];
    public static final int MIN_HEIGHT = 6;

    /** {@code allowed} filters artefact draws (redrawn while refused). */
    public static CavePlan roll(RandomSource r, int tier, int artefacts, Predicate<Loot> allowed) {
        int t = Math.max(1, Math.min(AnomalyMix.TIERS, tier));
        int radius = RADIUS_MIN[t - 1] + r.nextInt(RADIUS_MAX[t - 1] - RADIUS_MIN[t - 1] + 1);
        int count = COUNT_MIN[t - 1] + r.nextInt(COUNT_MAX[t - 1] - COUNT_MIN[t - 1] + 1);
        Element lean = Element.values()[r.nextInt(Element.values().length)];
        List<Kind> kinds = AnomalyMix.rollKinds(r, t, lean, true, count);
        List<Loot> loot = new ArrayList<>(artefacts);
        for (int i = 0; i < artefacts; i++) {
            Loot l;
            do {
                l = AnomalyMix.pickLoot(r, t, kinds);
            } while (!allowed.test(l));
            loot.add(l);
        }
        return new CavePlan(t, radius, HEIGHT[t - 1], List.copyOf(kinds), List.copyOf(loot), r.nextLong());
    }

    public CavePlan withHeight(int h) {
        return new CavePlan(tier, radius, h, anomalies, artefacts, variant);
    }
}
