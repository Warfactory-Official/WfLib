package com.wf.wflib.round.debug;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.RocketEntity;
import com.wf.wflib.round.api.Rounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib round <preset> [speed [count]]}. */
public final class RoundCommands {
    private RoundCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("round")
                        .then(Commands.argument("preset", ResourceLocationArgument.id())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(
                                        KineticPresets.all().stream().map(KineticPreset::id), b))
                                .executes(ctx -> fireRound(ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "preset"), -1, 1))
                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.0, 400.0))
                                        .executes(ctx -> fireRound(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "preset"),
                                                DoubleArgumentType.getDouble(ctx, "speed"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 4096))
                                                .executes(ctx -> fireRound(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "preset"),
                                                        DoubleArgumentType.getDouble(ctx, "speed"),
                                                        IntegerArgumentType.getInteger(ctx, "count"))))))));
    }

    /**
     * {@code speed} blocks/tick, < 0 => muzzle speed; motor => {@code RocketEntity}. {@code count > 1}: headings spread
     * +-15 deg (look bench). From the source position along its rotation (`execute anchored eyes positioned ^ ^ ^` = eye).
     */
    private static int fireRound(CommandSourceStack src, ResourceLocation id, double speed, int count) {
        KineticPreset preset = KineticPresets.get(id);
        if (preset == null) {
            src.sendFailure(Component.literal("No round " + id));
            return 0;
        }
        Vec3 dir = Vec3.directionFromRotation(src.getRotation());
        if (preset.motorAccel() > 0) {
            int rid = RocketEntity.fire(src.getLevel(), preset, src.getPosition(), dir, 0.0f,
                    Vec3.ZERO, src.getEntity(), null, null).getId();
            src.sendSuccess(() -> Component.literal("Rocket " + rid + " (" + id + ")"), false);
            return 1;
        }
        long key = speed < 0 ? Rounds.fire(src.getLevel(), preset, src.getPosition(), dir, 0.0f,
                Vec3.ZERO, src.getEntity(), null)
                : Rounds.launch(src.getLevel(), preset, src.getPosition(), dir.scale(speed),
                src.getEntity(), null);
        for (int i = 1; i < count; i++) {
            Vec3 d = Vec3.directionFromRotation(src.getRotation().x + (src.getLevel().random.nextFloat() - 0.5f) * 30,
                    src.getRotation().y + (src.getLevel().random.nextFloat() - 0.5f) * 30);
            Rounds.launch(src.getLevel(), preset, src.getPosition(),
                    d.scale(speed < 0 ? preset.muzzleSpeed() : speed), src.getEntity(), null);
        }
        src.sendSuccess(() -> Component.literal("Round " + key + " (" + id + ")"), false);
        return 1;
    }
}
