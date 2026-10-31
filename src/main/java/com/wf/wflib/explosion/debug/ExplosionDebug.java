package com.wf.wflib.explosion.debug;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wf.wflib.explosion.api.ExplosionAEF;
import com.wf.wflib.explosion.api.ExplosionCreator;
import com.wf.wflib.explosion.api.FragmentationUtil;
import com.wf.wflib.explosion.api.BlockAllocatorStandard;
import com.wf.wflib.explosion.api.BlockProcessorStandard;
import com.wf.wflib.explosion.api.EntityProcessorCross;
import com.wf.wflib.explosion.api.PlayerProcessorStandard;
import com.wf.wflib.warhead.api.Warheads;
import com.wf.wflib.explosion.api.EMPCreator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib frag|boom|debug explosions}. */
public final class ExplosionDebug {
    private ExplosionDebug() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("frag")
                        .executes(ctx -> spawnFrag(ctx.getSource(), 16, 1.2))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 512))
                                .executes(ctx -> spawnFrag(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count"), 1.2))
                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.0, 10.0))
                                        .executes(ctx -> spawnFrag(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                DoubleArgumentType.getDouble(ctx, "speed"))))))
                .then(Commands.literal("boom")
                        .executes(ctx -> boom(ctx.getSource(), "standard"))
                        .then(Commands.literal("real")
                                .then(Commands.argument("size", DoubleArgumentType.doubleArg(1.0, 30.0))
                                        .executes(ctx -> realBoom(ctx.getSource(),
                                                (float) DoubleArgumentType.getDouble(ctx, "size")))))
                        .then(Commands.literal("emp")
                                .executes(ctx -> emp(ctx.getSource(), 48))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                        .executes(ctx -> emp(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("empstun").executes(ctx -> empStun(ctx.getSource())))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        new String[]{"small", "standard", "large"}, b))
                                .executes(ctx -> boom(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "preset")))))
                .then(Commands.literal("debug")
                        .then(explosionsNode())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> explosionsNode() {
        return Commands.literal("explosions")
                .executes(ctx -> setExplosionDebug(ctx.getSource(), !ExplosionTrace.enabled()))
                .then(Commands.literal("on").executes(ctx -> setExplosionDebug(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setExplosionDebug(ctx.getSource(), false)))
                .then(Commands.literal("status").executes(ctx -> {
                    var traces = ExplosionTrace.recent();
                    long now = ctx.getSource().getLevel().getGameTime();
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                            "recording=%s  traces=%d  server t=%d",
                            ExplosionTrace.enabled(), traces.size(), now)), false);
                    for (var t : traces) {
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                                "  t=%d (age %d)  size=%.1f  rays=%d/%d  cone=%s",
                                t.gameTime(), now - t.gameTime(), t.size(), t.rays().size(),
                                t.totalRays(), t.axis() == null ? "none"
                                        : String.format("(%.2f, %.2f, %.2f) +/-%.0f deg",
                                                t.axis().x, t.axis().y, t.axis().z,
                                                t.halfAngleDeg()))), false);
                        for (var v : t.victims()) {
                            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "    %-13s %-6s dmg=%.1f at %.1f %.1f %.1f", v.verdict(),
                                    v.name(), v.damage(), v.pos().x, v.pos().y, v.pos().z)), false);
                        }
                    }
                    return 1;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    ExplosionTrace.clear();
                    ctx.getSource().sendSuccess(() -> Component.literal("Cleared recorded blasts."), false);
                    return 1;
                }));
    }

    private static int setExplosionDebug(CommandSourceStack source, boolean value) {
        ExplosionTrace.setEnabled(value);
        source.sendSuccess(() -> Component.literal("Explosion raycast recording " + (value ? "on" : "off")
                + (value ? " (singleplayer only to draw)" : "")), false);
        return 1;
    }

    /** Scatters a spherical burst of bomblets from the player's eye position. */
    private static int spawnFrag(CommandSourceStack src, int count, double speed) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Vec3 origin = player.getEyePosition();
        FragmentationUtil.burst(player.serverLevel(), origin, count, speed);
        src.sendSuccess(() -> Component.literal("Spawned " + count + " bomblets"), true);
        return count;
    }

    /** Draws a blast's effect without a warhead behind it: the {@link ExplosionCreator} call a missile makes. */
    private static int boom(CommandSourceStack src, String preset) {
        Vec3 pos = src.getPosition();
        ServerLevel level = src.getLevel();
        switch (preset) {
            case "small" -> ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
            case "large" -> ExplosionCreator.composeEffectLarge(level, pos.x, pos.y, pos.z);
            default -> ExplosionCreator.composeEffectStandard(level, pos.x, pos.y, pos.z);
        }
        src.sendSuccess(() -> Component.literal("Blast effect (" + preset + ") at "
                + BlockPos.containing(pos)), true);
        return 1;
    }

    private static int emp(CommandSourceStack src, int radius) {
        Vec3 pos = src.getPosition();
        EMPCreator.compose(src.getLevel(), pos.x, pos.y, pos.z, radius);
        src.sendSuccess(() -> Component.literal("EMP effect r" + radius + " at " + BlockPos.containing(pos)), true);
        return 1;
    }

    private static int empStun(CommandSourceStack src) {
        Vec3 pos = src.getPosition();
        EMPCreator.composeStun(src.getLevel(), pos.x, pos.y, pos.z);
        return 1;
    }

    /** Standard warhead blast of {@code size} at the source: blocks, entities, effect. */
    private static int realBoom(CommandSourceStack src, float size) {
        Vec3 pos = src.getPosition();
        ServerLevel level = src.getLevel();
        var xnt = new ExplosionAEF(level, pos.x, pos.y, pos.z, size, src.getEntity());
        xnt.setBlockAllocator(new BlockAllocatorStandard(size < 8 ? 16 : 32));
        xnt.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
        xnt.setEntityProcessor(new EntityProcessorCross());
        xnt.setPlayerProcessor(new PlayerProcessorStandard());
        xnt.explode();
        if (size >= Warheads.STANDARD_BLAST_SIZE * 0.5f) {
            ExplosionCreator.composeEffectLarge(level, pos.x, pos.y, pos.z);
        } else {
            ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
        }
        src.sendSuccess(() -> Component.literal("Blast " + size + " at " + BlockPos.containing(pos)), true);
        return 1;
    }
}
