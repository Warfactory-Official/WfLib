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

/**
 * The light decision against a real level: night and day must read differently where a soldier stands. A soldier is
 * spawned, read and removed within one tick: the test server has no gun mod, so a soldier that got an AI tick would
 * crash it. The waiting for the sky to change happens while there is no soldier.
 */
@PrefixGameTestTemplate(false)
public class LightGameTest {
    private record Reading(boolean wants, boolean wearing, boolean bare, boolean forcedOn, boolean forcedOff, int brightness,
                           int sky, String curios) {}

    private static Reading read(GameTestHelper h) {
        var spec = new SoldierSpec("light_test", "LightTest", false, null, List.of(), Map.of(), 0);
        var p = Soldiers.spawn(h.getLevel(), h.absoluteVec(new Vec3(1, 2, 1)), 0, spec, null);
        h.assertTrue(p != null, "could not spawn test soldier");
        try {
            var bot = SoldierAi.botOf(p.getGameProfile().getName());
            boolean wants = SoldierAi.wantsLight(p, bot), wearing = Headlamp.worn(p);
            int brightness = p.level().getMaxLocalRawBrightness(net.minecraft.core.BlockPos.containing(p.getEyePosition())),
                    sky = p.level().getSkyDarken();
            String curios = Headlamp.describe(p);
            bot.light = "on";
            boolean forcedOn = SoldierAi.wantsLight(p, bot);
            bot.light = "off";
            boolean forcedOff = SoldierAi.wantsLight(p, bot);
            bot.light = "auto";
            Headlamp.take(p);
            return new Reading(wants, wearing, SoldierAi.wantsLight(p, bot), forcedOn, forcedOff, brightness, sky, curios);
        } finally {
            Soldiers.remove(p);
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 200)
    public static void aSoldierWantsItsLightAtNightAndNotByDay(GameTestHelper h) {
        var level = h.getLevel();
        long was = level.getDayTime();
        level.setDayTime(18000);
        h.runAfterDelay(20, () -> {
            Reading night = read(h);
            level.setDayTime(6000);
            h.runAfterDelay(20, () -> {
                Reading day = read(h);
                level.setDayTime(was);
                h.assertTrue(night.wants && night.wearing, "midnight: wants " + night.wants + ", wearing " + night.wearing + ", brightness " + night.brightness
                        + ", skyDarken " + night.sky + ", curios: " + night.curios + " (noon: " + day.brightness + ", " + day.sky + ")");
                h.assertTrue(Headlamp.curios() ? !night.bare : night.bare, "a soldier without a headlamp still wants its light");
                h.assertTrue(!day.wants, "light wanted at noon");
                h.assertTrue(night.forcedOn && day.forcedOn && !night.forcedOff && !day.forcedOff, "the on / off orders are ignored");
                h.succeed();
            });
        });
    }

    /** A worn headlamp switches on and off by the key's toggle, and the state lives on the stack. */
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aWornHeadlampSwitchesOnAndOff(GameTestHelper h) {
        if (!Headlamp.curios()) {
            h.succeed();
            return;
        }
        var spec = new SoldierSpec("lamp_toggle", "LampToggle", false, null, List.of(), Map.of(), 0);
        var p = Soldiers.spawn(h.getLevel(), h.absoluteVec(new Vec3(1, 2, 1)), 0, spec, null);
        h.assertTrue(p != null, "could not spawn test soldier");
        try {
            h.assertTrue(!Headlamp.on(p), "lamp on before being switched");
            h.assertTrue(Headlamp.toggle(p) && Headlamp.on(p), "the toggle did not switch the lamp on");
            h.assertTrue(Headlamp.toggle(p) && !Headlamp.on(p), "the toggle did not switch the lamp off");
            Headlamp.take(p);
            h.assertTrue(!Headlamp.toggle(p), "toggled a lamp that is not worn");
            h.succeed();
        } finally {
            Soldiers.remove(p);
        }
    }

    /** A soldier that has lost its lamp is issued another: every soldier carries one. */
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aSoldierWithoutALampIsIssuedOne(GameTestHelper h) {
        if (!Headlamp.curios()) {
            h.succeed();
            return;
        }
        var spec = new SoldierSpec("lamp_heal", "LampHeal", false, null, List.of(), Map.of(), 0);
        var p = Soldiers.spawn(h.getLevel(), h.absoluteVec(new Vec3(1, 2, 1)), 0, spec, null);
        h.assertTrue(p != null, "could not spawn test soldier");
        try {
            h.assertTrue(Headlamp.wearing(p), "a fresh soldier has no headlamp");
            Headlamp.take(p);
            h.assertTrue(!Headlamp.wearing(p), "the lamp did not come off");
            p.tickCount = 40;
            Headlamp.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            h.assertTrue(Headlamp.wearing(p), "a soldier without a lamp was not issued one");
            h.succeed();
        } finally {
            Soldiers.remove(p);
        }
    }
}
