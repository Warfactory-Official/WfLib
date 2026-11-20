package com.wf.wflib.contract.command;

import com.wf.wflib.contract.Money;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.wf.wflib.contract.ContractDef;
import com.wf.wflib.contract.ContractService;
import com.wf.wflib.contract.Contracts;
import com.wf.wflib.contract.SessionBook;
import com.wf.wflib.contract.profile.ProfileBook;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** {@code /contract}: the contract board for players who have no terminal yet. */
public final class PmcCommands {
    private PmcCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("contract")
                .then(Commands.literal("offers").executes(PmcCommands::offers))
                .then(Commands.literal("accept").then(Commands.argument("contract", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Contracts.all().stream().map(x -> x.id().toString()), b))
                        .executes(PmcCommands::accept)))
                .then(Commands.literal("status").executes(PmcCommands::status))
                .then(Commands.literal("daily")
                        .executes(c -> {
                            var offers = Contracts.all().stream().filter(x -> x.id().getPath().startsWith("daily/")).toList();
                            if (offers.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No daily contracts yet.").withStyle(ChatFormatting.GRAY), false);
                            for (ContractDef offer : offers) {
                                c.getSource().sendSuccess(() -> Component.literal(offer.id().getPath() + "  " + offer.title() + ": " + Money.format(offer.money()) + ", +" + offer.rep() + " rep, needs "
                                        + offer.gate().id()).withStyle(ChatFormatting.YELLOW), false);
                            }
                            return offers.size();
                        })
                        .then(Commands.literal("reroll").requires(s -> s.hasPermission(2)).executes(c -> {
                            var server = c.getSource().getServer();
                            com.wf.wflib.contract.daily.DailyBoard.get(server).redraw(server, com.wf.wflib.contract.difficulty.Difficulty.day(server));
                            c.getSource().sendSuccess(() -> Component.literal("Today's board was redrawn.").withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        })))
                .then(Commands.literal("difficulty")
                        .executes(c -> {
                            var server = c.getSource().getServer();
                            c.getSource().sendSuccess(() -> Component.literal(com.wf.wflib.contract.difficulty.Difficulty.get(server).describe(server)).withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        })
                        .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                            com.wf.wflib.contract.difficulty.Difficulty.get(c.getSource().getServer()).reset();
                            c.getSource().sendSuccess(() -> Component.literal("Difficulty is back to the config's defaults.").withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        }))
                        .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("setting", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("mode", "start", "rate", "max", "step", "weight", "worldgen", "reward"), b))
                                        .then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                                            var server = c.getSource().getServer();
                                            try {
                                                com.wf.wflib.contract.difficulty.Difficulty.get(server).set(StringArgumentType.getString(c, "setting"), StringArgumentType.getString(c, "value"));
                                            } catch (IllegalArgumentException e) {
                                                c.getSource().sendFailure(Component.literal("Not allowed: " + e.getMessage()));
                                                return 0;
                                            }
                                            c.getSource().sendSuccess(() -> Component.literal(com.wf.wflib.contract.difficulty.Difficulty.get(server).describe(server)).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        })))))
                .then(Commands.literal("abandon").executes(PmcCommands::abandon))
                .then(Commands.literal("profile").executes(PmcCommands::profile))
                .then(Commands.literal("heat").executes(PmcCommands::heat))
                .then(Commands.literal("analyze").executes(PmcCommands::analyze))
                .then(Commands.literal("news").executes(c -> {
                    var lines = com.wf.wflib.contract.news.News.get(c.getSource().getServer()).latest(8);
                    if (lines.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Nothing to report.").withStyle(ChatFormatting.GRAY), false);
                    lines.forEach(l -> c.getSource().sendSuccess(() -> Component.literal("- " + l).withStyle(ChatFormatting.WHITE), false));
                    return lines.size();
                }))
                .then(Commands.literal("engagement").then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(c -> {
                    boolean ok = com.wf.wflib.contract.engagement.EngagementService.startNow(c.getSource().getServer());
                    if (!ok) c.getSource().sendFailure(Component.literal("One is running, or there is nothing to raise (needs saved friendly and enemy soldiers)."));
                    return ok ? 1 : 0;
                })))
                .then(Commands.literal("blacksite")
                        .then(Commands.literal("status").executes(PmcCommands::blackSite))
                        .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(c -> {
                            boolean ok = com.wf.wflib.contract.blacksite.BlackSiteService.start(c.getSource().getServer());
                            if (!ok) c.getSource().sendFailure(Component.literal("Already running, or no ground found."));
                            return ok ? 1 : 0;
                        }))
                        .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(c -> {
                            com.wf.wflib.contract.blacksite.BlackSiteService.stop(c.getSource().getServer(), "The Black Site was shut down.");
                            return 1;
                        })))
                .then(Commands.literal("bribe").then(Commands.argument("faction", StringArgumentType.word())
                        .executes(c -> bribe(c, 10)))));
    }

    private static int offers(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        var list = new java.util.ArrayList<>(ContractService.offers(p.getServer(), p.getUUID()));
        for (var lead : ContractService.leads(p.getServer(), p.getUUID())) {
            list.add(lead.getKey());
            c.getSource().sendSuccess(() -> Component.literal("Intel (" + lead.getValue().name().toLowerCase() + "): " + lead.getKey().id()).withStyle(ChatFormatting.DARK_AQUA), false);
        }
        if (list.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No contracts are available to you.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (ContractDef def : list) {
            c.getSource().sendSuccess(() -> Component.literal(def.id() + "  ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(def.title() + ": " + Money.format(def.money()) + ", +" + def.rep() + " rep, "
                            + def.timeLimitMinutes() + " min, " + def.minDistance() + "-" + def.maxDistance() + " blocks").withStyle(ChatFormatting.WHITE)), false);
        }
        return list.size();
    }

    private static int accept(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        ResourceLocation id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "contract");
        var def = id == null ? null : Contracts.get(id).orElse(null);
        if (def == null) {
            c.getSource().sendFailure(Component.literal("No such contract."));
            return 0;
        }
        try {
            ContractService.accept(p, def);
            return 1;
        } catch (ContractService.Refused e) {
            c.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int status(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        var s = SessionBook.get(p.getServer()).of(p.getUUID());
        if (s.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("You have no contract.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        var def = Contracts.get(s.get().def());
        long left = Math.max(0, s.get().deadline() - p.serverLevel().getGameTime()) / 20;
        c.getSource().sendSuccess(() -> Component.literal((def.isPresent() ? def.get().title() : s.get().def().toString())
                + " at " + (int) s.get().centre().x + ", " + (int) s.get().centre().z + ": " + left / 60 + " min " + left % 60 + " s left").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int abandon(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            ContractService.abandon(c.getSource().getPlayerOrException());
            return 1;
        } catch (ContractService.Refused e) {
            c.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int blackSite(CommandContext<CommandSourceStack> c) {
        var book = com.wf.wflib.contract.blacksite.BlackSiteBook.get(c.getSource().getServer());
        long now = c.getSource().getServer().overworld().getGameTime();
        if (!book.active) {
            c.getSource().sendSuccess(() -> Component.literal("No Black Site is live.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        var holder = book.capture.holder() == null ? null : c.getSource().getServer().getProfileCache().get(book.capture.holder()).map(g -> g.getName()).orElse("someone");
        c.getSource().sendSuccess(() -> Component.literal("Black Site at " + book.pos.getX() + ", " + book.pos.getZ() + ", held by "
                + (holder == null ? "nobody" : holder) + ", " + Math.max(0, (book.end - now) / 24000) + " days left.").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int analyze(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        try {
            var lead = ContractService.analyze(p).orElseThrow();
            c.getSource().sendSuccess(() -> Component.literal("Analysis: " + lead.getValue().name().toLowerCase() + " intel on " + lead.getKey().title()
                    + ". It is in your offers.").withStyle(ChatFormatting.AQUA), false);
            return 1;
        } catch (ContractService.Refused e) {
            c.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int heat(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        long now = p.serverLevel().getGameTime();
        var meters = com.wf.wflib.contract.heat.HeatBook.get(p.getServer()).of(p.getUUID());
        if (meters.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Nobody is looking for you.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        meters.forEach((faction, m) -> {
            double v = m.value(now);
            if (v >= 0.5) c.getSource().sendSuccess(() -> Component.literal(faction + ": " + Math.round(v) + " heat"
                    + (com.wf.wflib.contract.heat.HuntSize.restricted(v) ? " (closed to you)" : v >= com.wf.wflib.contract.heat.HuntSize.HUNT_AT ? " (hunted)" : "")).withStyle(ChatFormatting.RED), false);
        });
        return meters.size();
    }

    private static int bribe(CommandContext<CommandSourceStack> c, int points) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        String faction = StringArgumentType.getString(c, "faction");
        if (com.wf.wflib.contract.heat.HeatService.bribe(p, faction, points)) {
            c.getSource().sendSuccess(() -> Component.literal("A contact smooths things over: -" + points + " heat with " + faction + ".").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        c.getSource().sendFailure(Component.literal("Nothing to buy off, or you cannot afford it."));
        return 0;
    }

    private static int profile(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        var prof = ProfileBook.get(p.getServer()).of(p.getUUID());
        c.getSource().sendSuccess(() -> Component.literal("Rank " + prof.rank().id() + ", " + prof.rep + " rep. " + prof.completed + " done, "
                + prof.failed + " failed, " + prof.totalKills() + " kills, earned " + Money.format(prof.earnings) + ".").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }
}
