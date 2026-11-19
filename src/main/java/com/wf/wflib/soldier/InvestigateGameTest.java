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

    /** A hostile mob in front of a soldier becomes a contact (checked in one tick: with no gun mod a soldier cannot run its brain on a contact). */
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aSoldierNoticesAHostileMob(GameTestHelper h) {
        var level = h.getLevel();
        var origin = net.minecraft.core.BlockPos.containing(h.absoluteVec(new Vec3(0, 0, 0)));
        int x = origin.getX(), z = origin.getZ(), y = 250;                       // an open strip of its own: the test arena has walls
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 10; dz++) {
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y - 1, z + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for (int dy = 0; dy < 5; dy++) level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y + dy, z + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        var spec = new SoldierSpec("mob_test", "MobTest", false, null, List.of(), Map.of(), 0);
        var p = Soldiers.spawn(level, new Vec3(x + 0.5, y, z + 0.5), 0, spec, null);
        h.assertTrue(p != null, "could not spawn test soldier");
        var mob = net.minecraft.world.entity.EntityType.HUSK.create(level);
        try {
            mob.moveTo(x + 0.5, y, z + 4.5);
            mob.setNoAi(true);
            level.addFreshEntity(mob);
            var bot = SoldierAi.botOf(p.getGameProfile().getName());
            bot.moving = true;                                   // looks where the body faces, not along its scan
            bot.range = 60;
            bot.threats.clear();
            bot.mobs = SoldierAi.nearbyMobs(p, bot);
            h.assertTrue(bot.mobs.contains(mob), "the husk was not found near the soldier");
            SoldierAi.perceiveMobs(p, bot, level.getGameTime());
            h.assertTrue(bot.threats.containsKey(SoldierAi.keyOf(mob)), "the husk was not noticed: " + bot.threats.keySet() + " " + bot.glimpses.keySet());
            mob.discard();
            SoldierAi.perceiveMobs(p, bot, level.getGameTime() + 1);
            h.assertTrue(bot.threats.isEmpty(), "a dead mob is still remembered");
            h.succeed();
        } finally {
            mob.discard();
            Soldiers.remove(p);
        }
    }
}
