package com.wf.wflib.anomaly.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.wf.gemrender.lens.Lenses;
import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.AnomalyConfig;
import com.wf.wflib.anomaly.AnomalyBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Anomaly test bench, client side. {@code /wfdebug anomaly status} dumps the chain from anomaly to screen (config,
 * live set, per anomaly last tick / last lens / skip reason, loaded anomalies missing from the live set, the lens
 * pass); {@code measure} reads one lens pass back and says how much of the screen it changed (drawn is not seen);
 * {@code trace} logs the live set's comings and goings; {@code walkaway [blocks] [ticks]} teleports the player
 * away (chunks unload), back, and checks every link, pixels included, PASS/FAIL to chat, log
 * ({@code [anomaly-test]}) and {@code wflib-anomaly-test.txt}. {@code -Dwflib.anomalyTest=walkaway} runs the whole
 * thing on joining a world
 * (anomalies on, creative, an anomaly placed beside the player); {@code -Dwflib.anomalyTest.quit=true} then closes
 * the game. {@code -Dwflib.anomalyTest=trigger} instead sets a pig down on it: it must fire and hurt or throw the
 * pig; {@code bolt} drops a stick on it: it must not fire (only the living set anomalies off); {@code look}
 * screenshots it idle and blowing out. {@code -Dwflib.anomalyTest.block} picks the anomaly.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class AnomalyDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String AUTO = System.getProperty("wflib.anomalyTest");
    /** The anomaly the bench places: {@code -Dwflib.anomalyTest.block}. */
    private static final String AUTO_BLOCK = System.getProperty("wflib.anomalyTest.block", "wflib:gravity_anomaly");
    /** {@code -Dwflib.anomalyTest.time}: the bench's time of day (day, night, ...). */
    private static final String AUTO_TIME = System.getProperty("wflib.anomalyTest.time", "day");
    private static final boolean AUTO_QUIT = Boolean.getBoolean("wflib.anomalyTest.quit");
    private static final long SECOND = 1_000_000_000L;
    private static final int SCAN_CHUNKS = 8;
    private static final int SETTLE_TICKS = 100;

    /** Minimum share of the screen a drawn lens must change to count as seen (the idle shimmer is faint). */
    private static final float SEEN = 0.002F;

    /** Where {@link #auto} placed the bench's anomaly: the tests use it, not the nearest. */
    @Nullable
    private static BlockPos placed;
    @Nullable
    private static Walk walk;
    @Nullable
    private static Trigger trigger;
    private static int measureSay;
    private static int autoTicks = -1;

    private AnomalyDebug() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("wfdebug").then(Commands.literal("anomaly")
                .then(Commands.literal("status").executes(c -> {
                    status().forEach(line -> c.getSource().sendSuccess(() -> Component.literal(line), false));
                    status().forEach(line -> LOGGER.info("[anomaly-test] {}", line));
                    return 1;
                }))
                .then(Commands.literal("measure").executes(c -> {
                    Lenses.getInstance().measure();
                    measureSay = 3;
                    return 1;
                }))
                .then(Commands.literal("trace").executes(c -> {
                    AnomalyClient.trace = !AnomalyClient.trace;
                    c.getSource().sendSuccess(() -> Component.literal("anomaly trace "
                            + (AnomalyClient.trace ? "on (log: [anomaly])" : "off")), false);
                    return 1;
                }))
                .then(Commands.literal("walkaway").executes(c -> start(600, 200))
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(64, 100000))
                                .executes(c -> start(IntegerArgumentType.getInteger(c, "blocks"), 200))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(20, 6000))
                                        .executes(c -> start(IntegerArgumentType.getInteger(c, "blocks"),
                                                IntegerArgumentType.getInteger(c, "ticks")))))));
    }

    /** Every link from loaded anomaly to drawn lens, one line each. */
    public static List<String> status() {
        Minecraft mc = Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        long now = System.nanoTime();
        out.add("config enabled=" + AnomalyConfig.enabled + ", frames skipped as disabled="
                + AnomalyClient.disabledFrames);
        out.add(Lenses.getInstance().report());
        out.add(com.wf.wflib.client.xray.XrParticles.report());
        out.add("live set: " + AnomalyClient.live().size());
        Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
        for (AnomalyBlockEntity be : List.copyOf(AnomalyClient.live())) {
            AnomalyClient.Stamp st = AnomalyClient.stamp(be);
            out.add(String.format(Locale.ROOT,
                    "  %s @%s %s t=%d age=%d presence=%.2f removed=%s sameLevel=%s dist=%.1f ticked=%s lensed=%s skip=%s",
                    Integer.toHexString(st.identity), be.getBlockPos().toShortString(), be.phase(), be.ticks(),
                    be.age(), be.presence(0.0F), be.isRemoved(), be.getLevel() == mc.level,
                    be.center().distanceTo(eye), ago(now, st.ticked), ago(now, st.lensed), st.skipped));
        }
        for (AnomalyBlockEntity be : loaded()) {
            if (!AnomalyClient.live().contains(be)) {
                out.add(String.format(Locale.ROOT, "  NOT LIVE: %s @%s %s age=%d removed=%s (loaded, never ticked?)",
                        Integer.toHexString(System.identityHashCode(be)), be.getBlockPos().toShortString(),
                        be.phase(), be.age(), be.isRemoved()));
            }
        }
        return out;
    }

    private static String ago(long now, long then) {
        return then == 0 ? "never" : (now - then) / 1_000_000L + "ms ago";
    }

    /** Anomalies in the client's loaded chunks around the player. */
    private static List<AnomalyBlockEntity> loaded() {
        Minecraft mc = Minecraft.getInstance();
        List<AnomalyBlockEntity> out = new ArrayList<>();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return out;
        }
        int cx = mc.player.chunkPosition().x;
        int cz = mc.player.chunkPosition().z;
        for (int x = cx - SCAN_CHUNKS; x <= cx + SCAN_CHUNKS; x++) {
            for (int z = cz - SCAN_CHUNKS; z <= cz + SCAN_CHUNKS; z++) {
                LevelChunk chunk = level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof AnomalyBlockEntity anomaly) {
                        out.add(anomaly);
                    }
                }
            }
        }
        return out;
    }

    private static int start(int blocks, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || walk != null) {
            return 0;
        }
        if (!player.isCreative() && !player.isSpectator()) {
            say("walkaway: switch to creative or spectator first (it teleports you into the air)");
            return 0;
        }
        AnomalyBlockEntity nearest = placed != null && mc.level.getBlockEntity(placed) instanceof AnomalyBlockEntity p
                ? p : null;
        for (AnomalyBlockEntity be : placed != null ? List.<AnomalyBlockEntity>of() : loaded()) {
            if (nearest == null || be.center().distanceToSqr(player.position())
                    < nearest.center().distanceToSqr(player.position())) {
                nearest = be;
            }
        }
        if (nearest == null) {
            say("walkaway: no anomaly in the loaded chunks around you");
            return 0;
        }
        walk = new Walk(nearest.getBlockPos(), player.position(), blocks, ticks, System.identityHashCode(nearest));
        walk.log("before", status());
        walk.lensBefore = Lenses.getInstance().lastDrawNanos() > System.nanoTime() - SECOND;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", walk.home.x + blocks,
                walk.home.y, walk.home.z));
        say("walkaway: away " + blocks + " blocks for " + ticks + " ticks, then back to " + walk.target.toShortString());
        return 1;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (AUTO != null && autoTicks >= 0) {
            auto(mc);
        } else if (AUTO != null && autoTicks == -1) {
            autoTicks = 0;
        }
        if (walk != null && --walk.wait <= 0) {
            walk.next(mc);
        }
        if (trigger != null) {
            trigger.tick(mc);
        }
        lookTick(mc);
        holdTick(mc);
        if (measureSay > 0 && --measureSay == 0) {
            float m = Lenses.getInstance().measured();
            say(m < 0.0F ? "measure: no lens pass ran (nothing submitted, or the pass is off)"
                    : String.format(Locale.ROOT, "measure: the lens pass changed %.2f%% of the screen%s", m * 100.0F,
                    m < SEEN ? " - drawn but not visible" : ""));
        }
    }

    /** {@code -Dwflib.anomalyTest=walkaway}: set the bench up, settle, walk. */
    private static void auto(Minecraft mc) {
        int t = autoTicks++;
        LocalPlayer player = mc.player;
        if (t == 20) {
            BlockPos at = player.blockPosition().offset(6, 0, 0);
            placed = at;
            List<String> cmds = new ArrayList<>(List.of("wflib anomalies on", "gamemode creative", "time set " + AUTO_TIME,
                    "gamerule doDaylightCycle false",
                    // A saved bench world keeps the last run's anomalies, pigs and bolts: clear them first.
                    "kill @e[type=!player,distance=..48]"));
            for (String id : new String[]{"gravity_anomaly", "whirligig", "springboard", "electro", "burner", "acid"}) {
                cmds.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace wflib:%s", at.getX() - 16,
                        at.getY() - 4, at.getZ() - 16, at.getX() + 16, at.getY() + 4, at.getZ() + 16, id));
            }
            // A clear stage: flat floor, open air, nothing in front of the anomaly; the player 9 blocks west, facing it.
            cmds.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", at.getX() - 12, at.getY() - 1,
                    at.getZ() - 12, at.getX() + 12, at.getY() - 1, at.getZ() + 12));
            cmds.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", at.getX() - 12, at.getY(),
                    at.getZ() - 12, at.getX() + 12, at.getY() + 10, at.getZ() + 12));
            cmds.add(String.format(Locale.ROOT, "setblock %d %d %d %s", at.getX(), at.getY(), at.getZ(), AUTO_BLOCK));
            cmds.add(String.format(Locale.ROOT, "tp @s %.1f %d %.1f -90 12", at.getX() - 8.5D, at.getY(),
                    at.getZ() + 0.5D));
            for (String cmd : cmds) {
                player.connection.sendCommand(cmd);
            }
            // Face it: the pass draws whatever the view, but a screenshot of the result should show it.
            player.setYRot(-90.0F);
            player.setXRot(10.0F);
        } else if (t == 20 + SETTLE_TICKS) {
            boolean started = switch (AUTO) {
                case "walkaway" -> start(600, 200) == 1;
                case "trigger" -> trigger(false);
                case "bolt" -> trigger(true);
                case "look" -> look();
                case "hold" -> hold();
                default -> false;
            };
            if (!started) {
                finish(List.of("auto: unknown test '" + AUTO + "' or could not start; status:"), status());
            }
            autoTicks = -2;
        }
    }

    /**
     * {@code trigger}: a pig set down on the nearest anomaly; it must fire, then hurt or throw the pig. {@code bolt}:
     * a stick dropped on it instead (STALKER's thrown bolt); it must not fire: items never set anomalies off.
     */
    private static boolean trigger(boolean bolt) {
        Minecraft mc = Minecraft.getInstance();
        AnomalyBlockEntity nearest = placed != null && mc.level.getBlockEntity(placed) instanceof AnomalyBlockEntity p
                ? p : null;
        for (AnomalyBlockEntity be : placed != null ? List.<AnomalyBlockEntity>of() : loaded()) {
            if (nearest == null || be.center().distanceToSqr(mc.player.position())
                    < nearest.center().distanceToSqr(mc.player.position())) {
                nearest = be;
            }
        }
        if (nearest == null) {
            return false;
        }
        Vec3 c = nearest.center();
        mc.player.connection.sendCommand(String.format(Locale.ROOT, bolt
                ? "summon item %.2f %.2f %.2f {Item:{id:\"minecraft:stick\",count:1}}" : "summon pig %.2f %.2f %.2f",
                c.x, c.y + (bolt ? 1.0D : 0.0D), c.z));
        trigger = new Trigger(nearest.getBlockPos(), nearest.type().name(),
                nearest.behaviour().blowoutTicks() + 60, bolt);
        say("trigger: a " + (bolt ? "bolt" : "pig") + " on the " + nearest.type().name() + " at "
                + nearest.getBlockPos().toShortString());
        return true;
    }

    private static final class Trigger {
        final BlockPos target;
        final String name;
        final boolean bolt;
        int wait;
        @Nullable
        net.minecraft.world.entity.animal.Pig pig;
        float startHealth;
        double startY;
        double peakY = -1.0E9D;
        float lowHealth = Float.MAX_VALUE;
        boolean blowout;
        boolean spent;
        boolean dead;
        final List<String> log = new ArrayList<>();

        Trigger(BlockPos target, String name, int wait, boolean bolt) {
            this.target = target;
            this.name = name;
            this.wait = wait;
            this.bolt = bolt;
        }

        void tick(Minecraft mc) {
            if (mc.level.getBlockEntity(target) instanceof AnomalyBlockEntity be) {
                if (be.phase() == AnomalyBlockEntity.Phase.BLOWOUT && !blowout) {
                    log.add("blowout seen, wait " + wait);
                }
                blowout |= be.phase() == AnomalyBlockEntity.Phase.BLOWOUT;
                spent |= be.phase() == AnomalyBlockEntity.Phase.SPENT;
            }
            if (pig == null) {
                Vec3 c = Vec3.atBottomCenterOf(target);
                pig = mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,
                        new net.minecraft.world.phys.AABB(c, c).inflate(3.0D)).stream().findFirst().orElse(null);
                if (pig != null) {
                    startHealth = pig.getHealth();
                    startY = pig.getY();
                }
            } else {
                dead |= !pig.isAlive();
                peakY = Math.max(peakY, pig.getY());
                lowHealth = Math.min(lowHealth, pig.getHealth());
            }
            if (--wait > 0) {
                return;
            }
            trigger = null;
            List<String> checks = new ArrayList<>();
            if (bolt) {
                boolean pass = Walk.check(checks, !blowout && !spent, "a dropped bolt did not set it off");
                List<String> verdict = new ArrayList<>();
                verdict.add("[" + (pass ? "PASS" : "FAIL") + "] anomaly bolt: " + name);
                verdict.addAll(checks);
                finish(verdict, log);
                return;
            }
            boolean pass = Walk.check(checks, pig != null, "a pig appeared on it");
            pass &= Walk.check(checks, blowout, "it fired (blowout)");
            pass &= Walk.check(checks, spent, "and spent itself");
            boolean hurt = pig != null && (dead || lowHealth < startHealth);
            boolean thrown = pig != null && peakY - startY >= 1.0D;
            pass &= Walk.check(checks, hurt || thrown, String.format(Locale.ROOT,
                    "the pig was hurt or thrown (health %.1f -> %.1f%s, rose %.1f blocks)", startHealth,
                    lowHealth == Float.MAX_VALUE ? startHealth : lowHealth, dead ? ", died" : "", peakY - startY));
            List<String> verdict = new ArrayList<>();
            verdict.add("[" + (pass ? "PASS" : "FAIL") + "] anomaly trigger: " + name);
            verdict.addAll(checks);
            finish(verdict, log);
        }
    }

    /**
     * {@code look}: screenshots of the bench anomaly ({@code anomaly-<name>-<when>.png} in screenshots/): idle, then a
     * pig set on it and two shots of the blowout (just after the hit, and later in it).
     */
    private static boolean look() {
        Minecraft mc = Minecraft.getInstance();
        if (placed == null || !(mc.level.getBlockEntity(placed) instanceof AnomalyBlockEntity be)) {
            return false;
        }
        String name = be.type().name().toLowerCase(Locale.ROOT) + "-" + AUTO_TIME;
        shot(name + "-idle");
        Vec3 c = be.center();
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "summon pig %.2f %.2f %.2f", c.x, c.y, c.z));
        int late = Math.max(10, Math.min(be.behaviour().blowoutTicks() - be.behaviour().explosionTick() - 2, 40));
        looking = new int[]{0, 3, late, 0};
        lookName = name;
        return true;
    }

    /**
     * {@code hold}: a Battery in the main hand, then screenshots ({@code artifact-battery-<view>.png}): first person,
     * the inventory and third person from the front.
     */
    private static boolean hold() {
        Minecraft mc = Minecraft.getInstance();
        mc.player.connection.sendCommand("item replace entity @s weapon.mainhand with wflib:battery");
        holding = 0;
        return true;
    }

    private static int holding = -1;
    private static float healthBefore;

    private static void holdTick(Minecraft mc) {
        if (holding < 0) {
            return;
        }
        holding++;
        if (holding == 40) {
            shot("artifact-first-person");
        } else if (holding == 45) {
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        } else if (holding == 70) {
            shot("artifact-inventory");
            mc.setScreen(null);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (holding == 100) {
            shot("artifact-third-person");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            // Worn: one in an artifact slot, then 4 shock in survival should land as 4 x (1 - 0.1212).
            mc.player.connection.sendCommand("gamemode survival");
            mc.player.connection.sendCommand("curios replace artifact 0 @s with wflib:battery");
            mc.player.connection.sendCommand("effect clear @s");
        } else if (holding == 110) {
            healthBefore = mc.player.getHealth();
            mc.player.connection.sendCommand("damage @s 4 wflib:electric");
        } else if (holding == 120) {
            float lost = healthBefore - mc.player.getHealth();
            mc.player.connection.sendCommand("gamemode creative");
            holding = -1;
            boolean pass = Math.abs(lost - 4.0F * (1.0F - 0.1212F)) < 0.05F;
            finish(List.of((pass ? "[PASS]" : "[FAIL]") + " artifact hold: 3 shots; worn Battery, 4 shock took "
                    + String.format(Locale.ROOT, "%.2f", lost)), status());
        }
    }

    @Nullable
    private static int[] looking;
    private static String lookName = "";

    private static void lookTick(Minecraft mc) {
        if (looking == null || !(mc.level.getBlockEntity(placed) instanceof AnomalyBlockEntity be)) {
            return;
        }
        int since = be.sinceExplosion();
        if (since >= 0 && looking[3] == 0 && since >= looking[1]) {
            shot(lookName + "-blowout-early");
            looking[3] = 1;
        } else if (since >= 0 && looking[3] == 1 && since >= looking[2]) {
            shot(lookName + "-blowout-late");
            looking[3] = 2;
        }
        if (++looking[0] > 400 || looking[3] == 2) {
            List<String> verdict = List.of((looking[3] == 2 ? "[PASS]" : "[FAIL]") + " anomaly look: " + lookName
                    + " (" + looking[3] + " blowout shots)");
            looking = null;
            finish(verdict, status());
        }
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, "anomaly-" + name + ".png", mc.getMainRenderTarget(),
                message -> LOGGER.info("[anomaly-test] screenshot {}", name));
    }

    private static void say(String line) {
        LOGGER.info("[anomaly-test] {}", line);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(line), false);
        }
    }

    private static void finish(List<String> verdict, List<String> detail) {
        List<String> all = new ArrayList<>(verdict);
        all.addAll(detail);
        verdict.forEach(AnomalyDebug::say);
        detail.forEach(line -> LOGGER.info("[anomaly-test] {}", line));
        try {
            Files.write(Minecraft.getInstance().gameDirectory.toPath().resolve("wflib-anomaly-test.txt"), all);
        } catch (IOException e) {
            LOGGER.warn("[anomaly-test] could not write wflib-anomaly-test.txt", e);
        }
        if (AUTO != null && AUTO_QUIT) {
            Minecraft.getInstance().stop();
        }
    }

    private static final class Walk {
        final BlockPos target;
        final Vec3 home;
        final int blocks;
        final int ticks;
        final int before;
        final List<String> log = new ArrayList<>();
        int stage;
        int wait;
        boolean lensBefore;
        boolean unloadedAway;

        Walk(BlockPos target, Vec3 home, int blocks, int ticks, int before) {
            this.target = target;
            this.home = home;
            this.blocks = blocks;
            this.ticks = ticks;
            this.before = before;
            this.wait = ticks;
        }

        void log(String when, List<String> lines) {
            log.add("-- " + when);
            log.addAll(lines);
        }

        void next(Minecraft mc) {
            if (stage == 1) {
                // Back and settled: read one pass back, judge a few ticks later.
                Lenses.getInstance().measure();
                stage = 2;
                wait = 5;
                return;
            }
            if (stage == 0) {
                log("away", status());
                unloadedAway = !(mc.level.getBlockEntity(target) instanceof AnomalyBlockEntity);
                mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", home.x, home.y,
                        home.z));
                stage = 1;
                wait = ticks;
                return;
            }
            log("back", status());
            walk = null;
            long now = System.nanoTime();
            List<String> checks = new ArrayList<>();
            boolean pass = true;
            AnomalyBlockEntity be = mc.level.getBlockEntity(target) instanceof AnomalyBlockEntity a ? a : null;
            AnomalyClient.Stamp st = be == null ? null : AnomalyClient.stamp(be);
            pass &= check(checks, lensBefore, "lens pass drew before walking away (baseline)");
            check(checks, unloadedAway, "anomaly chunk unloaded while away (info: else the trip was too short)");
            pass &= check(checks, be != null, "block entity at " + target.toShortString() + " after coming back");
            if (be != null) {
                check(checks, System.identityHashCode(be) != before, "it is a new instance (info: chunk was reloaded)");
                pass &= check(checks, AnomalyClient.live().contains(be), "it is in the live set");
                pass &= check(checks, st.ticked > now - SECOND, "it ticked in the last second");
                pass &= check(checks, AnomalyConfig.enabled, "anomalies enabled on the client");
                boolean spent = be.phase() == AnomalyBlockEntity.Phase.SPENT;
                pass &= check(checks, spent || st.lensed > now - SECOND,
                        "its lens was submitted in the last second (skip=" + st.skipped + ", phase=" + be.phase() + ")");
                pass &= check(checks, spent || Lenses.getInstance().lastDrawNanos() > now - SECOND,
                        "the lens pass drew in the last second");
                float m = Lenses.getInstance().measured();
                pass &= check(checks, spent || m >= SEEN, String.format(Locale.ROOT,
                        "and visibly changed the screen (%.2f%% of pixels, needs %.1f%%)", m * 100.0F, SEEN * 100.0F));
            }
            List<String> verdict = new ArrayList<>();
            verdict.add("[" + (pass ? "PASS" : "FAIL") + "] anomaly walkaway " + blocks + " blocks / " + ticks
                    + " ticks");
            verdict.addAll(checks);
            finish(verdict, log);
        }

        static boolean check(List<String> out, boolean ok, String what) {
            out.add((ok ? "  ok   " : "  FAIL ") + what);
            return ok;
        }
    }
}
