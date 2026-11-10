package com.wf.wflib.soldier;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * {@code /squad ...}: any player commands and manages their own soldiers. {@code /soldiers territory|depot|assign}: the
 * map-maker's tools (permission level 2) for the regions forces hold, the containers they resupply from, and standing orders.
 */
final class SquadCommands {
    private SquadCommands() {}

    private interface Action {
        String run() throws Exception;
    }

    private static int run(CommandSourceStack s, Action a) {
        try {
            String message = a.run();
            s.sendSuccess(() -> Component.literal(message), false);
            return 1;
        } catch (IllegalArgumentException e) {
            s.sendFailure(Component.literal(e.getMessage()));
        } catch (CommandSyntaxException e) {
            s.sendFailure(Component.literal(e.getMessage()));
        } catch (Exception e) {
            s.sendFailure(Component.literal("Failed: " + e));
        }
        return 0;
    }

    private static List<String> targets() {
        return List.of("all", "alpha", "bravo", "charlie", "delta");
    }

    static LiteralArgumentBuilder<CommandSourceStack> squad() {
        var root = Commands.literal("squad");
        root.then(Commands.literal("list").executes(c -> run(c.getSource(), () -> list(c.getSource().getPlayerOrException()))));
        var order = Commands.literal("order");
        var target = Commands.argument("target", StringArgumentType.word()).suggests((c, b) -> {
            var names = new java.util.ArrayList<>(targets());
            if (c.getSource().getEntity() instanceof ServerPlayer p) Squads.deployed(p.getUUID()).forEach(bot -> names.add(bot.name));
            return SharedSuggestionProvider.suggest(names, b);
        });
        for (SoldierOrder.Kind kind : SoldierOrder.Kind.values()) {
            var node = Commands.literal(kind.id);
            if (kind.needsRegion) {
                node.then(Commands.argument("territory", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TerritoryBook.get(c.getSource().getServer()).all().keySet(), b))
                        .executes(c -> run(c.getSource(), () -> give(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "target"),
                                SoldierOrder.in(kind, StringArgumentType.getString(c, "territory"))))));
            } else {
                node.executes(c -> run(c.getSource(), () -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return give(p, StringArgumentType.getString(c, "target"),
                            kind.needsPos ? SoldierOrder.at(kind, Squads.aim(p, 128)) : SoldierOrder.of(kind));
                }));
            }
            target.then(node);
        }
        root.then(order.then(target));
        root.then(Commands.literal("team").then(Commands.argument("soldier", StringArgumentType.word())
                .suggests((c, b) -> c.getSource().getEntity() instanceof ServerPlayer p
                        ? SharedSuggestionProvider.suggest(Squads.deployed(p.getUUID()).stream().map(bot -> bot.name), b) : b.buildFuture())
                .then(Commands.argument("team", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(targets().subList(1, 5), b))
                        .executes(c -> run(c.getSource(), () -> {
                            var t = Squads.Target.of(StringArgumentType.getString(c, "team"));
                            if (t.team() < 0) throw new IllegalArgumentException("Choose alpha, bravo, charlie or delta");
                            Squads.setFireTeam(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "soldier"), t.team());
                            return "Moved to " + Companion.TEAMS[t.team()];
                        })))));
        root.then(Commands.literal("dismiss").then(Commands.argument("soldier", StringArgumentType.word())
                .executes(c -> run(c.getSource(), () -> {
                    Squads.dismiss(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "soldier"));
                    return "Dismissed; it holds where it stands";
                }))));
        var variants = Commands.argument("variant", StringArgumentType.word()).suggests((c, b) ->
                SharedSuggestionProvider.suggest(SoldierRoster.get(c.getSource().getServer()).units().keySet(), b));
        root.then(Commands.literal("recruit").requires(SoldierEditor::allowed).then(variants.executes(c -> run(c.getSource(), () -> {
            Companion added = Squads.enlist(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "variant"));
            return "Recruited " + added.callsign + " to " + Companion.TEAMS[added.fireTeam];
        }))));
        root.then(Commands.literal("kit").requires(SoldierEditor::allowed).then(Commands.argument("target", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(targets(), b))
                .then(Commands.argument("variant", StringArgumentType.word()).suggests((c, b) ->
                                SharedSuggestionProvider.suggest(SoldierRoster.get(c.getSource().getServer()).units().keySet(), b))
                        .executes(c -> run(c.getSource(), () -> "Re-equipped " + Squads.rekit(c.getSource().getPlayerOrException(),
                                Squads.Target.of(StringArgumentType.getString(c, "target")), StringArgumentType.getString(c, "variant")) + " soldiers")))));
        return root;
    }

    private static String give(ServerPlayer owner, String target, SoldierOrder order) {
        var taken = Squads.order(owner, Squads.Target.of(target), order);
        return order.label() + ": " + taken.size() + (taken.size() == 1 ? " soldier" : " soldiers");
    }

    private static String list(ServerPlayer owner) {
        var book = SquadBook.get(owner.getServer()).of(owner.getUUID());
        if (book.isEmpty()) return "No soldiers. Sneak and use an unowned friendly soldier to recruit it.";
        StringBuilder out = new StringBuilder();
        for (int t = 0; t < Companion.TEAMS.length; t++) {
            StringBuilder line = new StringBuilder();
            for (Companion c : book) {
                if (c.fireTeam != t) continue;
                var bot = Squads.deployed(owner.getUUID()).stream().filter(b -> c.id.equals(b.companion)).findFirst().orElse(null);
                line.append("\n  ").append(bot == null ? c.callsign + " (away)" : bot.name).append(" - ").append(c.role.title)
                        .append(", ").append(bot == null ? c.order.label() : bot.order.label());
            }
            if (!line.isEmpty()) out.append(Companion.TEAMS[t]).append(line).append('\n');
        }
        return out.toString().stripTrailing();
    }

    /** {@code /soldiers territory|depot|assign}. */
    static void admin(LiteralArgumentBuilder<CommandSourceStack> root) {
        var territory = Commands.literal("territory").requires(s -> s.hasPermission(2));
        var id = Commands.argument("id", StringArgumentType.word()).suggests((c, b) ->
                SharedSuggestionProvider.suggest(TerritoryBook.get(c.getSource().getServer()).all().keySet(), b));
        territory.then(Commands.literal("list").executes(c -> run(c.getSource(), () -> {
            var all = TerritoryBook.get(c.getSource().getServer()).all().values();
            if (all.isEmpty()) return "No territories";
            StringBuilder s = new StringBuilder();
            all.forEach(t -> s.append(t.id()).append(" (").append(t.team().isEmpty() ? "anyone" : t.team()).append(", r").append(t.radius())
                    .append(", ").append(t.route().size()).append(" waypoints")
                    .append(t.garrison() == null ? "" : ", garrison " + Garrisons.alive(c.getSource().getServer(), t.id()).size() + "/" + t.garrison().count())
                    .append(")\n"));
            return s.toString().stripTrailing();
        })));
        territory.then(Commands.literal("create").then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("radius", IntegerArgumentType.integer(2, Territory.MAX_RADIUS))
                        .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "radius"), ""))
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "radius"),
                                        StringArgumentType.getString(c, "team")))))));
        territory.then(Commands.literal("remove").then(id.executes(c -> run(c.getSource(), () -> {
            TerritoryBook.get(c.getSource().getServer()).remove(StringArgumentType.getString(c, "id"));
            return "Territory removed; soldiers patrolling it return to their owners";
        }))));
        territory.then(Commands.literal("waypoint")
                .then(Commands.literal("add").then(id.executes(c -> run(c.getSource(), () -> waypoint(c.getSource(), StringArgumentType.getString(c, "id"), true)))))
                .then(Commands.literal("clear").then(id.executes(c -> run(c.getSource(), () -> waypoint(c.getSource(), StringArgumentType.getString(c, "id"), false))))));
        // /soldiers territory garrison <id> clear | <count> <patrol|guard> <reinforcement seconds> [variant,variant,...]
        var garrisonUnits = Commands.argument("units", StringArgumentType.word()).suggests((c, b) ->
                SharedSuggestionProvider.suggest(SoldierRoster.get(c.getSource().getServer()).units().keySet(), b));
        territory.then(Commands.literal("garrison").then(id
                .then(Commands.literal("clear").executes(c -> run(c.getSource(), () -> garrison(c.getSource(), StringArgumentType.getString(c, "id"), null))))
                .then(Commands.argument("count", IntegerArgumentType.integer(0, Garrison.MAX_COUNT))
                        .then(Commands.argument("mode", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("patrol", "guard"), b))
                                .then(Commands.argument("reinforce", IntegerArgumentType.integer(0, 86_400))
                                        .executes(c -> run(c.getSource(), () -> garrison(c.getSource(), StringArgumentType.getString(c, "id"),
                                                IntegerArgumentType.getInteger(c, "count"), StringArgumentType.getString(c, "mode"),
                                                IntegerArgumentType.getInteger(c, "reinforce"), "")))
                                        .then(garrisonUnits.executes(c -> run(c.getSource(), () -> garrison(c.getSource(), StringArgumentType.getString(c, "id"),
                                                IntegerArgumentType.getInteger(c, "count"), StringArgumentType.getString(c, "mode"),
                                                IntegerArgumentType.getInteger(c, "reinforce"), StringArgumentType.getString(c, "units"))))))))));
        root.then(territory);

        var depot = Commands.literal("depot").requires(s -> s.hasPermission(2));
        depot.then(Commands.literal("list").executes(c -> run(c.getSource(), () -> {
            var all = DepotBook.get(c.getSource().getServer()).all().values();
            if (all.isEmpty()) return "No depots";
            StringBuilder s = new StringBuilder();
            all.forEach(d -> s.append(d.id()).append(" at ").append(d.pos().toShortString()).append(d.infinite() ? " (infinite)" : "")
                    .append(" for ").append(d.team().isEmpty() ? "anyone" : d.team()).append('\n'));
            return s.toString().stripTrailing();
        })));
        depot.then(Commands.literal("add").then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("team", StringArgumentType.word()).then(Commands.argument("infinite", BoolArgumentType.bool())
                        .executes(c -> run(c.getSource(), () -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            var hit = p.pick(8, 1f, false);
                            if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block) || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
                                throw new IllegalArgumentException("Look at the container within 8 blocks");
                            BlockPos pos = block.getBlockPos();
                            if (Resupply.containerAt(p.serverLevel(), pos) == null) throw new IllegalArgumentException("That block holds no items");
                            String team = StringArgumentType.getString(c, "team");
                            var d = new Depot(StringArgumentType.getString(c, "id"), team.equals("any") ? "" : team,
                                    p.level().dimension().location().toString(), pos, BoolArgumentType.getBool(c, "infinite"));
                            DepotBook.get(c.getSource().getServer()).put(d);
                            return "Depot " + d.id() + " at " + pos.toShortString();
                        }))))));
        depot.then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(DepotBook.get(c.getSource().getServer()).all().keySet(), b))
                .executes(c -> run(c.getSource(), () -> {
                    DepotBook.get(c.getSource().getServer()).remove(StringArgumentType.getString(c, "id"));
                    return "Depot removed";
                }))));
        root.then(depot);

        // /soldiers assign <team|name> <order> [territory]: standing orders for soldiers nobody owns (garrisons, enemy patrols)
        var assign = Commands.literal("assign").requires(s -> s.hasPermission(2));
        var who = Commands.argument("who", StringArgumentType.word()).suggests((c, b) ->
                SharedSuggestionProvider.suggest(SoldierAi.soldierBots().stream().map(bot -> bot.team).distinct(), b));
        for (SoldierOrder.Kind kind : List.of(SoldierOrder.Kind.PATROL, SoldierOrder.Kind.GUARD, SoldierOrder.Kind.HOLD, SoldierOrder.Kind.FOLLOW)) {
            var node = Commands.literal(kind.id);
            if (kind.needsRegion) {
                node.then(Commands.argument("territory", StringArgumentType.word()).suggests((c, b) ->
                                SharedSuggestionProvider.suggest(TerritoryBook.get(c.getSource().getServer()).all().keySet(), b))
                        .executes(c -> run(c.getSource(), () -> assign(c.getSource(), StringArgumentType.getString(c, "who"),
                                SoldierOrder.in(kind, StringArgumentType.getString(c, "territory"))))));
            } else {
                node.executes(c -> run(c.getSource(), () -> assign(c.getSource(), StringArgumentType.getString(c, "who"), SoldierOrder.of(kind))));
            }
            who.then(node);
        }
        root.then(assign.then(who));
    }

    private static String garrison(CommandSourceStack s, String id, Garrison g) {
        var book = TerritoryBook.get(s.getServer());
        Territory t = book.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown territory: " + id);
        book.put(t.withGarrison(g));
        return g == null ? id + " raises no garrison; its soldiers stay as they are" : id + " keeps " + g.count() + " soldiers (" + g.mode().id + ")";
    }

    private static String garrison(CommandSourceStack s, String id, int count, String mode, int reinforce, String units) {
        var kind = SoldierOrder.Kind.of(mode);
        List<String> list = units.isEmpty() ? List.of() : Arrays.asList(units.split(","));
        var known = SoldierRoster.get(s.getServer()).units().keySet();
        for (String u : list) if (!known.contains(u)) throw new IllegalArgumentException("Unknown saved variant: " + u);
        return garrison(s, id, new Garrison(count, list, kind, reinforce));
    }

    private static int create(CommandSourceStack s, String id, int radius, String team) {
        return run(s, () -> {
            Vec3 at = s.getPosition();
            var level = s.getLevel();
            var t = new Territory(id, id, team, level.dimension().location().toString(), at, radius,
                    level.getMinBuildHeight(), level.getMaxBuildHeight(), List.of(), null);
            TerritoryBook.get(s.getServer()).put(t);
            return "Territory " + id + ": radius " + radius + " around " + BlockPos.containing(at).toShortString();
        });
    }

    private static String waypoint(CommandSourceStack s, String id, boolean add) throws CommandSyntaxException {
        var book = TerritoryBook.get(s.getServer());
        Territory t = book.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown territory: " + id);
        if (!add) {
            book.put(t.withRoute(List.of()));
            return "Route cleared; soldiers pick their own points";
        }
        var route = new java.util.ArrayList<>(t.route());
        route.add(s.getPlayerOrException().position());
        book.put(t.withRoute(route));
        return "Waypoint " + route.size() + " added to " + id;
    }

    /** Gives every soldier of a side (or one by name) that nobody owns a standing order. Companions take orders from their owner only. */
    private static String assign(CommandSourceStack s, String who, SoldierOrder order) {
        if (order.kind().needsRegion && TerritoryBook.get(s.getServer()).get(order.region()) == null)
            throw new IllegalArgumentException("Unknown territory: " + order.region());
        int n = 0;
        for (SoldierAi.Bot b : SoldierAi.soldierBots()) {
            if (b.companion != null || !(b.team.equals(who) || b.name.equalsIgnoreCase(who))) continue;
            var p = s.getServer().getPlayerList().getPlayerByName(b.name);
            if (p == null) continue;
            Orders.apply(b, p.position(), order);
            n++;
        }
        if (n == 0) throw new IllegalArgumentException("No unowned soldiers match " + who);
        return order.label() + ": " + n + " soldiers";
    }
}
