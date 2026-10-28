package com.wf.wflib.anomaly.gametest;

import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.site.SiteLayout;
import com.wf.wflib.anomaly.site.SitePlan;
import com.wf.wflib.anomaly.site.SiteStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Site layouts on a made-up terrain (blocks need a running game, so this is a GameTest, but it writes nothing):
 * every motif and ruin, every tier it opens at, lays out the same twice, with anomalies spaced and inside the site.
 */
@GameTestHolder(WFLib.MODID)
@PrefixGameTestTemplate(false)
public class SiteLayoutGameTest {

    /** Gentle rolling ground at y 64-70, no water. */
    private static final SiteLayout.Terrain HILLS = new SiteLayout.Terrain() {
        @Override
        public int ground(int x, int z) {
            return 67 + (int) Math.round(3.0D * Math.sin(x / 23.0D) * Math.cos(z / 31.0D));
        }

        @Override
        public boolean wet(int x, int z) {
            return false;
        }
    };

    @GameTest(template = "empty")
    public static void everyMotifLaysOutSpacedAnomaliesTheSameEveryTime(GameTestHelper helper) {
        for (SitePlan.Motif motif : SitePlan.Motif.values()) {
            for (int tier = motif.minTier; tier <= SitePlan.TIERS; tier++) {
                for (long seed = 0; seed < 6; seed++) {
                    SitePlan plan = SitePlan.roll(RandomSource.create(seed * 31 + tier), tier, SiteStyle.PLAINS,
                            motif, null);
                    SiteLayout a = SiteLayout.build(plan, 1000, -2000, HILLS, SiteStyle.PLAINS, id -> null);
                    SiteLayout b = SiteLayout.build(plan, 1000, -2000, HILLS, SiteStyle.PLAINS, id -> null);
                    String where = motif + " t" + tier + " seed " + seed;
                    helper.assertTrue(a.anomalies().equals(b.anomalies()), where + ": not deterministic");
                    helper.assertFalse(a.anomalies().isEmpty(), where + ": no anomalies");
                    for (SiteLayout.Placed p : a.anomalies()) {
                        BlockPos q = p.pos();
                        helper.assertTrue(Math.hypot(q.getX() - 1000, q.getZ() + 2000) <= plan.radius() + 1,
                                where + ": " + q + " outside");
                        for (SiteLayout.Placed o : a.anomalies()) {
                            helper.assertTrue(o == p || o.pos().distSqr(q) >= 25, where + ": " + q + " crowds "
                                    + o.pos());
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRuinLaysOutInEveryStyle(GameTestHelper helper) {
        for (SitePlan.Overlay ruin : SitePlan.Overlay.values()) {
            for (SiteStyle style : SiteStyle.values()) {
                SitePlan plan = SitePlan.roll(RandomSource.create(9L), Math.max(ruin.minTier, 2), style,
                        SitePlan.Motif.CRATER_FIELD, ruin);
                SiteLayout l = SiteLayout.build(plan, 0, 0, HILLS, style, id -> null);
                helper.assertFalse(l.anomalies().isEmpty(), ruin + " in " + style + ": no anomalies");
            }
        }
        helper.succeed();
    }
}
