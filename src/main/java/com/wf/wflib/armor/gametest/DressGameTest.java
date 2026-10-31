package com.wf.wflib.armor.gametest;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.armor.ArmorConfig;
import com.wf.wflib.armor.api.ArmorContent;
import com.wf.wflib.armor.api.ArmorPreset;
import com.wf.wflib.armor.api.Dress;
import com.wf.wflib.core.api.WFLib;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** {@link Dress}: unaided equip/remove gating and the assisted routes. */
@PrefixGameTestTemplate(false)
public class DressGameTest {
    private static final String TEMPLATE = "empty";
    private static final int CHEST_MENU_SLOT = 6;

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        return p;
    }

    private static ItemStack suit() {
        return new ItemStack(ArmorContent.item(ArmorPreset.rl("exo_test")));
    }

    private static void selfDress(boolean on, Runnable body) {
        ArmorConfig.Values saved = ArmorConfig.get();
        ArmorConfig.override(new ArmorConfig.Values(saved.manageVanillaArmor(), saved.mobArmor(),
                saved.exposureIntervalTicks(), saved.persistIntervalTicks(), saved.fireTier(), saved.lavaTier(),
                saved.acuteFraction(), on, saved.assistTicks(), saved.standTicks()));
        try {
            body.run();
        } finally {
            ArmorConfig.override(saved);
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void selfDressIsOnByDefault(GameTestHelper helper) {
        ServerPlayer p = player(helper, "dress");
        p.setItemSlot(EquipmentSlot.CHEST, suit());
        helper.assertTrue(p.inventoryMenu.getSlot(CHEST_MENU_SLOT).mayPickup(p), "worn piece locked by default");
        p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        helper.assertTrue(p.inventoryMenu.getSlot(CHEST_MENU_SLOT).mayPlace(suit()), "placement refused by default");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void withoutSelfDressTheSlotsRefuseTheWearer(GameTestHelper helper) {
        selfDress(false, () -> {
            ServerPlayer p = player(helper, "dress");
            helper.assertFalse(p.inventoryMenu.getSlot(CHEST_MENU_SLOT).mayPlace(suit()), "placed unaided");
            p.setItemSlot(EquipmentSlot.CHEST, suit());
            helper.assertFalse(p.inventoryMenu.getSlot(CHEST_MENU_SLOT).mayPickup(p), "removed unaided");
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, suit());
            helper.assertTrue(p.getMainHandItem().getItem().use(helper.getLevel(), p,
                    net.minecraft.world.InteractionHand.MAIN_HAND).getResult()
                    == net.minecraft.world.InteractionResult.FAIL, "right-click equipped");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void anotherPlayerPutsOnAndTakesOff(GameTestHelper helper) {
        selfDress(false, () -> {
            ServerPlayer helperP = player(helper, "helper");
            ServerPlayer target = player(helper, "target");
            helperP.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, suit());
            helper.assertTrue(Dress.putOnSlot(helperP, target) == EquipmentSlot.CHEST, "cannot put on");
            Dress.putOn(helperP, target);
            helper.assertFalse(target.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "not worn");
            helper.assertTrue(helperP.getMainHandItem().isEmpty(), "hand kept the piece");
            helper.assertFalse(Dress.canTakeOff(target, target, EquipmentSlot.CHEST), "took itself off");
            Dress.takeOff(helperP, target, EquipmentSlot.CHEST);
            helper.assertTrue(target.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "still worn");
            helper.assertTrue(helperP.getInventory().contains(suit()), "not handed over");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void aWearerRemovesItOntoAStand(GameTestHelper helper) {
        selfDress(false, () -> {
            ServerPlayer p = player(helper, "dress");
            ArmorStand stand = new ArmorStand(helper.getLevel(), p.getX(), p.getY(), p.getZ());
            p.setItemSlot(EquipmentSlot.CHEST, suit());
            helper.assertTrue(Dress.canToStand(p, stand, EquipmentSlot.CHEST), "stand refused");
            Dress.toStand(p, stand, EquipmentSlot.CHEST);
            helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "still worn");
            helper.assertFalse(stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "not on the stand");
            helper.assertFalse(Dress.canToStand(p, stand, EquipmentSlot.CHEST), "stand slot reused");
        });
        helper.succeed();
    }
}
