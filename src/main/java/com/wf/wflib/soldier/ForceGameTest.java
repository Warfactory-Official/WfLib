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
            h.assertTrue(!bot.sprint, "a calm patrol runs between its stops");

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
            h.assertTrue(bot.sprint, "a patrol in a fight does not run for cover");

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

    private static void clearTeam(String team) {
        for (var b : SoldierAi.soldierBots()) {
            if (!b.team.equals(team)) continue;
            var p = b.server.getPlayerList().getPlayerByName(b.name);
            if (p != null) Soldiers.remove(p);
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void anUnownedSoldierComesBackFromTheBookWithItsKitPostAndOrder(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var enemy = new SoldierSpec("post_test", "PostTest", true, null, List.of(), Map.of(), 0);
        var p = Soldiers.spawn(level, h.absoluteVec(new Vec3(2, 2, 2)), 90, enemy, null);
        h.assertTrue(p != null, "could not spawn test soldier");
        try {
            var helmet = new ItemStack(Items.IRON_HELMET);
            helmet.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Lucky"));
            p.setItemSlot(EquipmentSlot.HEAD, helmet);
            Vec3 watch = h.absoluteVec(new Vec3(6, 2, 2));
            var bot = SoldierAi.botOf(p.getGameProfile().getName());
            Orders.apply(bot, p.position(), SoldierOrder.at(SoldierOrder.Kind.HOLD, watch));
            bot.garrison = "somewhere";

            var mine = Posts.snapshot(server).stream().filter(x -> x.team().equals("post_test")).toList();
            h.assertTrue(mine.size() == 1, "the book did not capture exactly the one soldier: " + mine.size());
            var regs = server.registryAccess();
            var book = new PostBook();
            book.replace(mine);
            var read = PostBook.load(book.save(new net.minecraft.nbt.CompoundTag(), regs), regs).posts();
            h.assertTrue(read.size() == 1, "the post did not survive its save");

            Soldiers.remove(p);
            h.assertTrue(SoldierAi.soldierBots().stream().noneMatch(b -> b.team.equals("post_test") && server.getPlayerList().getPlayerByName(b.name) != null),
                    "the soldier was not taken out");
            Posts.restore(server, read.get(0));

            var back = SoldierAi.soldierBots().stream().filter(b -> b.team.equals("post_test") && server.getPlayerList().getPlayerByName(b.name) != null).findFirst().orElse(null);
            h.assertTrue(back != null, "nobody came back");
            var body = server.getPlayerList().getPlayerByName(back.name);
            h.assertTrue(back.huntsPlayers && back.ownerId == null, "came back on the wrong side");
            h.assertTrue(back.order.kind() == SoldierOrder.Kind.HOLD && back.order.pos() != null && back.order.pos().distanceTo(watch) < 1e-9, "came back without its order: " + back.order);
            h.assertTrue(back.garrison.equals("somewhere"), "forgot which garrison raised it");
            h.assertTrue(ItemStack.matches(body.getItemBySlot(EquipmentSlot.HEAD), helmet), "came back without its helmet");
            h.assertTrue(body.position().distanceTo(p.position()) < 1.5, "came back somewhere else: " + body.position());
            h.succeed();
        } finally {
            clearTeam("post_test");
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aTerritoryRaisesItsGarrisonReplacesLossesAndLetsItGo(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var book = TerritoryBook.get(server);
        var oldRaiser = Garrisons.raiser;
        var oldVariants = Garrisons.variants;
        Garrisons.variants = s -> List.of("test_variant");
        Garrisons.raiser = (lvl, unit, at, yaw) -> Soldiers.spawn(lvl, at, yaw, new SoldierSpec("garrison_test", "Garr", false, null, List.of(), Map.of(), 0), null);
        try {
            for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            Vec3 centre = h.absoluteVec(new Vec3(4.5, 2, 4.5));
            var t = new Territory("force_test_garrison", "Keep", "", level.dimension().location().toString(), centre, 3,
                    level.getMinBuildHeight(), level.getMaxBuildHeight(), List.of());
            book.put(t.withGarrison(new Garrison(3, List.of(), SoldierOrder.Kind.PATROL, 60)));

            Garrisons.pass(server);
            var raised = Garrisons.alive(server, "force_test_garrison");
            h.assertTrue(raised.size() == 3, "raised " + raised.size() + " of 3");
            for (var p : raised) {
                var b = SoldierAi.botOf(p.getGameProfile().getName());
                h.assertTrue(b.order.kind() == SoldierOrder.Kind.PATROL && b.order.region().equals("force_test_garrison"), "not patrolling its territory: " + b.order);
                h.assertTrue(t.contains(p.position()), "raised outside the territory: " + p.position());
            }
            Garrisons.pass(server);
            h.assertTrue(Garrisons.alive(server, "force_test_garrison").size() == 3, "a full garrison grew");

            var fallen = raised.get(0);
            Garrisons.lost(SoldierAi.botOf(fallen.getGameProfile().getName()), level.getGameTime());
            Soldiers.remove(fallen);
            Garrisons.pass(server);
            h.assertTrue(Garrisons.alive(server, "force_test_garrison").size() == 2, "replaced a loss before the reinforcement delay");

            book.put(t.withGarrison(new Garrison(3, List.of(), SoldierOrder.Kind.PATROL, 0)));
            Garrisons.pass(server);
            h.assertTrue(Garrisons.alive(server, "force_test_garrison").size() == 3, "did not replace the loss");

            book.put(t.withGarrison(new Garrison(1, List.of(), SoldierOrder.Kind.GUARD, 0)));
            Garrisons.pass(server);
            h.assertTrue(Garrisons.alive(server, "force_test_garrison").size() == 1, "did not send the surplus home");

            book.put(t);                                          // no garrison any more: the last one is its own man
            Garrisons.pass(server);
            h.assertTrue(Garrisons.alive(server, "force_test_garrison").isEmpty(), "still counted as a garrison");
            h.succeed();
        } finally {
            Garrisons.raiser = oldRaiser;
            Garrisons.variants = oldVariants;
            if (book.get("force_test_garrison") != null) book.remove("force_test_garrison");
            clearTeam("garrison_test");
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aKilledEnemyIsForgottenAtOnceNotFiredAtForAMinute(GameTestHelper h) {
        var level = h.getLevel();
        var friend = Soldiers.spawn(level, h.absoluteVec(new Vec3(1, 2, 1)), 0, new SoldierSpec("kill_a", "KillA", false, null, List.of(), Map.of(), 0), null);
        var foe = Soldiers.spawn(level, h.absoluteVec(new Vec3(6, 2, 1)), 0, new SoldierSpec("kill_b", "KillB", true, null, List.of(), Map.of(), 0), null);
        h.assertTrue(friend != null && foe != null, "could not spawn the test soldiers");
        try {
            var bot = SoldierAi.botOf(friend.getGameProfile().getName());
            String key = foe.getGameProfile().getName().toLowerCase(java.util.Locale.ROOT);
            long now = level.getGameTime();
            var threat = SoldierAi.Threat.told();
            threat.confirmed = true;
            threat.seen = now;
            threat.eye = foe.getEyePosition();
            bot.threats.put(key, threat);
            foe.hurt(foe.damageSources().genericKill(), 1000f);
            h.assertTrue(!foe.isAlive(), "the foe did not die");
            SoldierAi.observe(friend, bot, foe, key, now + 2);
            h.assertTrue(bot.threats.isEmpty(), "still remembers where the dead enemy lay: " + bot.threats.keySet());
            bot.threats.put(key, threat);
            SoldierAi.observe(friend, bot, null, key, now + 3);          // the body is gone from the world altogether
            h.assertTrue(bot.threats.isEmpty(), "still remembers an enemy that left the world");
            h.succeed();
        } finally {
            Soldiers.remove(friend);
            if (server(level).getPlayerList().getPlayer(foe.getUUID()) != null) Soldiers.remove(foe);
        }
    }

    private static net.minecraft.server.MinecraftServer server(net.minecraft.server.level.ServerLevel level) {
        return level.getServer();
    }

    private static ServerPlayer body(net.minecraft.server.level.ServerLevel level, GameTestHelper h, String team, double x) {
        return Soldiers.spawn(level, h.absoluteVec(new Vec3(x, 2, 1)), 0, new SoldierSpec(team, "Wage", false, null, List.of(), Map.of(), 0), null);
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void unpaidSoldiersLeaveOrTurnOnTheirOwnerByTheDice(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var owner = body(level, h, "wage_owner", 1);
        var book = SquadBook.get(server);
        var recruits = new java.util.ArrayList<ServerPlayer>();
        try {
            for (int i = 0; i < 3; i++) {
                var s = body(level, h, "friendly", 2 + i);
                SoldierAi.botOf(s.getGameProfile().getName()).ownerId = owner.getUUID();
                Squads.recruit(owner, s);
                recruits.add(s);
            }
            var wage = com.wf.wflib.soldier.api.Cost.items(Items.EMERALD, 1);
            owner.getInventory().add(new ItemStack(Items.EMERALD, 1));                   // one wage in the pocket for three soldiers
            var squad = Squads.deployed(owner.getUUID());
            h.assertTrue(squad.size() == 3, "expected three deployed soldiers, got " + squad.size());
            int paid = Wages.settle(owner, squad, wage, true, 100.0, net.minecraft.util.RandomSource.create(1));
            h.assertTrue(paid == 1, "paid " + paid + " of 1 affordable");
            h.assertTrue(book.of(owner.getUUID()).size() == 1, "the unpaid two stayed on the roster: " + book.of(owner.getUUID()).size());
            long hostile = recruits.stream().map(s -> SoldierAi.botOf(s.getGameProfile().getName())).filter(b -> b != null && b.huntsPlayers).count();
            h.assertTrue(hostile == 2, "with 100% defection both unpaid should be hostile, " + hostile + " were");
            var rest = Squads.deployed(owner.getUUID());
            h.assertTrue(rest.size() == 1, "one soldier should remain loyal");
            int before = SoldierAi.soldierBots().size();
            int paid2 = Wages.settle(owner, rest, wage, false, 100.0, net.minecraft.util.RandomSource.create(1));   // defection off: it just leaves
            h.assertTrue(paid2 == 0 && Squads.deployed(owner.getUUID()).isEmpty(), "the unpaid soldier did not leave");
            h.assertTrue(book.of(owner.getUUID()).isEmpty(), "the roster still holds a soldier who left");
            h.succeed();
        } finally {
            for (var s : recruits) if (server.getPlayerList().getPlayer(s.getUUID()) != null) Soldiers.remove(s);
            for (var c : new java.util.ArrayList<>(book.of(owner.getUUID()))) book.remove(owner.getUUID(), c.id);
            Soldiers.remove(owner);
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 100)
    public static void aPaidHireArrivesWhenItsTimeComesAndWaitsForAnAbsentHirer(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var owner = body(level, h, "hire_owner", 1);
        var book = SquadBook.get(server);
        var oldHirer = Recruits.hirer;
        var arrived = new java.util.ArrayList<String>();
        Recruits.hirer = (who, variant, afar) -> {
            arrived.add(variant + (afar ? "/walking" : "/here"));
            return new Companion(java.util.UUID.randomUUID(), "Late", "friendly", com.wf.wflib.soldier.api.SoldierRole.RIFLEMAN, null);
        };
        long now = level.getGameTime();
        var soon = new SquadBook.Pending(owner.getUUID(), "rifle", now + 100);
        var away = new SquadBook.Pending(java.util.UUID.randomUUID(), "medic", now - 5);
        try {
            book.addPending(soon);
            book.addPending(away);
            Recruits.arrivals(server, now);
            h.assertTrue(arrived.isEmpty(), "arrived before its time or for an absent hirer: " + arrived);
            Recruits.arrivals(server, now + 100);
            h.assertTrue(arrived.equals(List.of("rifle/walking")), "the due hire did not arrive walking: " + arrived);
            h.assertTrue(book.pending().contains(away) && !book.pending().contains(soon), "pending list wrong: " + book.pending());
            h.assertTrue(Recruits.arriving(server, owner.getUUID()) == 0, "still counted as on the way");
            var copy = SquadBook.load(book.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()), server.registryAccess());
            h.assertTrue(copy.pending().contains(away), "a pending hire did not survive the save");
            h.succeed();
        } finally {
            Recruits.hirer = oldHirer;
            book.removePending(soon);
            book.removePending(away);
            Soldiers.remove(owner);
        }
    }
}
