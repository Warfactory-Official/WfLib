package com.wf.wflib.drone.gametest;

import com.wf.wflib.ammo.api.AmmoItems;
import com.wf.wflib.drone.api.StrappedExplosive;
import com.wf.wflib.warhead.gametest.ThermobaricGameTest.Lane;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.wf.wflib.core.api.WFLib;

/** Strapped cartridge mass; released cartridge falls onto a victim. */
@PrefixGameTestTemplate(false)
public class StrappedExplosiveGameTest {
    private static final String TEMPLATE = "empty";

    private static ResourceLocation wflib(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    /** Strapped mass = calibre - default projectile + variant's; tag covers rocket + mortar + VOG. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void strappedMassFollowsTheVariant(GameTestHelper helper) {
        String[][] cases = {{"rpg7/heat", "2.6"}, {"rpg7/thermobaric", "4.5"}, {"rpg7/frag", "1.75"},
                {"81mm/he", "4.1"}, {"60mm/he", "1.72"}, {"vog25/he", "0.25"}, {"30x29/he", "0.35"}};
        for (String[] c : cases) {
            StrappedExplosive s = StrappedExplosive.of(AmmoItems.stack(wflib(c[0]), 1));
            helper.assertTrue(s != null, c[0] + " not strappable");
            helper.assertTrue(Math.abs(s.massKg() - Float.parseFloat(c[1])) < 0.005f,
                    c[0] + " mass " + s.massKg() + ", want " + c[1]);
        }
        helper.assertTrue(StrappedExplosive.of(AmmoItems.stack(wflib("40x46/he"), 1)) == null,
                "40x46 strappable");
        helper.succeed();
    }

    /** OG-7V let go 6 blocks above a golem: falls, hits, fires its warhead. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, timeoutTicks = 200)
    public static void aReleasedChargeFallsOntoItsTarget(GameTestHelper helper) {
        Lane lane = new Lane(helper, 2);
        IronGolem[] golem = new IronGolem[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(lane.ticking(), "lane loading"))
                .thenExecute(() -> {
                    golem[0] = lane.golem(lane.centre());
                    StrappedExplosive.of(AmmoItems.stack(wflib("rpg7/frag"), 1)).release(lane.level,
                            golem[0].position().add(0, 6, 0), Vec3.ZERO, null, null);
                })
                .thenWaitUntil(() -> helper.assertTrue(golem[0].getHealth() < golem[0].getMaxHealth(),
                        "golem untouched"))
                .thenExecute(lane::release)
                .thenSucceed();
    }
}
