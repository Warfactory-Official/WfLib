package com.wf.wflib.colony.debug;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import com.wf.wflib.colony.api.GlyphidCaste;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib swarmbench ...}. */
public final class SwarmBenchCommand {
    private SwarmBenchCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("swarmbench")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal(SwarmBench.status(ctx.getSource().getLevel())), false);
                            return 1;
                        })
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2048))
                                        .executes(ctx -> SwarmBench.spawn(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"), 24.0))
                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(2.0, 256.0))
                                                .executes(ctx -> SwarmBench.spawn(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        DoubleArgumentType.getDouble(ctx, "radius")))
                                                .then(Commands.argument("caste", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            for (GlyphidCaste g : GlyphidCaste.VALUES) {
                                                                b.suggest(g.lowerName());
                                                            }
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            GlyphidCaste caste = GlyphidCaste.byName(
                                                                    StringArgumentType.getString(ctx, "caste"));
                                                            if (caste == null) {
                                                                ctx.getSource().sendFailure(
                                                                        Component.literal("Unknown caste."));
                                                                return 0;
                                                            }
                                                            return SwarmBench.spawn(ctx.getSource(),
                                                                    IntegerArgumentType.getInteger(ctx, "count"),
                                                                    DoubleArgumentType.getDouble(ctx, "radius"),
                                                                    caste);
                                                        })))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> SwarmBench.clear(ctx.getSource())))
                        .then(Commands.literal("report")
                                .executes(ctx -> SwarmBench.report(ctx.getSource())))
                        .then(Commands.literal("reset")
                                .executes(ctx -> SwarmBench.reset(ctx.getSource())))
                        .then(Commands.literal("profile")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.profile(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.profile(ctx.getSource(), false))))
                        .then(Commands.literal("dummies")
                                .executes(ctx -> SwarmBench.dummyReport(ctx.getSource()))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> SwarmBench.dummyClear(ctx.getSource())))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> SwarmBench.dummyReset(ctx.getSource())))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 512))
                                        .executes(ctx -> SwarmBench.dummies(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"), 30.0, 0.0))
                                        .then(Commands.argument("spread", DoubleArgumentType.doubleArg(1.0, 256.0))
                                                .executes(ctx -> SwarmBench.dummies(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        DoubleArgumentType.getDouble(ctx, "spread"), 0.0))
                                                .then(Commands.argument("drift", DoubleArgumentType.doubleArg(0.0, 64.0))
                                                        .executes(ctx -> SwarmBench.dummies(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                DoubleArgumentType.getDouble(ctx, "spread"),
                                                                DoubleArgumentType.getDouble(ctx, "drift")))))))
                        .then(Commands.literal("entitycollisions")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.entityCollisions(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.entityCollisions(ctx.getSource(), false))))
                        .then(Commands.literal("pathcache")
                                .then(Commands.argument("megabytes", DoubleArgumentType.doubleArg(0.05, 256.0))
                                        .executes(ctx -> SwarmBench.pathCache(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "megabytes")))))
                        .then(Commands.literal("charge")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.charge(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.charge(ctx.getSource(), false))))
                        .then(Commands.literal("sharedpaths")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.sharedPaths(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.sharedPaths(ctx.getSource(), false))))
                        .then(Commands.literal("push")
                                .then(Commands.literal("vanilla")
                                        .executes(ctx -> SwarmBench.push(ctx.getSource(), true)))
                                .then(Commands.literal("skip")
                                        .executes(ctx -> SwarmBench.push(ctx.getSource(), false))))
                        .then(Commands.literal("separation")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.separation(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.separation(ctx.getSource(), false))))
                        .then(Commands.literal("density")
                                .executes(ctx -> SwarmBench.density(ctx.getSource())))
                        .then(Commands.literal("census")
                                .executes(ctx -> SwarmBench.census(ctx.getSource(), 6.0))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5, 256.0))
                                        .executes(ctx -> SwarmBench.census(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "radius")))))
                        .then(Commands.literal("squads")
                                .executes(ctx -> SwarmBench.squads(ctx.getSource())))
                        .then(Commands.literal("deaths")
                                .executes(ctx -> GlyphidDeaths.command(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> GlyphidDeaths.command(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> GlyphidDeaths.command(ctx.getSource(), false))))
                        .then(Commands.literal("arena")
                                .executes(ctx -> GlyphidArena.report(ctx.getSource()))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> GlyphidArena.clear(ctx.getSource())))
                                .then(Commands.literal("launch")
                                        .executes(ctx -> GlyphidArena.launch(ctx.getSource(), 150))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2048))
                                                .executes(ctx -> GlyphidArena.launch(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count")))
                                                .then(Commands.argument("caste", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            for (GlyphidCaste g : GlyphidCaste.VALUES) {
                                                                b.suggest(g.lowerName());
                                                            }
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            GlyphidCaste caste = GlyphidCaste.byName(
                                                                    StringArgumentType.getString(ctx, "caste"));
                                                            if (caste == null) {
                                                                ctx.getSource().sendFailure(
                                                                        Component.literal("Unknown caste."));
                                                                return 0;
                                                            }
                                                            return GlyphidArena.launch(ctx.getSource(),
                                                                    IntegerArgumentType.getInteger(ctx, "count"),
                                                                    caste);
                                                        }))))
                                .then(Commands.literal("reinforce")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2048))
                                                .then(Commands.argument("caste", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            for (GlyphidCaste g : GlyphidCaste.VALUES) {
                                                                b.suggest(g.lowerName());
                                                            }
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            GlyphidCaste caste = GlyphidCaste.byName(
                                                                    StringArgumentType.getString(ctx, "caste"));
                                                            if (caste == null) {
                                                                ctx.getSource().sendFailure(
                                                                        Component.literal("Unknown caste."));
                                                                return 0;
                                                            }
                                                            return GlyphidArena.reinforce(ctx.getSource(),
                                                                    IntegerArgumentType.getInteger(ctx, "count"),
                                                                    caste);
                                                        }))))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (String s : GlyphidArena.NAMES) {
                                                b.suggest(s);
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> GlyphidArena.build(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"), null))
                                        .then(Commands.argument("material", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    b.suggest("stone");
                                                    b.suggest("obsidian");
                                                    b.suggest("bedrock");
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> GlyphidArena.build(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "material"))))))
                        .then(Commands.literal("players")
                                .executes(ctx -> BenchPlayer.report(ctx.getSource()))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> BenchPlayer.clear(ctx.getSource())))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("at", Vec3Argument.vec3())
                                                .executes(ctx -> BenchPlayer.add(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        Vec3Argument.getVec3(ctx, "at"))))))
                        .then(Commands.literal("flowfield")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.flowField(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.flowField(ctx.getSource(), false))))
                        .then(Commands.literal("flowfields")
                                .executes(ctx -> SwarmBench.flowFields(ctx.getSource())))
                        .then(Commands.literal("bridges")
                                .executes(ctx -> SwarmBench.bridgeReport(ctx.getSource()))
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.bridges(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.bridges(ctx.getSource(), false))))
                        .then(Commands.literal("sim")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.simTier(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.simTier(ctx.getSource(), false)))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(0.0, 4096.0))
                                        .executes(ctx -> SwarmBench.simRange(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "range")))))
                        .then(Commands.literal("march")
                                .then(Commands.argument("to", Vec3Argument.vec3())
                                        .executes(ctx -> SwarmBench.march(ctx.getSource(),
                                                Vec3Argument.getVec3(ctx, "to")))))
                        .then(Commands.literal("simasync")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.simAsync(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.simAsync(ctx.getSource(), false))))
                        .then(Commands.literal("simthread")
                                .executes(ctx -> SwarmBench.simThread(ctx.getSource())))
                        .then(Commands.literal("tiers")
                                .executes(ctx -> SwarmBench.tiers(ctx.getSource())))
                        .then(Commands.literal("simcount")
                                .then(Commands.argument("from", Vec3Argument.vec3())
                                        .then(Commands.argument("to", Vec3Argument.vec3())
                                                .executes(ctx -> SwarmBench.simCount(ctx.getSource(),
                                                        Vec3Argument.getVec3(ctx, "from"),
                                                        Vec3Argument.getVec3(ctx, "to"))))))
                        .then(Commands.literal("blast")
                                .then(Commands.argument("size", DoubleArgumentType.doubleArg(1.0, 64.0))
                                        .executes(ctx -> SwarmBench.blast(ctx.getSource(),
                                                (float) DoubleArgumentType.getDouble(ctx, "size")))))
                        .then(Commands.literal("watcher")
                                .executes(ctx -> SwarmBench.watcher(ctx.getSource(), null))
                                .then(Commands.argument("at", Vec3Argument.vec3())
                                        .executes(ctx -> SwarmBench.watcher(ctx.getSource(),
                                                Vec3Argument.getVec3(ctx, "at")))))
                        .then(Commands.literal("stagger")
                                .then(Commands.literal("on")
                                        .executes(ctx -> SwarmBench.stagger(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> SwarmBench.stagger(ctx.getSource(), false))))
                        .then(Commands.literal("meleegoal")
                                .then(Commands.literal("vanilla")
                                        .executes(ctx -> SwarmBench.meleeGoal(ctx.getSource(), true)))
                                .then(Commands.literal("glyphid")
                                        .executes(ctx -> SwarmBench.meleeGoal(ctx.getSource(), false))))));
    }

}
