package com.wf.wflib.radiation.gametest;

import com.wf.wflib.hazard.HazardConfig;
import com.wf.wflib.hazard.api.ContaminationUtil;
import com.wf.wflib.hazard.api.HazardContent;
import com.wf.wflib.hazard.api.LivingHazardProps;
import com.wf.wflib.radiation.api.RadiationConfig;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import com.wf.wflib.core.api.WFLib;

/**
 * NTM NEXT NeutronActivationServerTests. Mock server players join the player list => own batches, away from
 * husk rows they would despawn; every one is removed again.
 */
@PrefixGameTestTemplate(false)
public class NeutronActivationGameTest {

    private static final String TEMPLATE = "empty";
    private static final double HOT_FIELD = 1000D;
    private static final double COLD_FIELD = 10D;
    private static final int CARGO_SLOT = 20;

    private static @Nullable Float activation(ItemStack stack) {
        return stack.get(HazardContent.NEUTRON_ACTIVATION.get());
    }

    /** Every payload id reads as negotiated: no client here to negotiate with. */
    private static final Set<ResourceLocation> ANY_CHANNEL = new AbstractSet<>() {
        @Override
        public boolean contains(Object o) {
            return true;
        }

        @Override
        public Iterator<ResourceLocation> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }
    };

    /**
     * {@code GameTestHelper.makeMockServerPlayerInLevel} with the connection's channels pre-accepted: its bare
     * embedded connection negotiated nothing => the first login payload (AHF, attachment sync) throws.
     */
    public static ServerPlayer mockServerPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        connection.channel().attr(ChannelAttributes.ADHOC_CHANNELS).set(ANY_CHANNEL);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    public static void remove(ServerPlayer player) {
        player.server.getPlayerList().remove(player);
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = HazardGameTest.BATCH)
    public static void neutronActivationAccumulatesOnASingleItem(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STICK);
        helper.assertTrue(ContaminationUtil.neutronActivateItem(stack, 1F, 1F),
                "the first activation must report the stack as changed");
        helper.assertTrue(activation(stack) != null && activation(stack) == 1F,
                "one activation of 1.0 must store 1.0, got " + activation(stack));
        ContaminationUtil.neutronActivateItem(stack, 1F, 1F);
        helper.assertTrue(activation(stack) == 2F,
                "a second activation of 1.0 at decay 1.0 must accumulate to 2.0, got " + activation(stack));
        helper.assertTrue(ContaminationUtil.getNeutronRads(stack) == 2F,
                "an activated stack must radiate what it carries, got " + ContaminationUtil.getNeutronRads(stack));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = HazardGameTest.BATCH)
    public static void neutronActivationDecaysAndClears(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STICK);
        ContaminationUtil.neutronActivateItem(stack, 2F, 1F);
        ContaminationUtil.neutronActivateItem(stack, 0F, 0.5F);
        helper.assertTrue(activation(stack) == 1F, "decay 0.5 must halve the carried activation, got " + activation(stack));

        ItemStack faint = new ItemStack(Items.STICK);
        faint.set(HazardContent.NEUTRON_ACTIVATION.get(), 0.0015F);
        helper.assertTrue(ContaminationUtil.neutronActivateItem(faint, 0F, 0.5F),
                "decaying 0.0015 below the 0.001 decontamination floor must change the stack");
        helper.assertTrue(activation(faint) == null,
                "a decontaminated item must lose the component outright, got " + activation(faint));
        helper.assertTrue(ContaminationUtil.getNeutronRads(faint) == 0F, "a cleaned item must radiate nothing");

        ItemStack trace = new ItemStack(Items.STICK);
        helper.assertTrue(!ContaminationUtil.neutronActivateItem(trace, 0.00005F, 1F),
                "an activation under the 0.0001 floor must not be recorded");
        helper.assertTrue(activation(trace) == null, "a refused activation must leave no component behind");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = HazardGameTest.BATCH)
    public static void neutronActivationDividesStacksAndRefusesRadioactiveItems(GameTestHelper helper) {
        // NTM NEXT row asserts refusal; its ContaminationUtil (and this port) divides per item instead.
        ItemStack pair = new ItemStack(Items.STICK, 2);
        helper.assertTrue(ContaminationUtil.neutronActivateItem(pair, 1F, 1F), "a stack of two must activate per item");
        helper.assertTrue(activation(pair) != null && activation(pair) == 0.5F,
                "a stack of two must store rad/count = 0.5 per item, got " + activation(pair));
        helper.assertTrue(ContaminationUtil.getNeutronRads(pair) == 1F,
                "and radiate the whole dose for the stack, got " + ContaminationUtil.getNeutronRads(pair));

        ItemStack radioactive = new ItemStack(Items.STICK);
        radioactive.set(HazardContent.HAZ_RADIATION.get(), 5F);
        helper.assertTrue(!ContaminationUtil.neutronActivateItem(radioactive, 1F, 1F),
                "an item that is already a radiation source must not activate");
        radioactive.set(HazardContent.NEUTRON_ACTIVATION.get(), 3F);
        helper.assertTrue(ContaminationUtil.getNeutronRads(radioactive) == 0F,
                "a radioactive item must report no activation, got " + ContaminationUtil.getNeutronRads(radioactive));

        ItemStack grown = new ItemStack(Items.STICK);
        ContaminationUtil.neutronActivateItem(grown, 1F, 1F);
        grown.setCount(3);
        helper.assertTrue(ContaminationUtil.getNeutronRads(grown) == 3F,
                "activation reads per stack, so 3 items at 1.0 must radiate 3.0, got " + ContaminationUtil.getNeutronRads(grown));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = HazardGameTest.BATCH)
    public static void neutronActivationSkipsTheHeldAndOffhandSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inv = player.getInventory();
        int held = 2;
        inv.selected = held;
        for (int slot : new int[]{held, CARGO_SLOT, 36, Inventory.SLOT_OFFHAND}) inv.setItem(slot, new ItemStack(Items.STICK));
        helper.assertTrue(ContaminationUtil.neutronActivateInventory(player, 1F, 1F),
                "activating a carried inventory must report a change");
        helper.assertTrue(activation(inv.getItem(CARGO_SLOT)) == 1F,
                "a carried item must activate, got " + activation(inv.getItem(CARGO_SLOT)));
        helper.assertTrue(activation(inv.getItem(36)) == 1F, "worn armour must activate, got " + activation(inv.getItem(36)));
        helper.assertTrue(activation(inv.getItem(held)) == null, "the item in hand must not activate");
        helper.assertTrue(activation(inv.getItem(Inventory.SLOT_OFFHAND)) == null, "the offhand is outside the activated slot set");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = HazardGameTest.BATCH)
    public static void neutronActivationCarriedActivationDosesAndReportsARate(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.tickCount = 300;
        LivingHazardProps.setRadiation(player, 0D);
        LivingHazardProps.setRadBuf(player, 0D);
        double dose = 4D;
        helper.assertTrue(ContaminationUtil.contaminate(player, ContaminationUtil.HazardType.NEUTRON,
                ContaminationUtil.ContaminationType.CREATIVE, dose), "a survival player must take the dose");
        helper.assertTrue(Math.abs(LivingHazardProps.getRadiation(player) - dose) < 1.0E-6D,
                "an unarmoured player takes the whole neutron dose, got " + LivingHazardProps.getRadiation(player));
        helper.assertTrue(LivingHazardProps.getNeutron(player) == dose,
                "the pass's dose must be banked for the readout, got " + LivingHazardProps.getNeutron(player));
        double expected = dose * 20D / HazardConfig.get().hazardRate();
        helper.assertTrue(Math.abs(ContaminationUtil.getPlayerRads(player) - expected) < 1.0E-6D,
                "the readout must fold the banked dose in as RAD/s, expected " + expected
                        + " got " + ContaminationUtil.getPlayerRads(player));
        helper.assertTrue(ContaminationUtil.getNoNeutronPlayerRads(player) == 0D,
                "the activation feedback term must stay out of the rate that drives activation");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, timeoutTicks = 100, batch = "wflib_neutron_hot")
    public static void neutronActivationAmbientFieldActivatesCarriedItems(GameTestHelper helper) {
        boolean was = RadiationConfig.get().neutronActivation();
        RadiationConfig.override(v -> v.withNeutronActivation(true));
        ServerPlayer player = mockServerPlayer(helper);
        player.getInventory().setItem(CARGO_SLOT, new ItemStack(Items.STICK));
        helper.onEachTick(() -> LivingHazardProps.setRadBuf(player, HOT_FIELD));
        helper.succeedWhen(() -> {
            Float carried = activation(player.getInventory().getItem(CARGO_SLOT));
            helper.assertTrue(carried != null && carried > 0F,
                    "a carried item must pick up activation in a " + HOT_FIELD + " RAD/s field");
            helper.assertTrue(LivingHazardProps.getNeutron(player) > 0D,
                    "an activated inventory must report back as a neutron dose");
            RadiationConfig.override(v -> v.withNeutronActivation(was));
            remove(player);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, timeoutTicks = 100, batch = "wflib_neutron_cold")
    public static void neutronActivationBelowTheThresholdActivatesNothing(GameTestHelper helper) {
        boolean was = RadiationConfig.get().neutronActivation();
        RadiationConfig.override(v -> v.withNeutronActivation(true));
        ServerPlayer player = mockServerPlayer(helper);
        player.getInventory().setItem(CARGO_SLOT, new ItemStack(Items.STICK));
        helper.onEachTick(() -> LivingHazardProps.setRadBuf(player, COLD_FIELD));
        helper.runAfterDelay(60, () -> {
            RadiationConfig.override(v -> v.withNeutronActivation(was));
            remove(player);
            helper.assertTrue(activation(player.getInventory().getItem(CARGO_SLOT)) == null,
                    "a field under the " + RadiationConfig.get().neutronActivationThreshold() + " RAD/s threshold must activate nothing");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, timeoutTicks = 100, batch = "wflib_hazard_players")
    public static void neutronActivationMenuListenerFeedsTheHazardPass(GameTestHelper helper) {
        ServerPlayer player = mockServerPlayer(helper);
        ItemStack hot = new ItemStack(Items.STICK);
        hot.set(HazardContent.HAZ_RADIATION.get(), 100F);
        player.getInventory().setItem(CARGO_SLOT, hot);
        helper.succeedWhen(() -> {
            helper.assertTrue(LivingHazardProps.getRadEnv(player) > 0D || LivingHazardProps.getRadBuf(player) > 0D,
                    "a radioactive item dropped into an inventory slot must reach the hazard pass");
            remove(player);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, timeoutTicks = 100, batch = "wflib_hazard_players")
    public static void neutronActivationWornArmourReachesTheHazardPass(GameTestHelper helper) {
        ServerPlayer player = mockServerPlayer(helper);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helmet.set(HazardContent.HAZ_RADIATION.get(), 100F);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.succeedWhen(() -> {
            helper.assertTrue(LivingHazardProps.getRadEnv(player) > 0D || LivingHazardProps.getRadBuf(player) > 0D,
                    "a radioactive worn helmet must reach the hazard pass");
            remove(player);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = HazardGameTest.BATCH)
    public static void neutronActivationShipsDisabled(GameTestHelper helper) {
        ModConfigSpec.ConfigValue<?> enabled = RadiationConfig.SPEC.getValues().get("radiation.neutronActivation");
        ModConfigSpec.ConfigValue<?> threshold = RadiationConfig.SPEC.getValues().get("radiation.neutronActivationThreshold");
        helper.assertTrue(Boolean.FALSE.equals(enabled.getDefault()), "item neutron activation must ship disabled");
        helper.assertTrue(Integer.valueOf(15).equals(threshold.getDefault()),
                "the activation threshold must ship at the upstream 15 RAD/s, got " + threshold.getDefault());
        helper.succeed();
    }
}
