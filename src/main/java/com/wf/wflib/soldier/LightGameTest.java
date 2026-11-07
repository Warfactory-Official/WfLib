package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.api.SoldierSpec;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** The light decision against a real level: night and day must read differently where a soldier stands. */
@PrefixGameTestTemplate(false)
public class LightGameTest {
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 200)
    public static void aSoldierWantsItsLightAtNightAndNotByDay(GameTestHelper h) {
        var level = h.getLevel();
        long was = level.getDayTime();
        var spec = new SoldierSpec("light_test", "LightTest", false, null, List.of(), Map.of(), 0);
        var p = Soldiers.spawn(level, h.absoluteVec(new Vec3(1, 2, 1)), 0, spec, null);
        h.assertTrue(p != null, "could not spawn test soldier");
        var bot = SoldierAi.botOf(p.getGameProfile().getName());
        level.setDayTime(18000);
        h.runAfterDelay(20, () -> {
            boolean night = SoldierAi.wantsLight(p, bot);
            boolean wearing = Headlamp.worn(p);
            Headlamp.take(p);
            boolean bare = SoldierAi.wantsLight(p, bot);
            level.setDayTime(6000);
            h.runAfterDelay(20, () -> {
                boolean day = SoldierAi.wantsLight(p, bot);
                bot.light = "on";
                boolean forcedOn = SoldierAi.wantsLight(p, bot);
                bot.light = "off";
                boolean forcedOff = SoldierAi.wantsLight(p, bot);
                level.setDayTime(was);
                Soldiers.remove(p);
                h.assertTrue(night && wearing, "no light wanted at midnight, or the soldier did not spawn wearing a headlamp");
                h.assertTrue(Headlamp.curios() ? !bare : bare, "a soldier without a headlamp still wants its light");
                h.assertTrue(!day, "light wanted at noon");
                h.assertTrue(forcedOn && !forcedOff, "the on / off orders are ignored");
                h.succeed();
            });
        });
    }
}
