package com.wf.wflib.fire.debug;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wf.wflib.fire.api.FireLingeringEntity;
import com.wf.wflib.fire.api.FireType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/** {@code /wflib boom fire [normal|phosphorus|thermite] [width] [height]}. */
public final class FireDebug {
    private FireDebug() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var type = Commands.argument("type", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                        new String[]{"normal", "phosphorus", "thermite"}, b))
                .executes(ctx -> spawn(ctx.getSource(), typeOf(ctx.getArgument("type", String.class)), 3.0F, 2.0F))
                .then(Commands.argument("width", FloatArgumentType.floatArg(0.5F, 64.0F))
                        .executes(ctx -> spawn(ctx.getSource(), typeOf(ctx.getArgument("type", String.class)),
                                FloatArgumentType.getFloat(ctx, "width"), 2.0F))
                        .then(Commands.argument("height", FloatArgumentType.floatArg(0.5F, 16.0F))
                                .executes(ctx -> spawn(ctx.getSource(), typeOf(ctx.getArgument("type", String.class)),
                                        FloatArgumentType.getFloat(ctx, "width"),
                                        FloatArgumentType.getFloat(ctx, "height")))));
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("boom")
                        .then(Commands.literal("fire")
                                .executes(ctx -> spawn(ctx.getSource(), FireType.NORMAL, 3.0F, 2.0F))
                                .then(type))));
    }

    private static FireType typeOf(String name) {
        return FireType.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private static int spawn(CommandSourceStack src, FireType type, float width, float height) {
        Vec3 at = src.getPosition();
        FireLingeringEntity.spawn(src.getLevel(), at.x, at.y, at.z, width, height, 300, type);
        return 1;
    }
}
