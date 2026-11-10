package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierSpec;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Territory patrols and supply depots, with a real soldier, chest and server data: the order state machines run as the
 * brain runs them (the test server has no gun mod, so the walking itself is the AI's and is not exercised here).
 */
@PrefixGameTestTemplate(false)
public class ForceGameTest {
    private static final String TEAM = "force_test";

    private static SoldierSpec spec() {
        return new SoldierSpec(TEAM, "ForceTest", false, null, List.of(), Map.of(), 0);
    }

    private static Kit arrows(int n) {
        return new Kit(Map.of(Items.ARROW, new Kit.Want(new ItemStack(Items.ARROW), n)));
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aSoldierShortOfKitGoesToADepotTakesWhatItLacksAndResumes(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var p = Soldiers.spawn(level, h.absoluteVec(new Vec3(1, 2, 1)), 0, spec(), null);
        h.assertTrue(p != null, "could not spawn test soldier");
        BlockPos chest = h.absolutePos(new BlockPos(2, 2, 1));
        var depots = DepotBook.get(server);
        try {
            h.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);
            Container box = Resupply.containerAt(level, chest);
            h.assertTrue(box != null, "no container behind the chest block");
            box.setItem(0, new ItemStack(Items.ARROW, 40));
            depots.put(new Depot("force_test_chest", TEAM, level.dimension().location().toString(), chest, false));

            var bot = SoldierAi.botOf(p.getGameProfile().getName());
            bot.kit = arrows(16);
            long now = level.getGameTime();
            Resupply.tick(server, p, bot, now);
            h.assertTrue(bot.order.kind() == SoldierOrder.Kind.RESUPPLY, "a soldier with an empty kit stayed put: " + bot.order);
            h.assertTrue(bot.resume != null && bot.resume.kind() == SoldierOrder.Kind.FOLLOW, "the order to go back to was not kept");

            Resupply.tick(server, p, bot, now + 10);          // in reach of the chest: takes what it lacks
            h.assertTrue(Kit.count(p.getInventory(), Items.ARROW) == 16, "took " + Kit.count(p.getInventory(), Items.ARROW) + " of 16");
            h.assertTrue(Kit.count(box, Items.ARROW) == 24, "the chest was not drawn down: " + Kit.count(box, Items.ARROW));
            h.assertTrue(bot.order.kind() == SoldierOrder.Kind.FOLLOW, "did not go back to its order: " + bot.order);
            h.assertTrue(bot.nextResupply > now + 10, "no pause before it checks again");
            h.succeed();
        } finally {
            depots.remove("force_test_chest");
            Soldiers.remove(p);
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aDepotThatHasNothingForItIsGivenUpOnAndTheNextTried(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var p = Soldiers.spawn(level, h.absoluteVec(new Vec3(1, 2, 1)), 0, spec(), null);
        h.assertTrue(p != null, "could not spawn test soldier");
        var depots = DepotBook.get(server);
        BlockPos empty = h.absolutePos(new BlockPos(2, 2, 1)), crate = h.absolutePos(new BlockPos(1, 2, 2));
        try {
            h.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);
            h.setBlock(new BlockPos(1, 2, 2), Blocks.BARREL);
            var dim = level.dimension().location().toString();
            depots.put(new Depot("force_test_empty", TEAM, dim, empty, false));
            depots.put(new Depot("force_test_crate", TEAM, dim, crate, true));
            var bot = SoldierAi.botOf(p.getGameProfile().getName());
            bot.kit = arrows(8);
            long now = level.getGameTime();
            Orders.apply(bot, p.position(), SoldierOrder.of(SoldierOrder.Kind.RESUPPLY));
            for (int i = 0; i < 4 && bot.order.kind() == SoldierOrder.Kind.RESUPPLY; i++) Resupply.tick(server, p, bot, now + i * 10L);
            h.assertTrue(Kit.count(p.getInventory(), Items.ARROW) == 8, "never got to the second depot: " + Kit.count(p.getInventory(), Items.ARROW));
            h.assertTrue(bot.order.kind() == SoldierOrder.Kind.FOLLOW, "still on the trip: " + bot.order);
            h.succeed();
        } finally {
            depots.remove("force_test_empty");
            depots.remove("force_test_crate");
            Soldiers.remove(p);
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aPatrolWalksItsRouteStopsToLookAboutAndMovesOn(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var p = Soldiers.spawn(level, h.absoluteVec(new Vec3(1, 2, 1)), 0, spec(), null);
        h.assertTrue(p != null, "could not spawn test soldier");
        var book = TerritoryBook.get(server);
        try {
            Vec3 a = h.absoluteVec(new Vec3(3, 2, 1)), b = h.absoluteVec(new Vec3(5, 2, 1));
            book.put(new Territory("force_test_gate", "Gate", TEAM, level.dimension().location().toString(),
                    a, 20, level.getMinBuildHeight(), level.getMaxBuildHeight(), List.of(a, b)));
            var bot = SoldierAi.botOf(p.getGameProfile().getName());
            Orders.apply(bot, p.position(), SoldierOrder.in(SoldierOrder.Kind.PATROL, "force_test_gate"));
            long now = level.getGameTime();
            Patrols.tick(server, p, bot, now);
            h.assertTrue(a.equals(bot.patrolPoint) && a.equals(bot.anchor), "first stop is not the first waypoint: " + bot.patrolPoint);

            p.teleportTo(a.x, a.y, a.z);                         // arrived
            Patrols.tick(server, p, bot, now + 10);
            h.assertTrue(bot.dwell > 0, "did not stop at the waypoint");
            for (int i = 2; i < 60 && bot.patrolPoint != null; i++) Patrols.tick(server, p, bot, now + i * 10L);
            h.assertTrue(bot.patrolPoint == null, "never finished looking about");
            Patrols.tick(server, p, bot, now + 700);
            h.assertTrue(b.equals(bot.patrolPoint), "second stop is not the second waypoint: " + bot.patrolPoint);

            bot.threats.put("raider", SoldierAi.Threat.told());     // contact: the AI owns the movement until it is over
            Vec3 before = bot.patrolPoint;
            bot.patrolPoint = null;
            Patrols.tick(server, p, bot, now + 710);
            h.assertTrue(bot.patrolPoint == null && before != null, "kept planning stops under fire");

            book.remove("force_test_gate");                      // the territory goes: back to the owner
            bot.threats.clear();
            Patrols.tick(server, p, bot, now + 720);
            h.assertTrue(bot.order.kind() == SoldierOrder.Kind.FOLLOW, "kept patrolling a deleted territory");
            h.succeed();
        } finally {
            if (book.get("force_test_gate") != null) book.remove("force_test_gate");
            Soldiers.remove(p);
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aCompanionKeepsItsFireTeamOrdersAndWornKitAcrossAStow(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        // a soldier body stands in for the owner: a mock player's login trips other mods' login hooks (no real connection)
        var owner = Soldiers.spawn(level, h.absoluteVec(new Vec3(1, 2, 1)), 0, new SoldierSpec("owner_test", "OwnerTest", false, null, List.of(), Map.of(), 0), null);
        h.assertTrue(owner != null, "could not spawn the stand-in owner");
        var book = SquadBook.get(server);
        var spec = new SoldierSpec("friendly", "Wolf", false, null, List.of(), Map.of(), 0);
        var first = Soldiers.spawn(level, h.absoluteVec(new Vec3(2, 2, 1)), 0, spec, owner);
        h.assertTrue(first != null, "could not spawn test soldier");
        Companion joined = null;
        ServerPlayer back = null;
        try {
            var helmet = new ItemStack(Items.IRON_HELMET);
            helmet.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Lucky"));
            first.setItemSlot(EquipmentSlot.HEAD, helmet);
            joined = Squads.recruit(owner, first);
            h.assertTrue(book.of(owner.getUUID()).size() == 1 && joined.fireTeam == 0, "not on the roster in the first fire team");
            h.assertTrue(Squads.deployed(owner.getUUID()).size() == 1, "not deployed after recruiting");

            Vec3 objective = h.absoluteVec(new Vec3(8, 2, 1));
            var taken = Squads.order(owner, Squads.Target.of("alpha"), SoldierOrder.at(SoldierOrder.Kind.MOVE, objective));
            h.assertTrue(taken.size() == 1 && taken.get(0).order.kind() == SoldierOrder.Kind.MOVE, "alpha did not take the move order");
            h.assertTrue(joined.order.kind() == SoldierOrder.Kind.MOVE, "the roster did not remember the order");
            h.assertTrue(Squads.select(owner.getUUID(), Squads.Target.of("bravo")).isEmpty(), "bravo answered an order for alpha");

            var copy = SquadBook.load(book.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()), server.registryAccess());
            Companion read = copy.find(owner.getUUID(), joined.id);
            h.assertTrue(read != null && read.callsign.equals(joined.callsign) && read.order.pos() != null
                    && read.order.pos().distanceTo(objective) < 1e-9, "the roster did not survive its save");
            h.assertTrue(ItemStack.matches(read.armour.get(EquipmentSlot.HEAD), helmet), "the worn helmet changed in the save: " + read.armour);

            Squads.stow(server, owner.getUUID());
            h.assertTrue(Squads.deployed(owner.getUUID()).isEmpty() || server.getPlayerList().getPlayerByName(first.getGameProfile().getName()) == null,
                    "the soldier was left in the world");
            back = Squads.deploy(owner, joined);
            h.assertTrue(back != null, "could not bring the companion back");
            var bot = SoldierAi.botOf(back.getGameProfile().getName());
            h.assertTrue(bot.order.kind() == SoldierOrder.Kind.MOVE && bot.companion.equals(joined.id), "came back without its order");
            h.assertTrue(ItemStack.matches(back.getItemBySlot(EquipmentSlot.HEAD), helmet), "came back without its helmet");

            back.hurt(back.damageSources().genericKill(), 1000f);
            h.assertTrue(book.find(owner.getUUID(), joined.id) == null, "a dead companion stayed on the roster");
            h.succeed();
        } finally {
            if (joined != null) book.remove(owner.getUUID(), joined.id);
            if (back != null && !back.isDeadOrDying()) Soldiers.remove(back);
            Squads.stow(server, owner.getUUID());
            var left = server.getPlayerList().getPlayerByName(first.getGameProfile().getName());
            if (left != null) Soldiers.remove(left);
            Soldiers.remove(owner);
        }
    }
}
