package com.wf.wflib.drone.debug;

import com.wf.wflib.core.api.WorkerPool;

import com.wf.wflib.sim.api.SimTier;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Set;
import java.util.ArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import com.wf.wflib.drone.DroneLaunchQueue;
import com.wf.wflib.drone.api.CoordinationModels;
import com.wf.wflib.drone.api.Formation;
import com.wf.wflib.drone.api.Formations;
import com.wf.wflib.drone.api.DroneTracker;
import com.wf.wflib.drone.api.DroneState;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wf.wflib.drone.DroneDrafts;
import com.wf.wflib.drone.api.DroneMission;
import com.wf.wflib.drone.api.DroneProgram;
import com.wf.wflib.drone.api.DroneTask;
import com.wf.wflib.drone.api.MineLoad;
import com.wf.wflib.drone.api.ControllableDrone;
import com.wf.wflib.drone.api.DroneEntity;
import com.wf.wflib.drone.api.DronePadBlockEntity;
import com.wf.wflib.drone.api.CrateEntity;
import com.wf.wflib.missile.api.WFTelemetryService;
import com.wf.wflib.missile.api.WFTelemetry;
import com.wf.wflib.drone.api.DroneSelfTest;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wf.wflib.drone.api.DroneAiScheduler;
import com.wf.wflib.drone.api.DroneSnapshot;
import com.wf.wflib.drone.api.SquadView;
import com.wf.wflib.drone.ai.coord.Slots;
import com.wf.wflib.drone.api.SquadAnchor;
import com.wf.wflib.drone.ai.state.Tuning;
import com.wf.wflib.drone.api.TerrainSampler;
import com.wf.wflib.core.api.WorldThread;
import com.wf.wflib.drone.api.DronePath;
import com.wf.wflib.drone.nav.PlannerStats;
import com.wf.wflib.drone.api.SimDrone;
import com.wf.wflib.drone.api.SimDroneManager;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.wf.wflib.mine.api.MinePreset;
import com.wf.wflib.mine.api.MinePresets;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code /wflib drone ...}. */
public final class DroneCommands {
    private DroneCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("drone")
                        .then(Commands.literal("list")
                                .executes(ctx -> listDrones(ctx.getSource())))
                        .then(Commands.literal("selftest")
                                .executes(ctx -> selfTest(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearDrones(ctx.getSource())))
                        .then(Commands.literal("recall")
                                .executes(ctx -> recallDrones(ctx.getSource())))
                        .then(Commands.literal("telemetry")
                                .executes(ctx -> droneTelemetry(ctx.getSource())))
                        .then(Commands.literal("threads")
                                .executes(ctx -> droneThreads(ctx.getSource())))
                        .then(Commands.literal("camera")
                                .executes(ctx -> CameraDebug.list(ctx.getSource()))
                                .then(Commands.literal("stats")
                                        .executes(ctx -> CameraDebug.stats(ctx.getSource()))
                                        .then(Commands.literal("reset")
                                                .executes(ctx -> CameraDebug.reset(ctx.getSource()))))
                                .then(Commands.literal("demo")
                                        .executes(ctx -> CameraDebug.demo(ctx.getSource())))
                                .then(Commands.literal("fit")
                                        .then(Commands.literal("recon")
                                                .executes(ctx -> CameraDebug.fit(ctx.getSource(), "recon")))
                                        .then(Commands.literal("standard")
                                                .executes(ctx -> CameraDebug.fit(ctx.getSource(), "standard")))
                                        .then(Commands.literal("none")
                                                .executes(ctx -> CameraDebug.fit(ctx.getSource(), "none")))))
                        .then(Commands.literal("muster")
                                .executes(ctx -> droneMuster(ctx.getSource())))
                        .then(Commands.literal("sim")
                                .executes(ctx -> droneSim(ctx.getSource(), null))
                                .then(Commands.literal("on")
                                        .executes(ctx -> droneSim(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> droneSim(ctx.getSource(), false))))
                        .then(Commands.literal("scenario")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (String s : SCENARIOS) {
                                                b.suggest(s);
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> scenario(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("strike")
                                .then(Commands.argument("destination", Vec3Argument.vec3())
                                        .executes(ctx -> strikeDrones(ctx.getSource(),
                                                Vec3Argument.getVec3(ctx, "destination"), 1, ""))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                                .executes(ctx -> strikeDrones(ctx.getSource(),
                                                        Vec3Argument.getVec3(ctx, "destination"),
                                                        IntegerArgumentType.getInteger(ctx, "count"), ""))
                                                .then(Commands.argument("warhead", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            for (ResourceLocation id : Warheads.ids()) {
                                                                b.suggest(id.getPath());
                                                            }
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> strikeDrones(ctx.getSource(),
                                                                Vec3Argument.getVec3(ctx, "destination"),
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                StringArgumentType.getString(ctx, "warhead")))))))
                        .then(Commands.literal("minelay")
                                .then(Commands.argument("destination", Vec3Argument.vec3())
                                        .executes(ctx -> minelayDrones(ctx.getSource(),
                                                Vec3Argument.getVec3(ctx, "destination"), 1, "",
                                                MineLoad.DEFAULT_MINES, MineLoad.DEFAULT_SPACING))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                                .executes(ctx -> minelayDrones(ctx.getSource(),
                                                        Vec3Argument.getVec3(ctx, "destination"),
                                                        IntegerArgumentType.getInteger(ctx, "count"), "",
                                                        MineLoad.DEFAULT_MINES, MineLoad.DEFAULT_SPACING))
                                                .then(Commands.argument("mine", StringArgumentType.word())
                                                        .suggests(SOWN_MINES)
                                                        .executes(ctx -> minelayDrones(ctx.getSource(),
                                                                Vec3Argument.getVec3(ctx, "destination"),
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                StringArgumentType.getString(ctx, "mine"),
                                                                MineLoad.DEFAULT_MINES, MineLoad.DEFAULT_SPACING))
                                                        .then(Commands.argument("mines",
                                                                        IntegerArgumentType.integer(1, MineLoad.MAX_MINES))
                                                                .executes(ctx -> minelayDrones(ctx.getSource(),
                                                                        Vec3Argument.getVec3(ctx, "destination"),
                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                        StringArgumentType.getString(ctx, "mine"),
                                                                        IntegerArgumentType.getInteger(ctx, "mines"),
                                                                        MineLoad.DEFAULT_SPACING))
                                                                .then(Commands.argument("spacing",
                                                                                DoubleArgumentType.doubleArg(MineLoad.MIN_SPACING,
                                                                                        MineLoad.MAX_SPACING))
                                                                        .executes(ctx -> minelayDrones(ctx.getSource(),
                                                                                Vec3Argument.getVec3(ctx, "destination"),
                                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                                StringArgumentType.getString(ctx, "mine"),
                                                                                IntegerArgumentType.getInteger(ctx, "mines"),
                                                                                DoubleArgumentType.getDouble(ctx, "spacing")))))))))
                        .then(Commands.literal("pad")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("destination", Vec3Argument.vec3())
                                                .executes(ctx -> configurePad(ctx.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                        Vec3Argument.getVec3(ctx, "destination"), 1, "vee",
                                                        Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath()))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                                        .executes(ctx -> configurePad(ctx.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                                Vec3Argument.getVec3(ctx, "destination"),
                                                                IntegerArgumentType.getInteger(ctx, "count"), "vee",
                                                                Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath()))
                                                        .then(Commands.argument("formation", StringArgumentType.word())
                                                                .executes(ctx -> configurePad(ctx.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                                        Vec3Argument.getVec3(ctx, "destination"),
                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                        StringArgumentType.getString(ctx, "formation"),
                                                                        Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath()))
                                                                .then(Commands.argument("spacing",
                                                                                DoubleArgumentType.doubleArg(
                                                                                        Formation.MIN_SPACING,
                                                                                        Formation.MAX_SPACING))
                                                                        .executes(ctx -> configurePad(ctx.getSource(),
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                                                Vec3Argument.getVec3(ctx, "destination"),
                                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                                StringArgumentType.getString(ctx, "formation"),
                                                                                DoubleArgumentType.getDouble(ctx, "spacing"), CoordinationModels.DEFAULT.getPath()))
                                                                        .then(Commands.argument("coordination", StringArgumentType.word())
                                                                                .suggests((c, b) -> {
                                                                                        for (ResourceLocation id : CoordinationModels.ids()) {
                                                                                            b.suggest(id.getPath());
                                                                                        }
                                                                                        return b.buildFuture();
                                                                                    })
                                                                                .executes(ctx -> configurePad(ctx.getSource(),
                                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                                                        Vec3Argument.getVec3(ctx, "destination"),
                                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                                        StringArgumentType.getString(ctx, "formation"),
                                                                                        DoubleArgumentType.getDouble(ctx, "spacing"),
                                                                                        StringArgumentType.getString(ctx, "coordination"))))))))))
                        .then(Commands.literal("dispatch")
                                .then(Commands.argument("destination", Vec3Argument.vec3())
                                        .executes(ctx -> dispatchDrones(ctx.getSource(),
                                                Vec3Argument.getVec3(ctx, "destination"), 1, "vee",
                                                Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath(),
                                                DroneMission.DEFAULT_LAUNCH_INTERVAL, true))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                                .executes(ctx -> dispatchDrones(ctx.getSource(),
                                                        Vec3Argument.getVec3(ctx, "destination"),
                                                        IntegerArgumentType.getInteger(ctx, "count"), "vee",
                                                        Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath(),
                                                        DroneMission.DEFAULT_LAUNCH_INTERVAL, true))
                                                .then(Commands.argument("formation", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            for (ResourceLocation id : Formations.ids()) {
                                                                b.suggest(id.getPath());
                                                            }
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> dispatchDrones(ctx.getSource(),
                                                                Vec3Argument.getVec3(ctx, "destination"),
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                StringArgumentType.getString(ctx, "formation"),
                                                                Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath(),
                                                                DroneMission.DEFAULT_LAUNCH_INTERVAL, true))
                                                        .then(Commands.argument("spacing",
                                                                        DoubleArgumentType.doubleArg(
                                                                                Formation.MIN_SPACING,
                                                                                Formation.MAX_SPACING))
                                                                .executes(ctx -> dispatchDrones(ctx.getSource(),
                                                                        Vec3Argument.getVec3(ctx, "destination"),
                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                        StringArgumentType.getString(ctx, "formation"),
                                                                        DoubleArgumentType.getDouble(ctx, "spacing"), CoordinationModels.DEFAULT.getPath(),
                                                                        DroneMission.DEFAULT_LAUNCH_INTERVAL, true))
                                                                .then(Commands.argument("coordination", StringArgumentType.word())
                                                                        .suggests((c, b) -> {
                                                                                for (ResourceLocation id : CoordinationModels.ids()) {
                                                                                    b.suggest(id.getPath());
                                                                                }
                                                                                return b.buildFuture();
                                                                            })
                                                                        .executes(ctx -> dispatchDrones(ctx.getSource(),
                                                                                Vec3Argument.getVec3(ctx, "destination"),
                                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                                StringArgumentType.getString(ctx, "formation"),
                                                                                DoubleArgumentType.getDouble(ctx, "spacing"),
                                                                                StringArgumentType.getString(ctx, "coordination"),
                                                                                DroneMission.DEFAULT_LAUNCH_INTERVAL, true))
                                                                        .then(Commands.argument("interval",
                                                                                        IntegerArgumentType.integer(0,
                                                                                                DroneMission.MAX_LAUNCH_INTERVAL))
                                                                                .executes(ctx -> dispatchDrones(ctx.getSource(),
                                                                                        Vec3Argument.getVec3(ctx, "destination"),
                                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                                        StringArgumentType.getString(ctx, "formation"),
                                                                                        DoubleArgumentType.getDouble(ctx, "spacing"),
                                                                                        StringArgumentType.getString(ctx, "coordination"),
                                                                                        IntegerArgumentType.getInteger(ctx, "interval"),
                                                                                        true)))))))))
                        .then(programCommand())));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tickScenarios(level);
        }
    }

    /** The {@code program} subtree: build a queue of steps one command at a time, then fly it. */
    private static LiteralArgumentBuilder<CommandSourceStack> programCommand() {
        LiteralArgumentBuilder<CommandSourceStack> program = Commands.literal("program")
                .executes(ctx -> listProgram(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> listProgram(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> clearProgram(ctx.getSource())))
                .then(Commands.literal("remove")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, DroneTask.MAX_STEPS))
                                .executes(ctx -> removeStep(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "index")))))
                .then(Commands.literal("exfil")
                        .executes(ctx -> addStep(ctx.getSource(), new DroneTask.Exfil())));

        for (DroneTask.Kind kind : new DroneTask.Kind[]{DroneTask.Kind.MOVE_TO, DroneTask.Kind.DELIVER,
                DroneTask.Kind.COLLECT, DroneTask.Kind.STRIKE, DroneTask.Kind.MINELAY}) {
            program = program.then(Commands.literal(kind.id())
                    .then(Commands.argument("at", Vec3Argument.vec3())
                            .executes(ctx -> addStep(ctx.getSource(),
                                    kind.at(Vec3Argument.getVec3(ctx, "at"))))));
        }

        program = program.then(Commands.literal("loiter")
                .then(Commands.argument("at", Vec3Argument.vec3())
                        .executes(ctx -> addStep(ctx.getSource(), new DroneTask.Loiter(
                                Vec3Argument.getVec3(ctx, "at"), DroneTask.Loiter.DEFAULT_RADIUS, 0)))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0,
                                        DroneTask.MAX_LOITER_RADIUS))
                                .executes(ctx -> addStep(ctx.getSource(), new DroneTask.Loiter(
                                        Vec3Argument.getVec3(ctx, "at"),
                                        DoubleArgumentType.getDouble(ctx, "radius"), 0)))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 36000))
                                        .executes(ctx -> addStep(ctx.getSource(), new DroneTask.Loiter(
                                                Vec3Argument.getVec3(ctx, "at"),
                                                DoubleArgumentType.getDouble(ctx, "radius"),
                                                IntegerArgumentType.getInteger(ctx, "seconds") * 20)))))));

        program = program.then(Commands.literal("hold")
                .then(Commands.argument("at", Vec3Argument.vec3())
                        .executes(ctx -> addStep(ctx.getSource(),
                                new DroneTask.Loiter(Vec3Argument.getVec3(ctx, "at"), 0.0, 0)))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 36000))
                                .executes(ctx -> addStep(ctx.getSource(), new DroneTask.Loiter(
                                        Vec3Argument.getVec3(ctx, "at"), 0.0,
                                        IntegerArgumentType.getInteger(ctx, "seconds") * 20))))));

        return program.then(Commands.literal("launch")
                .executes(ctx -> launchProgram(ctx.getSource(), 1, "vee", "", Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath()))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                        .executes(ctx -> launchProgram(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "count"), "vee", "",
                                Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath()))
                        .then(Commands.argument("formation", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (ResourceLocation id : Formations.ids()) {
                                        b.suggest(id.getPath());
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> launchProgram(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count"),
                                        StringArgumentType.getString(ctx, "formation"), "",
                                        Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath()))
                                .then(Commands.argument("warhead", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (ResourceLocation id : Warheads.ids()) {
                                                b.suggest(id.getPath());
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> launchProgram(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "formation"),
                                                StringArgumentType.getString(ctx, "warhead"),
                                                Formation.DEFAULT_SPACING, CoordinationModels.DEFAULT.getPath()))
                                        .then(Commands.argument("spacing", DoubleArgumentType.doubleArg(
                                                        Formation.MIN_SPACING, Formation.MAX_SPACING))
                                                .executes(ctx -> launchProgram(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        StringArgumentType.getString(ctx, "formation"),
                                                        StringArgumentType.getString(ctx, "warhead"),
                                                        DoubleArgumentType.getDouble(ctx, "spacing"), CoordinationModels.DEFAULT.getPath()))
                                                .then(Commands.argument("coordination", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                                for (ResourceLocation id : CoordinationModels.ids()) {
                                                                    b.suggest(id.getPath());
                                                                }
                                                                return b.buildFuture();
                                                            })
                                                        .executes(ctx -> launchProgram(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                StringArgumentType.getString(ctx, "formation"),
                                                                StringArgumentType.getString(ctx, "warhead"),
                                                                DoubleArgumentType.getDouble(ctx, "spacing"),
                                                                StringArgumentType.getString(ctx, "coordination")))))))));
    }

    private static int addStep(CommandSourceStack src, DroneTask task) {
        UUID player = draftOwner(src);
        DroneProgram before = DroneDrafts.get(player);
        DroneProgram after = DroneDrafts.edit(player, p -> p.plus(task));
        if (after.tasks().size() == before.tasks().size()) {
            src.sendFailure(Component.literal("Program is full (" + DroneTask.MAX_STEPS + " steps)."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(after.tasks().size() + ". " + task.label()), false);
        return after.tasks().size();
    }

    private static int removeStep(CommandSourceStack src, int oneBased) {
        UUID player = draftOwner(src);
        DroneProgram before = DroneDrafts.get(player);
        if (oneBased > before.tasks().size()) {
            src.sendFailure(Component.literal("No step " + oneBased + "; the program has "
                    + before.tasks().size() + "."));
            return 0;
        }
        DroneProgram after = DroneDrafts.edit(player, p -> p.removing(oneBased - 1));
        src.sendSuccess(() -> Component.literal("Removed step " + oneBased + ", "
                + after.tasks().size() + " left."), false);
        return after.tasks().size();
    }

    private static int clearProgram(CommandSourceStack src) {
        DroneDrafts.clear(draftOwner(src));
        src.sendSuccess(() -> Component.literal("Program cleared."), false);
        return 1;
    }

    private static int listProgram(CommandSourceStack src) {
        DroneProgram program = DroneDrafts.get(draftOwner(src));
        if (program.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "No program. Add steps with /wflib drone program <moveto|deliver|collect|strike|"
                            + "loiter|hold|exfil>, then launch it."), false);
            return 0;
        }
        List<DroneTask> tasks = program.tasks();
        for (int i = 0; i < tasks.size(); i++) {
            int n = i + 1;
            DroneTask task = tasks.get(i);
            src.sendSuccess(() -> Component.literal(n + ". " + task.label()), false);
        }
        Vec3 origin = src.getPosition();
        int route = (int) program.routeLength(origin, origin);
        src.sendSuccess(() -> Component.literal(tasks.size() + " step(s), " + route + "m of route")
                .withStyle(ChatFormatting.GRAY), false);
        return tasks.size();
    }

    /** Fly the draft. */
    private static int launchProgram(CommandSourceStack src, int count, String formation, String warhead,
                                     double spacing, String coordination) {
        ServerLevel level = src.getLevel();
        Vec3 origin = src.getPosition();
        DroneProgram program = DroneDrafts.get(draftOwner(src));
        if (program.isEmpty()) {
            src.sendFailure(Component.literal("No program to launch. Add steps first."));
            return 0;
        }

        boolean delivers = program.tasks().stream().anyMatch(t -> t.kind() == DroneTask.Kind.DELIVER);
        boolean strikes = program.tasks().stream().anyMatch(t -> t.kind() == DroneTask.Kind.STRIKE);
        boolean lays = program.tasks().stream().anyMatch(t -> t.kind() == DroneTask.Kind.MINELAY);

        DroneMission mission = new DroneMission();
        mission.program = program;
        mission.count = count;
        mission.formationId = Formations.parse(formation);
        mission.formationSpacing = Formation.clampSpacing(spacing);
        mission.coordinationId = CoordinationModels.parse(coordination);
        if (strikes) {
            mission.payloadId = warhead.isEmpty() ? Warheads.defaultId() : Warheads.parse(warhead);
            if (!Warheads.exists(mission.payloadId)) {
                src.sendFailure(Component.literal("Unknown warhead '" + warhead + "'. Known: "
                        + Warheads.ids().stream().map(ResourceLocation::getPath).sorted().toList()));
                return 0;
            }
        }
        if (lays) {
            if (strikes) {
                src.sendFailure(Component.literal(
                        "A program cannot both strike and lay mines: one load, one drone. Split it in two."));
                return 0;
            }
            ResourceLocation mine = warhead.isEmpty()
                    ? MinePresets.defaultSownId() : MinePresets.parse(warhead);
            if (!MinePresets.exists(mine)) {
                src.sendFailure(Component.literal("Unknown mine '" + warhead + "'. Known: "
                        + MinePresets.all().stream().map(p -> p.id().getPath()).sorted().toList()));
                return 0;
            }
            mission.mines = MineLoad.of(mine, MineLoad.DEFAULT_MINES);
        }

        DroneMission.Result result = mission.dispatch(level, origin, delivers ? new CompoundTag() : null);
        if (!result.ok()) {
            src.sendFailure(Component.literal("Launch refused: " + result.error()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(String.format(
                "Launched %d drone(s) in %s formation (%s) on a %d-step program (%dm of route)",
                result.ordered(), mission.formationId.getPath(),
                mission.coordinationId.getPath(), program.tasks().size(),
                (int) mission.outboundDistance(origin))), true);
        return result.ordered();
    }

    /**
     * @return whose draft this command edits. Keyed on the player so two people writing programs at once do
     *      not write each other's; anything without a player behind it (a command block, the console) shares one
     *      draft, which is the only sensible reading of "the server's program".
     */
    private static UUID draftOwner(CommandSourceStack src) {
        return src.getEntity() != null ? src.getEntity().getUUID() : SERVER_DRAFT;
    }

    private static final UUID SERVER_DRAFT = new UUID(0L, 0L);

    /** Dispatches a delivery flight from the player's position. */
    private static int dispatchDrones(CommandSourceStack src, Vec3 destination, int count, String formation,
                                      double spacing, String coordination, int interval,
                                      boolean withCrate) {
        ServerLevel level = src.getLevel();
        Vec3 origin = src.getPosition();

        DroneMission mission = new DroneMission();
        mission.destination = destination;
        mission.count = count;
        mission.formationId = Formations.parse(formation);
        mission.formationSpacing = Formation.clampSpacing(spacing);
        mission.coordinationId = CoordinationModels.parse(coordination);
        mission.launchInterval = DroneMission.clampInterval(interval);

        CompoundTag crate = withCrate ? new CompoundTag() : null;

        DroneMission.Result result = mission.dispatch(level, origin, crate);
        if (!result.ok()) {
            src.sendFailure(Component.literal("Dispatch refused: " + result.error()));
            return 0;
        }

        boolean roundTrip = mission.canRoundTrip(origin, withCrate);
        int dist = (int) origin.distanceTo(destination);
        src.sendSuccess(() -> Component.literal(String.format(
                "Dispatched %d drone(s) in %s formation (%s) to %d, %d, %d (%dm)%s",
                result.ordered(), mission.formationId.getPath(), mission.coordinationId.getPath(),
                (int) destination.x, (int) destination.y, (int) destination.z, dist,
                roundTrip ? "" : " - one-way, battery won't cover the return")), true);
        return result.ordered();
    }

    private static final String[] SCENARIOS =
            {"delivery", "strike", "squad", "lowbattery", "downed", "longrange", "terrain"};

    /** Run the pure-logic checks and report them. */
    private static int selfTest(CommandSourceStack src) {
        List<DroneSelfTest.Result> results = DroneSelfTest.runAll();
        long failed = results.stream().filter(r -> !r.passed()).count();
        for (DroneSelfTest.Result r : results) {
            if (!r.passed()) {
                src.sendSuccess(() -> Component.literal("FAIL " + r.name() + " - " + r.detail())
                        .withStyle(ChatFormatting.RED), false);
            }
        }
        long passed = results.size() - failed;
        src.sendSuccess(() -> Component.literal(String.format("Drone self-test: %d passed, %d failed",
                passed, failed)).withStyle(failed == 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return failed == 0 ? 1 : 0;
    }

    /**
     * Set up a canned situation to watch. Everything spawns near the caller.
     */
    private static int scenario(CommandSourceStack src, String name) {
        ServerLevel level = src.getLevel();
        Vec3 origin = src.getPosition();
        DroneMission mission = new DroneMission();

        switch (name.toLowerCase()) {
            case "delivery" -> mission.destination = origin.add(80.0, 0.0, 80.0);
            case "strike" -> {
                mission.destination = origin.add(80.0, 0.0, 80.0);
                mission.payloadId = Warheads.defaultId();
            }
            case "squad" -> {
                mission.destination = origin.add(120.0, 0.0, 120.0);
                mission.count = 4;
            }
            case "lowbattery" -> {
                // Just enough to get there, nothing like enough to come back: watch it strand and park.
                mission.destination = origin.add(150.0, 0.0, 150.0);
                mission.batteryCapacity = 500.0;
            }
            case "longrange" -> {
                // Far enough that it offloads to the sim and onloads again near the target.
                mission.destination = origin.add(900.0, 0.0, 900.0);
                mission.batteryCapacity = 8000.0;
            }
            case "terrain" -> {
                mission.destination = origin.add(400.0, 0.0, 400.0);
                mission.cruiseAltitude = 10.0;
                mission.batteryCapacity = 6000.0;
            }
            case "downed" -> {
                mission.destination = origin.add(200.0, 0.0, 200.0);
                // Named and shot down by hand a moment later, so it has to exist now rather than be queued.
                mission.launchInterval = 0;
                DroneMission.Result spawned = mission.dispatch(level, origin, null);
                if (!spawned.ok()) {
                    src.sendFailure(Component.literal("Scenario refused: " + spawned.error()));
                    return 0;
                }
                // Let it get airborne first, then knock it down, so the spin-in is actually visible.
                spawned.drones().forEach(d -> d.flight().setState(DroneState.TRANSIT));
                src.sendSuccess(() -> Component.literal(
                        "Launched 1 drone; it will be shot down in a few seconds"), false);
                DOWNED_SCENARIO.add(spawned.drones().get(0).droneId());
                return 1;
            }
            default -> {
                src.sendFailure(Component.literal("Unknown scenario. Try: " + String.join(", ", SCENARIOS)));
                return 0;
            }
        }

        CompoundTag crate = mission.isStrike() ? null : new CompoundTag();
        DroneMission.Result result = mission.dispatch(level, origin, crate);
        if (!result.ok()) {
            src.sendFailure(Component.literal("Scenario refused: " + result.error()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(String.format("Scenario '%s': %d drone(s) away",
                name, result.ordered())), false);
        return result.ordered();
    }

    /**
     * Drones queued to be shot down by the {@code downed} scenario once they are properly airborne.
     */
    private static final List<UUID> DOWNED_SCENARIO = new ArrayList<>();
    private static int downedScenarioDelay;

    /**
     * Drives the {@code downed} scenario's delayed kill.
     */
    private static void tickScenarios(ServerLevel level) {
        if (DOWNED_SCENARIO.isEmpty()) {
            return;
        }
        if (++downedScenarioDelay < 100) {
            return;
        }
        downedScenarioDelay = 0;
        for (UUID id : new ArrayList<>(DOWNED_SCENARIO)) {
            if (level.getEntity(id) instanceof DroneEntity drone) {
                drone.damage().shootDown();
                DOWNED_SCENARIO.remove(id);
            }
        }
    }

    private static int clearDrones(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        int removed = 0;
        for (DroneEntity drone : new ArrayList<>(DroneTracker.drones(level))) {
            drone.discard();
            removed++;
        }
        for (CrateEntity crate : level.getEntitiesOfClass(CrateEntity.class,
                new AABB(BlockPos.containing(src.getPosition())).inflate(4096.0))) {
            crate.discard();
            removed++;
        }
        SimTier<SimDrone> registry = SimDroneManager.tier(level);
        for (SimDrone sd : new ArrayList<>(registry.view())) {
            registry.remove(sd);
            removed++;
        }
        int total = removed;
        src.sendSuccess(() -> Component.literal("Removed " + total + " drone(s)/crate(s)"), true);
        return total;
    }

    private static int recallDrones(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        Vec3 home = src.getPosition();
        int count = 0;
        for (ControllableDrone drone : DroneTracker.bodies(level)) {
            if (drone.recall(home)) {
                count++;
            }
        }
        for (SimDrone sd : new ArrayList<>(SimDroneManager.tier(level).view())) {
            sd.exfil = home;
            sd.destination = null;
            sd.state = DroneState.EXFIL;
            count++;
        }
        int total = count;
        src.sendSuccess(() -> Component.literal("Recalled " + total + " drone(s)"), true);
        return total;
    }

    /**
     * Dump the nearest drone's telemetry timeline, the drone equivalent of {@code track}.
     */
    private static int droneTelemetry(CommandSourceStack src) {
        Vec3 from = src.getPosition();
        DroneEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (DroneEntity drone : DroneTracker.drones(src.getLevel())) {
            double d = drone.position().distanceToSqr(from);
            if (d < best) {
                best = d;
                nearest = drone;
            }
        }
        if (nearest == null) {
            src.sendFailure(Component.literal("No drones in this dimension."));
            return 0;
        }
        DroneEntity target = nearest;
        WFTelemetry telemetry = WFTelemetryService.get(target.getUUID());
        if (telemetry == null) {
            // Open one now so the rest of this flight is recorded, and say so rather than silently failing.
            target.openTelemetry();
            src.sendSuccess(() -> Component.literal(
                            "No timeline yet for " + target.getUUID() + " - now recording, run this again shortly.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(String.format("%s - %s, %.0f%% battery, %d events",
                target.getUUID(), target.flight().getDroneState(), target.battery().percent(), telemetry.total()))
                .withStyle(ChatFormatting.GOLD), false);
        List<?> events = telemetry.events();
        for (int i = Math.max(0, events.size() - 20); i < events.size(); i++) {
            String line = events.get(i).toString();
            src.sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    /** Dispatch an armed flight. */
    private static int strikeDrones(CommandSourceStack src, Vec3 destination, int count, String warhead) {
        ServerLevel level = src.getLevel();
        Vec3 origin = src.getPosition();

        DroneMission mission = new DroneMission();
        mission.destination = destination;
        mission.count = count;
        mission.payloadId = warhead.isEmpty() ? Warheads.defaultId() : Warheads.parse(warhead);
        if (!Warheads.exists(mission.payloadId)) {
            src.sendFailure(Component.literal("Unknown warhead '" + warhead + "'. Known: "
                    + Warheads.ids().stream().map(ResourceLocation::getPath).sorted().toList()));
            return 0;
        }

        DroneMission.Result result = mission.dispatch(level, origin, null);
        if (!result.ok()) {
            src.sendFailure(Component.literal("Strike refused: " + result.error()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(String.format(
                "Launched %d strike drone(s) with %s at %d, %d, %d (%dm)",
                result.ordered(), mission.payloadId.getPath(),
                (int) destination.x, (int) destination.y, (int) destination.z,
                (int) origin.distanceTo(destination))), true);
        return result.ordered();
    }

    /** Suggests the mines built to be dispensed, first and by themselves. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SOWN_MINES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    MinePresets.sown().stream().map(preset -> preset.id().getPath()), builder);

    /** Dispatch a mine-laying flight. */
    private static int minelayDrones(CommandSourceStack src, Vec3 destination, int count, String mine,
                                     int mines, double spacing) {
        ServerLevel level = src.getLevel();
        Vec3 origin = src.getPosition();

        ResourceLocation presetId = mine.isEmpty()
                ? MinePresets.defaultSownId() : MinePresets.parse(mine);
        MinePreset preset = MinePresets.get(presetId);
        if (preset == null) {
            src.sendFailure(Component.literal("Unknown mine '" + mine + "'. Known: "
                    + MinePresets.all().stream().map(p -> p.id().getPath()).sorted().toList()));
            return 0;
        }

        DroneMission mission = new DroneMission();
        mission.destination = destination;
        mission.count = count;
        mission.mines = MineLoad.of(presetId, mines, spacing);
        mission.program = DroneProgram.of(List.of(new DroneTask.Minelay(destination), new DroneTask.Exfil()));

        DroneMission.Result result = mission.dispatch(level, origin, null);
        if (!result.ok()) {
            src.sendFailure(Component.literal("Lay refused: " + result.error()));
            return 0;
        }

        if (!preset.sownOnly()) {
            src.sendSuccess(() -> Component.literal(presetId.getPath()
                            + " is not built to be sown: it has no self-destruct and may not land the right way up")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        MineLoad rack = mission.mines;
        src.sendSuccess(() -> Component.literal(String.format(
                "Launched %d minelayer(s): %d x %s each, %.0fm strip at %d, %d, %d (%dm out)",
                result.ordered(), rack.capacity(), presetId.getPath(), rack.laneLength(),
                (int) destination.x, (int) destination.y, (int) destination.z,
                (int) origin.distanceTo(destination))), true);
        return result.ordered();
    }

    /** Point a drone pad at a destination. */
    private static int configurePad(CommandSourceStack src, BlockPos pos, Vec3 destination, int count,
                                    String formation, double spacing, String coordination) {
        if (!(src.getLevel().getBlockEntity(pos) instanceof DronePadBlockEntity pad)) {
            src.sendFailure(Component.literal("No drone pad at " + pos.toShortString()));
            return 0;
        }
        DroneMission mission = pad.mission();
        mission.destination = destination;
        mission.count = count;
        mission.formationId = Formations.parse(formation);
        mission.formationSpacing = Formation.clampSpacing(spacing);
        mission.coordinationId = CoordinationModels.parse(coordination);
        mission.exfil = Vec3.atCenterOf(pos.above());
        pad.setMission(mission);
        src.sendSuccess(() -> Component.literal(String.format(
                "Pad at %s set: %d drone(s), %s formation at %.0f spacing, to %d %d %d", pos.toShortString(),
                count, mission.formationId.getPath(), mission.formationSpacing,
                (int) destination.x, (int) destination.y, (int) destination.z)), true);
        return 1;
    }

    /**
     * @return a short description of what a drone is carrying, for the listing.
     */
    private static String load(boolean hasCrate, @Nullable ResourceLocation payloadId) {
        return load(hasCrate, payloadId, null);
    }

    private static String load(boolean hasCrate, @Nullable ResourceLocation payloadId,
                               @Nullable com.wf.wflib.drone.api.MineLoad mines) {
        if (payloadId != null) {
            return "armed:" + payloadId.getPath();
        }
        if (mines != null && !mines.empty()) {
            return "mines:" + mines.label();
        }
        return hasCrate ? "carrying" : "empty";
    }

    /** Turn off-world simulation on or off. */
    private static int droneSim(CommandSourceStack src, Boolean enabled) {
        if (enabled != null) {
            SimDroneManager.setOffloadEnabled(enabled);
        }
        boolean on = SimDroneManager.isOffloadEnabled();
        src.sendSuccess(() -> Component.literal("Drone off-world simulation is " + (on ? "on" : "off")
                + (on ? "" : " - drones stay real for their whole mission")), true);
        return on ? 1 : 0;
    }

    /** Report where the planning actually runs. */
    /** Why every squad in this dimension is or is not ready to leave form-up. */
    private static int droneMuster(CommandSourceStack src) {
        List<SquadView> squads = DroneAiScheduler.squadsFor(src.getLevel());
        if (squads.isEmpty()) {
            src.sendFailure(Component.literal("No drones in this dimension."));
            return 0;
        }
        int held = 0;
        for (SquadView squad : squads) {
            if (squad.size() <= 1) {
                continue;
            }
            int ordered = squad.leader().squadSize();
            List<String> climbing = new ArrayList<>();
            for (DroneSnapshot member : squad.slots()) {
                if (member.state() == DroneState.IDLE || member.state() == DroneState.TAKEOFF) {
                    climbing.add(member.state().name().toLowerCase(Locale.ROOT));
                }
            }
            SquadAnchor anchor = squad.anchor();
            boolean formed = squad.coordination().formedUp(squad, anchor);
            double worst = anchor == null ? Double.NaN : Slots.worstError(squad, anchor);
            double tolerance = Math.max(Tuning.MUSTER_IN_PLACE_FLOOR,
                    squad.spacing() * Tuning.MUSTER_IN_PLACE);
            boolean ready = squad.size() >= ordered && climbing.isEmpty() && formed;
            if (!ready) {
                held++;
            }

            src.sendSuccess(() -> Component.literal(String.format(
                    "squad %08x: %d/%d up, %s in %s at %.0f spacing - %s",
                    squad.squadId(), squad.size(), ordered,
                    squad.coordination().id(),
                    squad.formationId() == null ? "vee" : squad.formationId().getPath(),
                    squad.spacing(), ready ? "READY" : "holding"))
                    .withStyle(ready ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
            src.sendSuccess(() -> Component.literal(String.format(
                    "  all up:      %s (%d of %d turned up)",
                    squad.size() >= ordered ? "yes" : "NO", squad.size(), ordered))
                    .withStyle(squad.size() >= ordered ? ChatFormatting.GRAY : ChatFormatting.RED), false);
            src.sendSuccess(() -> Component.literal(String.format(
                    "  climbed out: %s%s", climbing.isEmpty() ? "yes" : "NO - " + climbing,
                    climbing.isEmpty() ? "" : " still on the way up"))
                    .withStyle(climbing.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.RED), false);
            src.sendSuccess(() -> Component.literal(String.format(
                    "  in formation: %s (worst slot error %.1f against a tolerance of %.1f%s)",
                    formed ? "yes" : "NO", worst, tolerance,
                    anchor == null ? ", no frame yet" : ""))
                    .withStyle(formed ? ChatFormatting.GRAY : ChatFormatting.RED), false);
            if (!formed && anchor != null) {
                Formation shape = Formations.get(squad.formationId());
                Vec3 forward = Formation.forward(anchor.yaw());
                for (DroneSnapshot member : squad.slots()) {
                    Vec3 slot = shape.slot(squad.indexOf(member), anchor.pos(), forward, squad.spacing());
                    double error = member.pos().distanceTo(slot);
                    Vec3 gap = member.pos().subtract(slot);
                    src.sendSuccess(() -> Component.literal(String.format(
                            "    slot %-2d %-8s off by %5.1f (flat %5.1f, vert %+6.1f)",
                            squad.indexOf(member), member.state().name().toLowerCase(Locale.ROOT),
                            error, gap.horizontalDistance(), gap.y))
                            .withStyle(error > tolerance ? ChatFormatting.RED : ChatFormatting.DARK_GRAY),
                            false);
                }
            }
        }
        return held == 0 ? 1 : 0;
    }

    private static int droneThreads(CommandSourceStack src) {
        boolean armed = WorldThread.armed();
        boolean onWorldThread = WorldThread.isWorldThread();
        String planner = PlannerStats.lastThread();
        // This command runs on the world thread, so a search that ran anywhere else proves the split.
        boolean offThread = !planner.equals(Thread.currentThread().getName());

        src.sendSuccess(() -> Component.literal(String.format(
                "Planner pool: %d worker(s)   assertions %s",
                WorkerPool.poolSize(), armed ? "armed" : "NOT ARMED"))
                .withStyle(armed ? ChatFormatting.GOLD : ChatFormatting.RED), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "This command is on the world thread: %s (%s)",
                onWorldThread, Thread.currentThread().getName())), false);

        if (PlannerStats.count() == 0) {
            src.sendSuccess(() -> Component.literal(
                            "No terrain searches yet - fly a drone, then run this again.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(String.format(
                "%d terrain search(es), last on '%s' in %.1fms (worst %.1fms)",
                PlannerStats.count(), planner,
                PlannerStats.lastMicros() / 1000.0, PlannerStats.worstMicros() / 1000.0))
                .withStyle(offThread ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        src.sendSuccess(() -> Component.literal(offThread
                ? "A* is running OFF the world thread, as required."
                : "A* RAN ON THE WORLD THREAD - this is a bug.")
                .withStyle(offThread ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return offThread ? 1 : 0;
    }

    private static int listDrones(CommandSourceStack src) {
        Set<DroneEntity> drones = DroneTracker.drones(src.getLevel());
        List<SimDrone> simulated = SimDroneManager.tier(src.getLevel()).view();
        int queued = DroneLaunchQueue.get(src.getLevel()).pending();
        if (drones.isEmpty() && simulated.isEmpty() && queued == 0) {
            src.sendSuccess(() -> Component.literal("No drones in this dimension."), false);
            return 0;
        }
        if (queued > 0) {
            src.sendSuccess(() -> Component.literal(queued + " more still to launch")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        Vec3 from = src.getPosition();
        for (SimDrone sd : new ArrayList<>(simulated)) {
            Vec3 flat = sd.pos.multiply(1, 0, 1);
            String dest = sd.destination == null ? "done"
                    : (int) flat.distanceTo(sd.destination.multiply(1, 0, 1)) + "m";
            String line = String.format("• %-11s y=%-4.0f dst %-6s exf %-5dm  %3.0f%% battery  %-16s  [SIM]",
                    sd.state, sd.pos.y, dest, (int) flat.distanceTo(sd.exfil.multiply(1, 0, 1)),
                    100.0 * sd.charge / sd.capacity, load(sd.cargo != null, sd.payloadId, sd.mines));
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        if (drones.isEmpty()) {
            return simulated.size();
        }
        List<DroneEntity> sorted = new ArrayList<>(drones);
        sorted.sort(Comparator.comparingDouble(d -> d.position().distanceToSqr(from)));
        src.sendSuccess(() -> Component.literal(sorted.size() + " drone(s):").withStyle(ChatFormatting.GOLD), false);
        for (DroneEntity drone : sorted.subList(0, Math.min(sorted.size(), 30))) {
            Vec3 flat = drone.position().multiply(1, 0, 1);
            String shownDest = drone.route().getDestination() == null ? "done"
                    : (int) flat.distanceTo(drone.route().getDestination().multiply(1, 0, 1)) + "m";
            DronePath path = drone.route().getPath();
            String dest = drone.orders().isClassified() ? "[CLSFD]" : shownDest;
            String line = String.format(
                    "• %-11s y=%-4.0f dst %-6s exf %-5dm  %3.0f%% bat  %-8s spd%.2f thr%.2f tilt%2.0f° agl%-4.0f %s%s",
                    drone.flight().getDroneState(), drone.getY(), dest,
                    (int) flat.distanceTo(drone.route().getExfil().multiply(1, 0, 1)),
                    drone.battery().percent(), load(drone.hold().hasCargo(), drone.hold().getPayloadId(), drone.hold().getMines()),
                    drone.getDeltaMovement().horizontalDistance(),
                    drone.flight().getAttitude().throttle(), Math.toDegrees(drone.flight().getAttitude().tilt()),
                    drone.getY() - TerrainSampler.groundY(src.getLevel(), drone.getX(), drone.getZ(), drone.getY()),
                    path == null ? "no route" : path.waypoints().size() + "wp" + (path.partial() ? "+" : ""),
                    drone.leader() ? "  [lead]" : "");
            src.sendSuccess(() -> Component.literal(line).withStyle(
                    drone.flight().getDroneState() == DroneState.DEPLETED ? ChatFormatting.RED : ChatFormatting.YELLOW), false);
        }
        return sorted.size();
    }
}
