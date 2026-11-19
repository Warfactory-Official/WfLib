package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.api.SiteSpec;
import com.wf.wflib.soldier.api.Sites;
import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierSpec;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** An operation site raises its squads, shrinks as they die without replacing them, and cleans up after itself. */
@PrefixGameTestTemplate(false)
public class SiteGameTest {
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aSiteRaisesItsSquadLosesSoldiersForGoodAndCleansUp(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var oldRaiser = Garrisons.raiser;
        var oldVariants = Garrisons.variants;
        Garrisons.raiser = (lvl, unit, at, yaw) -> Soldiers.spawn(lvl, at, yaw,
                new SoldierSpec("site_test", "SiteTest", false, null, List.of(), Map.of(), 0), null);
        Garrisons.variants = s -> List.of("alpha");
        var site = Sites.create(level, "site_test_a", h.absoluteVec(new Vec3(4, 2, 4)),
                new SiteSpec(12, List.of(new SiteSpec.Squad(List.of("alpha"), 3, SoldierOrder.Kind.GUARD))));
        try {
            h.assertTrue(site.remaining() == 3 && !site.cleared(), "a fresh site is not full: " + site.remaining());
            h.assertTrue(Sites.get(server, "site_test_a").isPresent(), "the site cannot be found again by its id");
            boolean refused = false;
            try {
                Sites.create(level, "site_test_a", Vec3.ZERO, new SiteSpec(8, List.of(new SiteSpec.Squad(List.of(), 1, SoldierOrder.Kind.GUARD))));
            } catch (IllegalArgumentException e) {
                refused = true;
            }
            h.assertTrue(refused, "a second site took an id that was in use");

            Garrisons.pass(server);
            h.assertTrue(site.members().size() == 3, "raised " + site.members().size() + " of 3");

            var victim = site.members().get(0);
            victim.hurt(new DamageSource(victim.damageSources().genericKill().typeHolder()), 1000f);
            h.assertTrue(victim.isDeadOrDying(), "the kill did not land");
            h.assertTrue(site.remaining() == 2, "remaining after one death: " + site.remaining());
            Garrisons.pass(server);
            h.assertTrue(site.members().size() == 2, "a dead soldier was replaced: " + site.members().size());
            h.assertTrue(site.bounds().contains(site.centre()), "bounds do not hold the centre");
            h.assertTrue(site.squads() == 1 && site.squadRemaining(0) == 2 && site.squadRemaining(5) == 0, "squad counts are off");

            site.remove();
            h.assertTrue(site.members().isEmpty() && Sites.get(server, "site_test_a").isEmpty(), "the site left something behind");
            h.succeed();
        } finally {
            Garrisons.raiser = oldRaiser;
            Garrisons.variants = oldVariants;
            site.remove();
        }
    }
}
