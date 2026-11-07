package com.wf.wflib.soldier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Stream;

/** {@code /soldiers voices ...}: list, reload, pick the voice of allies / enemies / one NPC, preview, open the menu. */
final class VoiceCommands {
    /** The team names the gun mods register their allied and hostile soldiers under. */
    static final String ALLIES = "friendly", ENEMIES = "enemy";

    private VoiceCommands() {}

    private interface Apply { int run(CommandContext<CommandSourceStack> c, String binding); }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("voices")
                .executes(c -> list(c.getSource()))
                .then(Commands.literal("menu").executes(c -> VoicePackets.open(c.getSource().getPlayerOrException())))
                .then(Commands.literal("reload").executes(c -> {
                    try {
                        SoldierVoices.reload();
                        VoiceDelivery.refresh(c.getSource().getServer());
                        return result(c.getSource(), "Loaded " + SoldierVoices.ids() + ". Local clients: F3+T; remote clients receive a new resource-pack offer.");
                    } catch (Exception e) {
                        c.getSource().sendFailure(Component.literal("Voice reload failed: " + e.getMessage())); return 0;
                    }
                }))
                .then(Commands.literal("allies").then(voiceArgs((c, binding) -> team(c, ALLIES, binding))))
                .then(Commands.literal("enemies").then(voiceArgs((c, binding) -> team(c, ENEMIES, binding))))
                .then(Commands.literal("team").then(Commands.argument("team", StringArgumentType.word())
                        .then(voiceArgs((c, binding) -> team(c, StringArgumentType.getString(c, "team"), binding)))))
                .then(Commands.literal("npc").then(Commands.argument("name", StringArgumentType.word())
                        .then(voiceArgs(VoiceCommands::npc))))
                .then(Commands.literal("preview").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(VoicePack.EVENTS, b))
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    var bot = SoldierAi.botOf(name);
                                    var player = c.getSource().getServer().getPlayerList().getPlayerByName(name);
                                    if (bot == null || player == null || !SoldierAi.scripted(player)) return result(c.getSource(), "No such NPC");
                                    boolean played = SoldierVoices.say(player, bot, StringArgumentType.getString(c, "event"));
                                    return result(c.getSource(), played ? "Callout emitted" : "Silent: no assigned pack/line, unconscious, or cooldown");
                                }))));
    }

    /** {@code <pack|none> [speaker|auto]}; "none" (or "default" for an NPC) is the empty binding. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> voiceArgs(Apply apply) {
        return Commands.argument("pack", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("none", "default"), SoldierVoices.ids().stream()), b))
                .executes(c -> apply.run(c, binding(c, "")))
                .then(Commands.argument("speaker", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("auto"),
                                SoldierVoices.catalog().getOrDefault(StringArgumentType.getString(c, "pack"), List.of()).stream()), b))
                        .executes(c -> apply.run(c, binding(c, StringArgumentType.getString(c, "speaker")))));
    }

    private static String binding(CommandContext<CommandSourceStack> c, String speaker) {
        String pack = StringArgumentType.getString(c, "pack");
        return pack.equals("none") || pack.equals("default") ? "" : SoldierVoices.binding(pack, speaker);
    }

    private static int team(CommandContext<CommandSourceStack> c, String team, String binding) {
        try {
            SoldierVoices.team(team, binding);
            return result(c.getSource(), binding.isEmpty() ? "Team " + team + " is silent" : "Team " + team + " now speaks as " + binding);
        } catch (Exception e) { c.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int npc(CommandContext<CommandSourceStack> c, String binding) {
        String name = StringArgumentType.getString(c, "name");
        var bot = SoldierAi.botOf(name);
        if (bot == null) { c.getSource().sendFailure(Component.literal("No such NPC: " + name)); return 0; }
        if (!SoldierVoices.valid(binding)) { c.getSource().sendFailure(Component.literal("Unknown voice pack or speaker")); return 0; }
        bot.voice = binding;
        return result(c.getSource(), binding.isEmpty() ? name + " follows its team's voice" : name + " now speaks as " + binding);
    }

    private static int list(CommandSourceStack source) {
        var teams = SoldierVoices.teams();
        result(source, "Allies: " + teams.getOrDefault(ALLIES, "silent") + ", enemies: " + teams.getOrDefault(ENEMIES, "silent"));
        SoldierVoices.catalog().forEach((pack, speakers) -> result(source, pack + ": " + String.join(", ", speakers)));
        return 1;
    }

    private static int result(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false); return 1;
    }
}
