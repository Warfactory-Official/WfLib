package com.wf.wflib.build;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wf.wflib.drone.api.DroneMission;
import com.wf.wflib.drone.api.DroneProgram;
import com.wf.wflib.drone.api.DronePadBlockEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wf.wflib.drone.api.ExchangeMode;
import com.wf.wflib.drone.api.StationCode;
import com.wf.wflib.build.api.Blueprint;
import com.wf.wflib.build.api.BlueprintFormat;
import com.wf.wflib.build.api.BuildJobs;
import com.wf.wflib.compat.api.TerritoryVerdict;
import com.wf.wflib.build.api.WorkPlan;
import com.wf.wflib.work.api.WorkJob;
import com.wf.wflib.work.api.WorkData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code /wflib blueprint|build|salvage|job}. */
public final class BuildCommands {
    private BuildCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(blueprintCommand())
                .then(buildCommand())
                .then(salvageCommand())
                .then(jobCommand()));
    }

    // --- stations, blueprints and work ---

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> BLUEPRINTS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    BlueprintLibrary.names(ctx.getSource().getServer()), builder);
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> JOBS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    WorkData.get(ctx.getSource().getServer()).all().stream()
                            .map(job -> job.id().toString().substring(0, 8)).toList(), builder);

    private static LiteralArgumentBuilder<CommandSourceStack> blueprintCommand() {
        return Commands.literal("blueprint")
                .executes(ctx -> blueprintList(ctx.getSource()))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            BlueprintLibrary.invalidate();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Blueprint cache cleared."), false);
                            return 1;
                        }))
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(BLUEPRINTS)
                        .executes(ctx -> blueprintInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("build")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(BLUEPRINTS)
                        .executes(ctx -> build(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                BlockPos.containing(ctx.getSource().getPosition())))
                        .then(Commands.argument("at", BlockPosArgument.blockPos())
                                .executes(ctx -> build(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "at")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> salvageCommand() {
        return Commands.literal("salvage")
                .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(ctx -> salvage(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "to")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> jobCommand() {
        return Commands.literal("job")
                .executes(ctx -> jobList(ctx.getSource()))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(JOBS)
                                .executes(ctx -> jobCancel(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("work")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(JOBS)
                                .executes(ctx -> jobWork(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"), 1))
                                .then(Commands.argument("drones", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> jobWork(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "drones"))))))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(JOBS)
                        .executes(ctx -> jobInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"))));
    }









    /**
     * @return the faction of whoever ran this command, or null for the console, which has none, and whose
     *      jobs are therefore judged as an outsider's on every claim.
     */
    @Nullable
    private static UUID factionOf(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        return player == null ? null : com.wf.wflib.recon.api.ReconOwners.owningEntity(player);
    }

    /**
     * Refuse a job whose ground is not ours to touch, saying where and why.
     *
     * @return true if the job may go ahead
     */
    private static boolean surveyed(CommandSourceStack src, BoundingBox box, String what) {
        BlockPos.MutableBlockPos where = new BlockPos.MutableBlockPos();
        TerritoryVerdict verdict = SiteRules.survey(src.getLevel(), factionOf(src), box, where);
        if (verdict.allowed()) {
            return true;
        }
        src.sendFailure(Component.literal(String.format("Cannot %s there: %s (at %d, %d).",
                what, verdict.reason(), where.getX(), where.getZ())));
        return false;
    }

    private static int blueprintList(CommandSourceStack src) {
        List<String> names = BlueprintLibrary.names(src.getServer());
        if (names.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No blueprints. Put " + String.join("/",
                            com.wf.wflib.build.api.BlueprintFormats.extensions())
                    + " files in " + BlueprintLibrary.FOLDER + " inside the world folder.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(names.size() + " blueprint(s):")
                .withStyle(ChatFormatting.GOLD), false);
        for (String name : names) {
            src.sendSuccess(() -> Component.literal("• " + name), false);
        }
        return names.size();
    }

    private static int blueprintInfo(CommandSourceStack src, String name) {
        Blueprint blueprint;
        try {
            blueprint = BlueprintLibrary.load(src.getServer(), name);
        } catch (BlueprintFormat.BlueprintException e) {
            src.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        WorkPlan plan = ConstructionPlan.of(blueprint, BlockPos.ZERO);
        src.sendSuccess(() -> Component.literal(String.format("%s  %dx%dx%d  %d blocks, %d states",
                        blueprint.name(), blueprint.size().getX(), blueprint.size().getY(),
                        blueprint.size().getZ(), blueprint.blocks(), blueprint.palette().size() - 1))
                .withStyle(ChatFormatting.GOLD), false);
        reportPlan(src, plan);
        if (blueprint.blockEntities() > 0) {
            src.sendSuccess(() -> Component.literal("  " + blueprint.blockEntities()
                            + " block entities; their contents are not reproduced")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (blueprint.unresolved() > 0) {
            src.sendSuccess(() -> Component.literal("  " + blueprint.unresolved()
                            + " palette entries name blocks this server does not have; they load as air")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int build(CommandSourceStack src, String name, BlockPos origin) {
        Blueprint blueprint;
        try {
            blueprint = BlueprintLibrary.load(src.getServer(), name);
        } catch (BlueprintFormat.BlueprintException e) {
            src.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        WorkPlan plan = ConstructionPlan.of(blueprint, origin);
        if (plan.orders().isEmpty()) {
            src.sendFailure(Component.literal("'" + blueprint.name() + "' has nothing to build."));
            return 0;
        }
        if (!surveyed(src, plan.bounds(), "build")) {
            return 0;
        }
        WorkJob job = BuildJobs.construct(src.getLevel(), plan, name, null, factionOf(src));
        WorkData.get(src.getLevel()).add(job);
        src.sendSuccess(() -> Component.literal(String.format("Queued %s: %d blocks at %d, %d, %d  [%s]",
                        blueprint.name(), plan.size(), origin.getX(), origin.getY(), origin.getZ(),
                        job.id().toString().substring(0, 8)))
                .withStyle(ChatFormatting.GREEN), true);
        reportPlan(src, plan);
        return plan.size();
    }

    private static int salvage(CommandSourceStack src, BlockPos from, BlockPos to) {
        BoundingBox box = BoundingBox.fromCorners(from, to);
        WorkPlan plan;
        try {
            plan = SalvagePlan.of(src.getLevel(), box);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        if (plan.orders().isEmpty()) {
            src.sendFailure(Component.literal("Nothing to take down there."));
            return 0;
        }
        if (!surveyed(src, box, "salvage")) {
            return 0;
        }
        WorkJob job = BuildJobs.salvage(src.getLevel(), plan, null, factionOf(src));
        WorkData.get(src.getLevel()).add(job);
        src.sendSuccess(() -> Component.literal(String.format("Queued salvage: %d blocks over %dx%dx%d  [%s]",
                        plan.size(), box.getXSpan(), box.getYSpan(), box.getZSpan(),
                        job.id().toString().substring(0, 8)))
                .withStyle(ChatFormatting.GREEN), true);
        reportPlan(src, plan);
        return plan.size();
    }

    /**
     * The bill and the caveats, shared by everything that makes or inspects a plan.
     */
    private static void reportPlan(CommandSourceStack src, WorkPlan plan) {
        List<String> lines = plan.billLines();
        int shown = Math.min(6, lines.size());
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            src.sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false);
        }
        if (lines.size() > shown) {
            int rest = lines.size() - shown;
            src.sendSuccess(() -> Component.literal("  ...and " + rest + " more kinds, "
                    + plan.totalItems() + " items in total").withStyle(ChatFormatting.GRAY), false);
        }
        if (plan.unbuildable() > 0) {
            src.sendSuccess(() -> Component.literal("  " + plan.unbuildable()
                            + " blocks have no item that places them (fluids, potted plants) "
                            + "and are left out")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static int jobList(CommandSourceStack src) {
        List<WorkJob> jobs = WorkData.get(src.getServer()).all();
        if (jobs.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No work queued."), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(jobs.size() + " job(s):")
                .withStyle(ChatFormatting.GOLD), false);
        for (WorkJob job : jobs) {
            BlockPos at = job.centre();
            String line = String.format("• %s  %s  %s  %d, %d, %d  %s",
                    job.id().toString().substring(0, 8), job.kind().getPath(), job.label(),
                    at.getX(), at.getY(), at.getZ(), job.progress());
            src.sendSuccess(() -> Component.literal(line).withStyle(
                    job.over() ? ChatFormatting.DARK_GRAY : ChatFormatting.YELLOW), false);
        }
        return jobs.size();
    }

    private static int jobInfo(CommandSourceStack src, String prefix) {
        WorkJob job = findJob(src, prefix);
        if (job == null) {
            return 0;
        }
        BoundingBox box = job.bounds();
        src.sendSuccess(() -> Component.literal(job.label() + "  [" + job.id() + "]")
                .withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal(String.format("  %s in %s, %dx%dx%d from %d, %d, %d",
                job.kind().getPath(), job.dimension().location().getPath(),
                box.getXSpan(), box.getYSpan(), box.getZSpan(),
                box.minX(), box.minY(), box.minZ())), false);
        src.sendSuccess(() -> Component.literal("  " + job.progress()
                + ", frontier at sequence " + job.queue().frontier()), false);
        String station = BuildJobs.stationOf(job);
        if (station != null) {
            src.sendSuccess(() -> Component.literal("  station " + StationCode.pretty(station)), false);
        }
        return 1;
    }

    /** Put drones from the nearest pad onto a job. */
    private static int jobWork(CommandSourceStack src, String prefix, int drones) {
        WorkJob job = findJob(src, prefix);
        if (job == null) {
            return 0;
        }
        if (!job.dimension().equals(src.getLevel().dimension())) {
            src.sendFailure(Component.literal("That job is in another dimension."));
            return 0;
        }
        if (job.over()) {
            src.sendFailure(Component.literal("That job is " + job.progress() + "."));
            return 0;
        }
        DronePadBlockEntity pad = DronePadBlockEntity.nearest(src.getLevel(), src.getPosition());
        if (pad == null) {
            src.sendFailure(Component.literal("Stand near a drone pad."));
            return 0;
        }
        DroneMission mission = pad.mission();
        mission.count = drones;
        mission.destination = Vec3.atCenterOf(job.centre());
        mission.payloadId = null;
        mission.program = DroneProgram.EMPTY;
        mission.mode = ExchangeMode.DIRECT;
        mission.recipientCode = null;
        mission.jobId = job.id();
        DroneMission.Result result = mission.dispatch(src.getLevel(),
                Vec3.atCenterOf(pad.getBlockPos()).add(0.0, 1.0, 0.0), null);
        if (!result.ok()) {
            src.sendFailure(Component.literal("Refused: " + result.error()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(String.format("%d drone(s) on %s.",
                result.ordered(), job.label())).withStyle(ChatFormatting.GREEN), true);
        return result.ordered();
    }

    private static int jobCancel(CommandSourceStack src, String prefix) {
        WorkJob job = findJob(src, prefix);
        if (job == null) {
            return 0;
        }
        if (!WorkData.get(src.getServer()).cancel(job.id())) {
            src.sendFailure(Component.literal("Already cancelled."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Cancelled " + job.label() + "."), true);
        return 1;
    }

    /**
     * @return the one job whose id starts with {@code prefix}, or null having said why not. Prefixes rather
     *      than full UUIDs because the list prints prefixes and nobody is retyping thirty-six characters
     */
    @Nullable
    private static WorkJob findJob(CommandSourceStack src, String prefix) {
        List<WorkJob> matches = WorkData.get(src.getServer()).all().stream()
                .filter(job -> job.id().toString().startsWith(prefix.toLowerCase(java.util.Locale.ROOT)))
                .toList();
        if (matches.isEmpty()) {
            src.sendFailure(Component.literal("No job starting '" + prefix + "'."));
            return null;
        }
        if (matches.size() > 1) {
            src.sendFailure(Component.literal(matches.size() + " jobs start '" + prefix + "'."));
            return null;
        }
        return matches.get(0);
    }
}
