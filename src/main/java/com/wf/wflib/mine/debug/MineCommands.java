package com.wf.wflib.mine.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.mine.api.MinePreset;
import com.wf.wflib.mine.api.MinePresets;
import com.wf.wflib.mine.api.MineEntity;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /wflib debug mines|scatter|mine}. */
public final class MineCommands {
    private MineCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(mineDebugCommand()));
    }


    /** {@code /wflib debug}: the two overlays plus a text dump. */
    private static LiteralArgumentBuilder<CommandSourceStack> mineDebugCommand() {
        return Commands.literal("debug")
                .then(Commands.literal("mines")
                        .executes(ctx -> setMineDebug(ctx.getSource(), !MineDebug.renderAreas()))
                        .then(Commands.literal("on").executes(ctx -> setMineDebug(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setMineDebug(ctx.getSource(), false))))
                .then(Commands.literal("scatter")
                        .executes(ctx -> scatterMines(ctx.getSource(), 8,
                                MinePresets.defaultId().getPath()))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> scatterMines(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count"),
                                        MinePresets.defaultId().getPath()))
                                .then(Commands.argument("preset", StringArgumentType.string())
                                        .suggests(MINE_PRESETS)
                                        .executes(ctx -> scatterMines(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "preset"))))))
                .then(Commands.literal("mine")
                        .executes(ctx -> mineInfo(ctx.getSource()))
                        .then(Commands.literal("lay")
                                .then(Commands.argument("preset", StringArgumentType.string())
                                        .suggests(MINE_PRESETS)
                                        .then(Commands.argument("at", Vec3Argument.vec3())
                                                .executes(ctx -> layMine(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "preset"),
                                                        Vec3Argument.getVec3(ctx, "at"), 0.0f,
                                                        Laying.AS_BUILT))
                                                .then(Commands.argument("yaw",
                                                                FloatArgumentType.floatArg(-360.0f, 360.0f))
                                                        .executes(ctx -> layMine(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "preset"),
                                                                Vec3Argument.getVec3(ctx, "at"),
                                                                FloatArgumentType.getFloat(ctx, "yaw"),
                                                                Laying.AS_BUILT))
                                                        .then(Commands.literal("armed")
                                                                .executes(ctx -> layMine(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "preset"),
                                                                        Vec3Argument.getVec3(ctx, "at"),
                                                                        FloatArgumentType.getFloat(ctx, "yaw"),
                                                                        Laying.ARMED)))
                                                        .then(Commands.literal("safe")
                                                                .executes(ctx -> layMine(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "preset"),
                                                                        Vec3Argument.getVec3(ctx, "at"),
                                                                        FloatArgumentType.getFloat(ctx, "yaw"),
                                                                        Laying.SAFE))))))));
    }

    /** How {@code /wflib debug mine lay} leaves the mine it laid. */
    private enum Laying {
        /** Whatever the preset says: counting down, or safe if it needs activating. */
        AS_BUILT,
        /** Past the arming delay, the way a rack that has been sitting there a minute already is. */
        ARMED,
        /** Inert, which is what laying one while crouching does; see {@code MineItem#useOn}. */
        SAFE
    }

    /** Lays one mine from its preset, facing a given yaw, optionally already armed. */
    private static int layMine(CommandSourceStack source, String presetName, Vec3 at, float yaw,
                               Laying how) {
        ServerLevel level = source.getLevel();
        MinePreset preset = MinePresets.get(MinePresets.parse(presetName));
        if (preset == null) {
            source.sendFailure(Component.literal("No mine preset '" + presetName + "'."));
            return 0;
        }
        MineEntity mine = preset.build(level, yaw);
        mine.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        level.addFreshEntity(mine);
        switch (how) {
            case ARMED -> {
                mine.activate();
                mine.forceArmed();
            }
            case SAFE -> mine.layInert();
            case AS_BUILT -> {
            }
        }
        source.sendSuccess(() -> Component.literal(String.format("Laid %s at %.1f %.1f %.1f facing %.0f (%s)",
                preset.id().getPath(), at.x, at.y, at.z, yaw, mine.getState())), false);
        return 1;
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> MINE_PRESETS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    MinePresets.all().stream().map(preset -> preset.id().getPath()), builder);

    /**
     * Throws a handful of mines up and out from wherever the command was run, the way a deployer or a dispersing
     * payload will: they turn over on the way down and lie at whatever angle they land at.
     */
    private static int scatterMines(CommandSourceStack source, int count, String presetName) {
        ServerLevel level = source.getLevel();
        MinePreset preset = MinePresets.get(MinePresets.parse(presetName));
        if (preset == null) {
            source.sendFailure(Component.literal("No mine preset '" + presetName + "'."));
            return 0;
        }
        Vec3 from = source.getPosition()
                .add(0.0, 1.0, 0.0);
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            MineEntity mine = preset.build(level, random.nextFloat() * 360.0f);
            mine.moveTo(from.x, from.y, from.z, mine.getYRot(), 0.0f);
            mine.scatter(new Vec3((random.nextDouble() - 0.5) * 0.7,
                    0.32 + random.nextDouble() * 0.28,
                    (random.nextDouble() - 0.5) * 0.7), random);
            level.addFreshEntity(mine);
        }
        source.sendSuccess(() -> Component.literal(
                "Scattered " + count + " x " + preset.id().getPath()), false);
        return count;
    }

    private static int setMineDebug(CommandSourceStack source, boolean value) {
        MineDebug.setRenderAreas(value);
        source.sendSuccess(() -> Component.literal("Mine detection overlay " + (value ? "on" : "off")
                + (value ? " (singleplayer only)" : "")), false);
        return 1;
    }

    /** Prints the nearest mine's live state. */
    private static int mineInfo(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        MineEntity nearest = null;
        double bestSq = Double.MAX_VALUE;
        for (MineEntity mine : level.getEntitiesOfClass(MineEntity.class,
                new AABB(at, at).inflate(64.0), MineEntity::isAlive)) {
            double d = mine.distanceToSqr(at);
            if (d < bestSq) {
                bestSq = d;
                nearest = mine;
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("No mine within 64 blocks."));
            return 0;
        }
        MineEntity mine = nearest;
        double distance = Math.sqrt(bestSq);
        Vec3 facing = mine.facing();
        source.sendSuccess(() -> Component.literal(String.format("Mine %s at %.1f %.1f %.1f",
                mine.getStringUUID().substring(0, 8), mine.getX(), mine.getY(), mine.getZ())), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "  state=%s  trigger=%s  warhead=%s  bounds=%s", mine.getState(),
                mine.getTriggerId().getPath(), mine.getDetonationId().getPath(),
                mine.bounds())), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "  trigger=%.1f  crouching=%.1f  arc=%.0f  yaw=%.0f facing=(%.2f, %.2f)",
                mine.getTriggerRange(), mine.getSneakTriggerRange(), mine.getArc(), mine.getYRot(),
                facing.x, facing.z)), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "  pitch=%.0f  roll=%.0f  tumble=%.0f deg/t  rest tilt=%.0f", mine.getXRot(),
                mine.getRoll(), mine.getTumble(), mine.getRestTilt())), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "  distance from here=%.2f  in arc=%s", distance,
                inArc(mine, at) ? "yes" : "no")), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "  wet=%s  arms only in water=%s  self-destruct=%s", mine.isWet(), mine.armsOnlyInWater(),
                mine.selfDestructTicks() <= 0 ? "never"
                        : mine.ticksToSelfDestruct() / 20 + "s of " + mine.selfDestructTicks() / 20 + "s")),
                false);
        return 1;
    }

    /** Whether {@code at} lies inside a directional mine's wedge, which is the usual reason for silence. */
    private static boolean inArc(MineEntity mine, Vec3 at) {
        Vec3 facing = mine.facing();
        double dx = at.x - mine.getX();
        double dz = at.z - mine.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) {
            return true;
        }
        double cos = (dx * facing.x + dz * facing.z) / horizontal;
        return cos >= Math.cos(Math.toRadians(mine.getArc() * 0.5));
    }
}
