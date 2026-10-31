package com.wf.wflib.armor.gametest;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.armor.api.ArmorContent;
import com.wf.wflib.armor.api.ArmorResolution;
import com.wf.wflib.armor.api.exo.ArmorBattery;
import com.wf.wflib.armor.api.exo.Exo;
import com.wf.wflib.armor.api.exo.ExoConfig;
import com.wf.wflib.armor.api.exo.ExoPopup;
import com.wf.wflib.armor.api.exo.ExoProfiles;
import com.wf.wflib.armor.api.exo.ExoServer;
import com.wf.wflib.armor.api.exo.ExoSpec;
import com.wf.wflib.armor.api.exo.ExoStacks;
import com.wf.wflib.armor.api.exo.PartState;
import com.wf.wflib.backpack.api.Backpacks;
import com.wf.wflib.carry.api.Carry;
import com.wf.wflib.carry.api.Shoulder;
import com.wf.wflib.core.api.MenuSlots;
import com.wf.wflib.core.api.SlotLayout;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.damage.api.WFDamageTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Exoskeleton rules on a dev {@code exo_test} chest piece; pure math is {@code ExoResolverTest}. */
@PrefixGameTestTemplate(false)
public class ExoGameTest {
    private static final String TEMPLATE = "empty";

    /** FE bank keyed by stack identity: stands in for a battery item. */
    private static final class Bank implements ArmorBattery.Backend {
        long fe;

        @Override
        public long stored(ItemStack stack) {
            return fe;
        }

        @Override
        public long capacity(ItemStack stack) {
            return 100_000;
        }

