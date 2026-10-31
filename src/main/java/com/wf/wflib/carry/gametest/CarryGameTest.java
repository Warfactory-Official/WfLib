package com.wf.wflib.carry.gametest;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.carry.api.Carry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import com.wf.wflib.core.api.WFLib;

/** Carry rules; the tag is empty here, so state is set directly. */
@PrefixGameTestTemplate(false)
public class CarryGameTest {
    private static final String TEMPLATE = "empty";

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "carrier"));
        p.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        return p;
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void carryingBlocksAttackPickupMountAndJump(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Carry.set(p, new ItemStack(Items.ANVIL));
        helper.assertTrue(Carry.carrying(p), "not carrying");
        helper.assertTrue(p.getAttributeValue(Attributes.JUMP_STRENGTH) == 0, "jump " + p.getAttributeValue(Attributes.JUMP_STRENGTH));

        var attack = new AttackEntityEvent(p, helper.spawn(EntityType.PIG, 2, 1, 2));
        NeoForge.EVENT_BUS.post(attack);
        helper.assertTrue(attack.isCanceled(), "attack allowed");

        ItemEntity loose = new ItemEntity(helper.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.STICK));
        var pickup = new ItemEntityPickupEvent.Pre(p, loose);
        NeoForge.EVENT_BUS.post(pickup);
        helper.assertTrue(pickup.canPickup() == TriState.FALSE, "pickup allowed");

        Boat boat = helper.spawn(EntityType.BOAT, 3, 1, 3);
        var mount = new EntityMountEvent(p, boat, p.level(), true);
        NeoForge.EVENT_BUS.post(mount);
        helper.assertTrue(mount.isCanceled(), "mount allowed");

        var use = new PlayerInteractEvent.RightClickItem(p, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(use);
        helper.assertTrue(use.isCanceled(), "item use allowed");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void storageTakesTheLoadAndFullStorageRefuses(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        BlockPos chest = new BlockPos(2, 1, 1);
        helper.setBlock(chest, Blocks.CHEST);
        BlockPos abs = helper.absolutePos(chest);
        Carry.set(p, new ItemStack(Items.ANVIL));
        click(p, abs);
        ChestBlockEntity be = (ChestBlockEntity) helper.getLevel().getBlockEntity(abs);
        helper.assertTrue(be.getItem(0).is(Items.ANVIL), "chest " + be.getItem(0));
        helper.assertTrue(Carry.carried(p).isEmpty(), "still carrying");
        helper.assertTrue(p.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.3, "jump not restored");

        for (int i = 0; i < be.getContainerSize(); i++) {
            be.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        Carry.set(p, new ItemStack(Items.ANVIL));
        click(p, abs);
        helper.assertTrue(Carry.carrying(p), "load vanished into a full chest");
        helper.succeed();
    }

    private static void click(ServerPlayer p, BlockPos abs) {
        var hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, abs, hit));
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void dropPutsTheLoadOnTheFloor(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Carry.set(p, new ItemStack(Items.ANVIL));
        Carry.drop(p);
        helper.assertTrue(Carry.carried(p).isEmpty(), "still carrying");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4),
                i -> i.getItem().is(Items.ANVIL)).isEmpty(), "no dropped item");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void pickupRefusesUntaggedItems(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ItemEntity stick = new ItemEntity(helper.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.STICK));
        helper.getLevel().addFreshEntity(stick);
        Carry.pickup(p, stick.getId());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ANVIL));
        Carry.pickup(p, -1);
        helper.assertTrue(Carry.carried(p).isEmpty() && !stick.isRemoved(), "untagged item picked up");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void creativeIsExemptAndGetsTheLoadBack(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Carry.set(p, new ItemStack(Items.ANVIL));
        p.setGameMode(GameType.CREATIVE);
        helper.assertFalse(Carry.carrying(p), "creative restricted");
        var attack = new AttackEntityEvent(p, helper.spawn(EntityType.PIG, 2, 1, 2));
        NeoForge.EVENT_BUS.post(attack);
        helper.assertFalse(attack.isCanceled(), "creative attack blocked");
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(p));
        helper.assertTrue(Carry.carried(p).isEmpty() && p.getInventory().contains(new ItemStack(Items.ANVIL)),
                "load not handed back");
        helper.succeed();
    }
}
