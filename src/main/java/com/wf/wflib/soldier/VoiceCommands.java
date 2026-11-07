package com.wf.wflib.soldier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

final class VoiceCommands {
    private VoiceCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("voices")
                .executes(c -> result(c.getSource(), "Voice packs: " + SoldierVoices.ids()))
                .then(Commands.literal("reload").executes(c -> {
                    try {
                        SoldierVoices.reload();
                        VoiceDelivery.refresh(c.getSource().getServer());
                        return result(c.getSource(), "Loaded " + SoldierVoices.ids() + ". Local clients: F3+T; remote clients receive a new resource-pack offer.");
                    } catch (Exception e) {
                        c.getSource().sendFailure(Component.literal("Voice reload failed: " + e.getMessage())); return 0;
                    }
                }))
                .then(Commands.literal("team").then(Commands.argument("team", StringArgumentType.word())
                        .then(Commands.argument("pack", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SoldierVoices.ids(), b))
                                .executes(c -> {
                                    try {
                                        SoldierVoices.team(StringArgumentType.getString(c, "team"), StringArgumentType.getString(c, "pack"));
                                        return result(c.getSource(), "Team voice assignment saved");
                                    } catch (Exception e) { c.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
                                }))))
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

    private static int result(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false); return 1;
    }
}
