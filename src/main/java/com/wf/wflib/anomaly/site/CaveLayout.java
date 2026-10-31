package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.api.AnomalyMix.Kind;
import com.wf.wflib.anomaly.api.AnomalyMix.Loot;
import com.wf.wflib.anomaly.site.SiteLayout.LootSpot;
import com.wf.wflib.anomaly.site.SiteLayout.Placed;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.wf.wflib.anomaly.api.AnomalyMix;
import com.wf.wflib.anomaly.api.CavePlan;

/**
 * Chamber of a cave field: footprint, floor and dome as functions of (x, z), and where anomalies and artefacts go,
 * from the plan, a middle and a floor height alone (every chunk works out the same). Floor cell {@code floor(x, z)}
 * is solid; air from there +1 up to {@code ceiling(x, z)}, solid.
 */
public final class CaveLayout {
    private static final double[] SPACING = {7.0D, 6.0D, 5.0D, 4.0D};
    /** Narrowest the chamber gets, floor to ceiling, at the rim. */
    static final int RIM_HEIGHT = 4;

    final CavePlan plan;
    final int cx;
    final int cz;
    final int y0;
    private final SimplexNoise noise;
    public final List<Placed> anomalies = new ArrayList<>();
    public final List<LootSpot> loot = new ArrayList<>();

    private CaveLayout(CavePlan plan, int cx, int cz, int y0) {
        this.plan = plan;
        this.cx = cx;
        this.cz = cz;
        this.y0 = y0;
        this.noise = new SimplexNoise(RandomSource.create(plan.variant() ^ 0x5DEECE66DL));
    }

    public static CaveLayout build(CavePlan plan, int cx, int cz, int y0) {
        CaveLayout l = new CaveLayout(plan, cx, cz, y0);
        RandomSource r = RandomSource.create(plan.variant());
        l.placeAnomalies(r);
        l.placeLoot(r);
        return l;
    }

    public List<Placed> anomalies() {
        return anomalies;
    }

    public List<LootSpot> loot() {
        return loot;
    }

    /** Everything the field writes, with room round the rim. */
    public BoundingBox box() {
        int r = CavePlan.MAX_RADIUS + 6;
        return new BoundingBox(cx - r, y0 - 12, cz - r, cx + r, y0 + plan.height() + 8, cz + r);
    }

    /** Distance from the middle in rim radii (ragged); inside < 1. */
    double u(int x, int z) {
        double dx = x + 0.5D - cx;
        double dz = z + 0.5D - cz;
        double a = Math.atan2(dz, dx);
        double rim = plan.radius() * (1.0D + 0.22D * noise.getValue(Math.cos(a) * 1.4D + 7.0D, Math.sin(a) * 1.4D - 3.0D));
        return Math.sqrt(dx * dx + dz * dz) / rim;
    }

    /** Widest the footprint reaches from the middle. */
    int reach() {
        return (int) Math.ceil(plan.radius() * 1.25D) + 1;
    }

    int floor(int x, int z) {
        return y0 + (int) Math.round(1.5D * noise.getValue(x / 9.0D + 31.0D, z / 9.0D - 17.0D));
    }

    int ceiling(int x, int z) {
        double u = Math.min(1.0D, u(x, z));
        int dome = (int) Math.round((plan.height() - RIM_HEIGHT) * (1.0D - u * u)
                + 0.8D * noise.getValue(x / 5.0D, z / 5.0D + 99.0D));
        return floor(x, z) + RIM_HEIGHT + Math.max(0, dome);
    }

    private void placeAnomalies(RandomSource r) {
        double spacing = SPACING[plan.tier() - 1];
        for (Kind k : plan.anomalies()) {
            double reach = k.spot == AnomalyMix.Spot.FIELD ? 0.3D : 0.8D;
            for (int tries = 0; tries < 80; tries++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double d = Math.sqrt(r.nextDouble()) * reach * plan.radius() * 0.9D;
                int x = cx + (int) Math.round(Math.cos(a) * d);
                int z = cz + (int) Math.round(Math.sin(a) * d);
                if (u(x, z) > reach) {
                    continue;
                }
                BlockPos at = k.spot == AnomalyMix.Spot.CEILING ? new BlockPos(x, ceiling(x, z) - 1, z)
                        : new BlockPos(x, floor(x, z) + 1, z);
                boolean clear = true;
                for (Placed p : anomalies) {
                    clear &= Math.hypot(p.pos().getX() - x, p.pos().getZ() - z) >= spacing;
                }
                if (clear && ceiling(x, z) - floor(x, z) >= RIM_HEIGHT + 1) {
                    anomalies.add(new Placed(k, at));
                    break;
                }
            }
        }
    }

    /** Hardest anomaly's neighbourhood first, one artefact each in turn, on the floor within a few blocks. */
    private void placeLoot(RandomSource r) {
        if (anomalies.isEmpty() || plan.artefacts().isEmpty()) {
            return;
        }
        List<Placed> order = new ArrayList<>(anomalies);
        order.sort(Comparator.comparingInt((Placed p) -> -p.kind().rank));
        int n = 0;
        for (Loot l : plan.artefacts()) {
            Placed anchor = order.get(n++ % order.size());
            BlockPos at = null;
            for (int tries = 0; tries < 24 && at == null; tries++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double out = 2.0D + r.nextDouble() * 2.5D;
                int x = anchor.pos().getX() + (int) Math.round(Math.cos(a) * out);
                int z = anchor.pos().getZ() + (int) Math.round(Math.sin(a) * out);
                if (u(x, z) < 0.92D) {
                    at = new BlockPos(x, floor(x, z) + 1, z);
                }
            }
            if (at == null) {
                at = new BlockPos(anchor.pos().getX(), floor(anchor.pos().getX(), anchor.pos().getZ()) + 1,
                        anchor.pos().getZ());
            }
            loot.add(new LootSpot(l, at, anchor.pos()));
        }
    }
}
