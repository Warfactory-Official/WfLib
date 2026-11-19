package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.api.SoldierSpec;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/**
 * A soldier is killed, then keeps being hit in the same tick: death must happen once, and the corpse's armour must not
 * lose condition to hits that landed after it. Spawned, hit and removed within one tick (the test server has no gun mod).
 */
@PrefixGameTestTemplate(false)
public class DeathGameTest {
    private static final ResourceLocation HELMET = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "combat_helmet"),
            VEST = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "plate_carrier");

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aSoldierDiesOnceAndItsArmourIsNotSpentOnTheCorpse(GameTestHelper h) {
        var spec = new SoldierSpec("death_test", "DeathTest", false, null, List.of(),
                Map.of(EquipmentSlot.HEAD, HELMET, EquipmentSlot.CHEST, VEST), 0);
        var p = Soldiers.spawn(h.getLevel(), h.absoluteVec(new Vec3(1, 2, 1)), 0, spec, null);
        h.assertTrue(p != null, "could not spawn test soldier");
        int[] deaths = {0}, hitsAfterDeath = {0};
        Consumer<LivingDeathEvent> onDeath = e -> { if (e.getEntity() == p) deaths[0]++; };
        Consumer<LivingIncomingDamageEvent> onHit = e -> { if (e.getEntity() == p && p.isDeadOrDying()) hitsAfterDeath[0]++; };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, onHit);
        try {
            var helmet = p.getItemBySlot(EquipmentSlot.HEAD);
            var vest = p.getItemBySlot(EquipmentSlot.CHEST);
            h.assertTrue(!helmet.isEmpty() && !vest.isEmpty(), "the soldier is not wearing the test armour");
            var helmetBefore = helmet.copy();
            var vestBefore = vest.copy();
            p.hurt(p.damageSources().genericKill(), 1000f);      // spawn protection holds off everything else
            h.assertTrue(p.isDeadOrDying(), "the kill did not land");
            var src = p.damageSources().generic();
            for (int i = 0; i < 5; i++) p.hurt(src, 1000f);
            h.assertTrue(deaths[0] == 1, "died " + deaths[0] + " times");
            h.assertTrue(hitsAfterDeath[0] == 0, hitsAfterDeath[0] + " hits were resolved against the corpse");
            // the stacks themselves: the death empties the slots, and the pieces go on as the dropped loot
            h.assertTrue(ItemStack.isSameItemSameComponents(helmet, helmetBefore)
                    && ItemStack.isSameItemSameComponents(vest, vestBefore), "the armour changed on dying");
            if (ModList.get().isLoaded("corpse")) {
                var around = new AABB(p.blockPosition()).inflate(6);
                var corpses = h.getLevel().getEntities((Entity) null, around, e -> e.getType().builtInRegistryHolder().key().location().getNamespace().equals("corpse"));
                var kit = new StringBuilder();
                for (var c : corpses) kit.append(c.saveWithoutId(new CompoundTag()));
                h.assertTrue(corpses.size() == 1, corpses.size() + " corpses; " + kit);
                h.assertTrue(kit.toString().contains("combat_helmet") && kit.toString().contains("plate_carrier"), "corpse kit lost the armour: " + kit);
                h.assertTrue(!kit.toString().contains("minecraft:damage"), "corpse armour is damaged: " + kit);
            }
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(onDeath);
            NeoForge.EVENT_BUS.unregister(onHit);
            Soldiers.remove(p);
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aSoldierKillIsReportedWithItsKillerTeamAndDistance(GameTestHelper h) {
        var red = new SoldierSpec("kill_red", "KillRed", false, null, List.of(), Map.of(), 0);
        var blue = new SoldierSpec("kill_blue", "KillBlue", false, null, List.of(), Map.of(), 0);
        var shooter = Soldiers.spawn(h.getLevel(), h.absoluteVec(new Vec3(1, 2, 1)), 0, red, null);
        var victim = Soldiers.spawn(h.getLevel(), h.absoluteVec(new Vec3(4, 2, 1)), 0, blue, null);
        h.assertTrue(shooter != null && victim != null, "could not spawn the test soldiers");
        var seen = new java.util.ArrayList<com.wf.wflib.soldier.api.SoldierKillEvent>();
        Consumer<com.wf.wflib.soldier.api.SoldierKillEvent> on = seen::add;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, com.wf.wflib.soldier.api.SoldierKillEvent.class, on);
        try {
            // generic_kill passes spawn protection; naming the shooter as the cause makes it theirs
            victim.hurt(new net.minecraft.world.damagesource.DamageSource(victim.damageSources().genericKill().typeHolder(), shooter), 1000f);
            h.assertTrue(seen.size() == 1, "expected one kill event, got " + seen.size());
            var e = seen.get(0);
            h.assertTrue(e.victim() == victim && e.victimTeam().equals("kill_blue"), "wrong victim " + e.victimTeam());
            h.assertTrue(e.killer() == shooter && "kill_red".equals(e.killerTeam()), "wrong killer " + e.killerTeam());
            h.assertTrue(!e.killedByPlayer(), "a soldier is not a real player");
            h.assertTrue(Math.abs(e.distance() - 3.0) < 0.5, "distance " + e.distance());
            h.assertTrue(e.weapon() == null, "bare hands should report no weapon, got " + e.weapon());
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(on);
            Soldiers.remove(shooter);
            Soldiers.remove(victim);
        }
    }
}
