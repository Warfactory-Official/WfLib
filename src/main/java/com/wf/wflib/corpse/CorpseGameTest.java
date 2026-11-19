package com.wf.wflib.corpse;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.corpse.api.CorpseEntity;
import com.wf.wflib.corpse.api.Corpses;
import com.wf.wflib.soldier.api.SoldierSpec;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;

/** A killed soldier leaves a corpse; a lead drags it, flint and steel burns it away. */
@PrefixGameTestTemplate(false)
public class CorpseGameTest {

    private static CorpseEntity corpseOf(GameTestHelper h, String id, String name, Vec3 at) {
        var spec = new SoldierSpec(id, name, false, null, List.of(), Map.of(EquipmentSlot.HEAD,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "combat_helmet")), 0);
        var soldier = Soldiers.spawn(h.getLevel(), h.absoluteVec(at), 0, spec, null);
        h.assertTrue(soldier != null, "could not spawn the soldier");
        soldier.hurt(soldier.damageSources().genericKill(), 1000f);
        Soldiers.remove(soldier);
        var found = h.getLevel().getEntitiesOfClass(CorpseEntity.class, new AABB(h.absolutePos(new net.minecraft.core.BlockPos((int) at.x, (int) at.y, (int) at.z))).inflate(4));
        h.assertTrue(found.size() == 1, found.size() + " corpses");
        return found.get(0);
    }

    private static ServerPlayer holder(GameTestHelper h, String name, Vec3 at) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.moveTo(h.absoluteVec(at));
        return p;
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 400)
    public static void aLeadDragsACorpseAlong(GameTestHelper h) {
        var corpse = corpseOf(h, "lead_corpse", "LeadCorpse", new Vec3(1, 2, 1));
        var player = holder(h, "LeadHolder", new Vec3(1, 2, 3));
        h.assertTrue(corpse.canHaveALeashAttachedToIt(), "a corpse takes a lead");
        corpse.setLeashedTo(player, true);
        h.assertTrue(corpse.getLeashHolder() == player, "the lead is held");
        Vec3[] start = new Vec3[1];
        h.startSequence()
                .thenIdle(30) // lands and settles
                .thenExecute(() -> {
                    start[0] = corpse.position();
                    h.assertTrue(com.wf.wflib.ragdoll.api.Ragdolls.active(corpse), "the body is simulating");
                    player.moveTo(player.getX(), player.getY(), player.getZ() + 9);
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    double moved = corpse.position().distanceTo(start[0]);
                    h.assertTrue(moved > 3.0, "the corpse followed the lead only " + moved + " blocks, holder " + player.position().distanceTo(corpse.position()) + " away, leashed " + corpse.isLeashed());
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 400)
    public static void aLeashedCorpseSurvivesSaving(GameTestHelper h) {
        var corpse = corpseOf(h, "save_corpse", "SaveCorpse", new Vec3(1, 2, 1));
        var player = holder(h, "SaveHolder", new Vec3(1, 2, 3));
        corpse.setLeashedTo(player, true);
        var tag = corpse.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        h.assertTrue(tag.contains("leash"), "the lead is written: " + tag.getAllKeys());
        h.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 600)
    public static void flintAndSteelBurnsACorpseAway(GameTestHelper h) {
        var corpse = corpseOf(h, "burn_corpse", "BurnCorpse", new Vec3(1, 2, 1));
        var player = holder(h, "BurnHolder", new Vec3(1, 2, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        h.assertTrue(!corpse.burning(), "not burning yet");
        corpse.interact(player, InteractionHand.MAIN_HAND);
        h.assertTrue(corpse.burning(), "flint and steel lights it");
        h.assertTrue(player.getMainHandItem().getDamageValue() == 1, "the steel is worn");
        h.succeedWhen(() -> h.assertTrue(corpse.isRemoved(), "the corpse is still there after burning"));
    }
}
