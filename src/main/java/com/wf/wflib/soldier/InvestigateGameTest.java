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

/** A heard shot sends a soldier to look, in the open as well as behind cover. */
@PrefixGameTestTemplate(false)
public class InvestigateGameTest {
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aSoldierGoesToLookAtAShotAndStopsWhenPinned(GameTestHelper h) {
        var level = h.getLevel();
        var spec = new SoldierSpec("look_test", "LookTest", false, null, List.of(), Map.of(), 0);
        var p = Soldiers.spawn(level, h.absoluteVec(new Vec3(1, 2, 1)), 0, spec, null);
        h.assertTrue(p != null, "could not spawn test soldier");
        try {
            var bot = SoldierAi.botOf(p.getGameProfile().getName());
            long now = level.getGameTime();
            bot.anchor = p.position();
            bot.mode = SoldierAi.Mode.HOLD;
            bot.holdRadius = 6;
            bot.mayAdvance = true;
            bot.arrived = -1000;
            var shot = SoldierAi.Threat.told();
            shot.eye = p.position().add(0, 1.62, 30);
            shot.seen = now;
            shot.sound = true;
            shot.soundSince = now - 100;
            bot.threats.put("shooter", shot);

            Vec3 goal = SoldierAi.goal(level.getServer(), p, bot, now);
            h.assertTrue(goal != null && bot.probe != null, "a heard shot 30 blocks away sent nobody to look");
            h.assertTrue(goal.z - p.getZ() > 4, "the soldier is not heading toward the shot: " + goal);

            bot.suppression = 0.8f;                        // under fire: stay put instead
            SoldierAi.goal(level.getServer(), p, bot, now + 1);
            h.assertTrue(bot.probe == null, "a pinned soldier kept walking out to look");
            h.succeed();
        } finally {
            Soldiers.remove(p);
        }
    }
}
