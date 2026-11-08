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
            h.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(helmet, helmetBefore)
                    && net.minecraft.world.item.ItemStack.isSameItemSameComponents(vest, vestBefore), "the armour changed on dying");
            if (net.neoforged.fml.ModList.get().isLoaded("corpse")) {
                var around = new net.minecraft.world.phys.AABB(p.blockPosition()).inflate(6);
                var corpses = h.getLevel().getEntities((net.minecraft.world.entity.Entity) null, around, e -> e.getType().builtInRegistryHolder().key().location().getNamespace().equals("corpse"));
                var kit = new StringBuilder();
                for (var c : corpses) kit.append(c.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
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
}