        @Override
        public long extract(ItemStack stack, long amount, boolean simulate) {
            long got = Math.min(fe, amount);
            if (!simulate) {
                fe -= got;
            }
            return got;
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "exo"));
        p.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ExoServer.forget(p.getUUID());
        return p;
    }

    private static ItemStack suit() {
        return new ItemStack(ArmorContent.item(com.wf.wflib.armor.api.ArmorPreset.rl("exo_test")));
    }

    private static void with(Bank bank, ExoConfig.Values cfg, Runnable body) {
        ExoConfig.Values saved = ExoConfig.get();
        ArmorBattery.override(bank);
        ExoConfig.override(cfg);
        try {
            body.run();
        } finally {
            ArmorBattery.override(null);
            ExoConfig.override(saved);
        }
    }

    private static ExoConfig.Values cfg(boolean energy) {
        ExoConfig.Values d = ExoConfig.get();
        return new ExoConfig.Values(energy, ExoConfig.Unit.FE, d.euToFe(), d.recoilFloor(), d.swayFloor(),
                d.partAbsorbedShare(), d.lifeSupportDanger(), d.lifeSupportCooldownTicks(), 0.0D, d.repairHp());
    }

    private static DamageSource source(GameTestHelper helper) {
        return new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(WFDamageTypes.PHYSICAL));
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void aChargedSuitGrantsPoweredStats(GameTestHelper helper) {
        Bank bank = new Bank();
        bank.fe = 10_000;
        with(bank, cfg(true), () -> {
            ServerPlayer p = player(helper);
            ItemStack suit = suit();
            ExoStacks.setBattery(suit, new ItemStack(Items.STICK));
            p.setItemSlot(EquipmentSlot.CHEST, suit);
            ExoServer.recompute(p, true);
            helper.assertTrue(Exo.canSprint(p), "powered suit cannot sprint");
            helper.assertTrue(Math.abs(Exo.effect(p).recoilFactor() - 0.4) < 1e-9, "recoil " + Exo.effect(p).recoilFactor());
            helper.assertTrue(Exo.effect(p).carryBonusKg() == 30.0, "carry " + Exo.effect(p).carryBonusKg());
            helper.assertTrue(Exo.effect(p).groupBonus("grenades") == 4, "group limit");
            helper.assertTrue(bank.fe < 10_000, "no energy drawn");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void anEnergyCellPowersTheSuitAndDrains(GameTestHelper helper) {
        with(null, cfg(true), () -> {
            ServerPlayer p = player(helper);
            ItemStack cell = com.wf.wflib.armor.api.exo.EnergyCellItem.CELL.get().full();
            helper.assertTrue(ArmorBattery.accepts(cell), "cell not in battery tag");
            long before = ArmorBattery.stored(cell);
            ItemStack suit = suit();
            ExoStacks.setBattery(suit, cell);
            p.setItemSlot(EquipmentSlot.CHEST, suit);
            ExoServer.recompute(p, true);
            helper.assertTrue(Exo.canSprint(p), "cell-powered suit cannot sprint");
            helper.assertTrue(ArmorBattery.stored(ExoStacks.battery(p.getItemBySlot(EquipmentSlot.CHEST))) < before,
                    "cell not drained");
            helper.assertTrue(cell.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM)
                    .extractEnergy(1000, true) == 0, "cell discharges outside exo");
            ItemStack empty = new ItemStack(com.wf.wflib.armor.api.exo.EnergyCellItem.CELL.get());
            helper.assertTrue(ArmorBattery.stored(empty) == 0 && ArmorBattery.capacity(empty) == 400_000, "empty cell");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void anEmptyBatteryFallsBackToUnpoweredStats(GameTestHelper helper) {
        with(new Bank(), cfg(true), () -> {
            ServerPlayer p = player(helper);
            ItemStack suit = suit();
            ExoStacks.setBattery(suit, new ItemStack(Items.STICK));
            p.setItemSlot(EquipmentSlot.CHEST, suit);
            ExoServer.recompute(p, true);
            helper.assertTrue(!Exo.canSprint(p), "unpowered suit sprints");
            helper.assertTrue(Exo.effect(p).recoilFactor() == 1.0, "unpowered suit compensates recoil");
            helper.assertTrue(!Exo.effect(p).shoulder(), "unpowered suit shoulders");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void withEnergyOffTheSuitIsAlwaysPowered(GameTestHelper helper) {
        with(new Bank(), cfg(false), () -> {
            ServerPlayer p = player(helper);
            p.setItemSlot(EquipmentSlot.CHEST, suit());
            ExoServer.recompute(p, true);
            helper.assertTrue(Exo.canSprint(p) && Exo.effect(p).recoilFactor() < 1.0, "energy off but unpowered");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void aWreckedArmLocksTheTriggerAndTheLegsTheSprint(GameTestHelper helper) {
        with(new Bank(), cfg(false), () -> {
            ServerPlayer p = player(helper);
            ItemStack suit = suit();
            p.setItemSlot(EquipmentSlot.CHEST, suit);
            ExoSpec spec = ExoProfiles.of(suit);
            ExoStacks.damage(suit, spec, spec.partIndex("arms"), 90);
            ExoStacks.damage(suit, spec, spec.partIndex("legs"), 90);
            helper.assertTrue(ExoStacks.states(suit, spec)[0] == PartState.CRITICAL, "arms " + ExoStacks.states(suit, spec)[0]);
            ExoServer.recompute(p, false);
            helper.assertTrue(Exo.triggerLocked(p), "trigger free with critical arms");
            helper.assertTrue(!Exo.canSprint(p), "sprint with critical legs");
            helper.assertTrue(Exo.effect(p).recoilFactor() > 1.0, "critical arms do not worsen recoil");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void hitsWearThePartTheLimbFeeds(GameTestHelper helper) {
        with(new Bank(), cfg(false), () -> {
            ServerPlayer p = player(helper);
            ItemStack suit = suit();
            p.setItemSlot(EquipmentSlot.CHEST, suit);
            ExoSpec spec = ExoProfiles.of(suit);
            ArmorResolution.resolveSlot(p, EquipmentSlot.CHEST, "left_arm", source(helper), 20.0F, true);
            int arms = ExoStacks.hp(suit, spec, spec.partIndex("arms"));
            int legs = ExoStacks.hp(suit, spec, spec.partIndex("legs"));
            helper.assertTrue(arms < 100, "left arm hit left arms at " + arms);
            helper.assertTrue(legs == 100, "left arm hit wore the legs: " + legs);
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void aRepairItemRestoresTheWorstPart(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ItemStack suit = suit();
        ExoSpec spec = ExoProfiles.of(suit);
        ExoStacks.damage(suit, spec, spec.partIndex("core"), 60);
        ItemStack log = new ItemStack(Items.OAK_LOG, 2);
        helper.assertTrue(ExoStacks.repairWith(suit, spec, log), "log did not repair");
        helper.assertTrue(ExoStacks.hp(suit, spec, spec.partIndex("core")) == 40 + ExoConfig.get().repairHp(),
                "hp " + ExoStacks.hp(suit, spec, spec.partIndex("core")));
        helper.assertTrue(log.getCount() == 1, "repair item not consumed");
        helper.assertTrue(!ExoStacks.repairWith(suit, spec, new ItemStack(Items.STICK)), "stick repaired");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void lifeSupportDrinksAHealingPotionInDanger(GameTestHelper helper) {
        with(new Bank(), cfg(false), () -> {
            ServerPlayer p = player(helper);
            p.setItemSlot(EquipmentSlot.CHEST, suit());
            ExoServer.recompute(p, true);
            ItemStack potion = new ItemStack(Items.POTION);
            potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
            p.getInventory().setItem(10, potion);
            p.setHealth(4.0F);
            ExoServer.lifeSupport(p);
            helper.assertTrue(p.getHealth() > 4.0F, "not healed: " + p.getHealth());
            helper.assertTrue(p.getInventory().getItem(10).isEmpty() || p.getInventory().getItem(10).is(Items.GLASS_BOTTLE),
                    "potion kept");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void menuSlotGroupsDoNotOverlapAndPopupSlotsWriteTheGarment(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        SlotLayout.Placed bag = MenuSlots.range(p, Backpacks.SLOTS);
        SlotLayout.Placed popup = MenuSlots.range(p, ExoPopup.GROUP);
        helper.assertTrue(bag != null && popup != null, "groups not placed: " + bag + " " + popup);
        helper.assertTrue(bag.end() <= popup.first() || popup.end() <= bag.first(), "groups overlap");
        helper.assertTrue(p.inventoryMenu.slots.size() >= Math.max(bag.end(), popup.end()), "slots missing");
        for (int i = 0; i < popup.count(); i++) {
            helper.assertTrue(p.inventoryMenu.getSlot(popup.first() + i).index == popup.first() + i, "index drift");
        }
        ItemStack suit = suit();
        p.getInventory().setItem(10, suit);
        helper.assertTrue(!p.inventoryMenu.getSlot(popup.first()).isActive(), "popup slot active while closed");
        helper.assertTrue(ExoPopup.open(p, 10), "popup refused a suit");
        var batterySlot = p.inventoryMenu.getSlot(popup.first() + ExoPopup.BATTERY);
        helper.assertTrue(batterySlot.isActive(), "battery slot inactive with popup open");
        helper.assertTrue(!batterySlot.mayPlace(new ItemStack(Items.STICK)), "untagged item accepted as battery");
        batterySlot.set(new ItemStack(Items.STICK));
        helper.assertTrue(ExoStacks.battery(p.getInventory().getItem(10)).isEmpty() == false, "slot write missed the garment");
        ExoPopup.close(p);
        helper.assertTrue(!batterySlot.isActive(), "slot active after close");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void aShoulderingCarrierHoldsTheGunInTheMainHand(GameTestHelper helper) {
        with(new Bank(), cfg(false), () -> {
            ServerPlayer p = player(helper);
            p.setItemSlot(EquipmentSlot.CHEST, suit());
            ExoServer.recompute(p, true);
            Carry.set(p, new ItemStack(Items.ANVIL));
            p.setData(Shoulder.HELD, new ItemStack(Items.STICK));
            helper.assertTrue(Shoulder.shouldering(p), "not shouldering");
            helper.assertTrue(p.getMainHandItem().is(Items.STICK), "main hand " + p.getMainHandItem());
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            ExoServer.recompute(p, true);
            helper.assertTrue(!p.getMainHandItem().is(Items.STICK), "gun held without a shoulder rig");
            Carry.set(p, ItemStack.EMPTY);
        });
        helper.succeed();
    }
}
