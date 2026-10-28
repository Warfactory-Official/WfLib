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
        clusterTick(mc);
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
                case "clusters" -> clusters();
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
        // The bench world keeps the player's health between runs (and creative never heals): start whole.
        if (mc.player.isDeadOrDying()) {
            mc.player.respawn();
        }
        mc.player.connection.sendCommand("effect give @s minecraft:instant_health 1 10 true");
        mc.player.connection.sendCommand("item replace entity @s weapon.mainhand with " + HOLD_ITEM);
        holding = 0;
        return true;
    }

    private static int holding = -1;
    /** {@code -Dwflib.anomalyTest.item}: the artefact the hold test uses. */
    private static final String HOLD_ITEM = System.getProperty("wflib.anomalyTest.item", "wflib:battery");
    private static float healthBefore;

    private static void holdTick(Minecraft mc) {
        if (holding < 0) {
            return;
        }
        holding++;
        // The bench window renders a few frames a second (parked out of sight), so motion is checked as stills at
        // forced lifts, and the switch itself by where the lift ends up, not by filmstrip.
        if (holding >= 50 && holding < 100 && (holding - 50) % 10 == 0) {
            float lift = (holding - 50) / 40.0F;
            com.wf.wflib.artifact.client.ArtifactRenderer.forceLift(lift);
        } else if (holding >= 50 && holding < 100 && (holding - 50) % 10 == 8) {
            shot(String.format(Locale.ROOT, "artifact-lift-%.2f", (holding - 58) / 40.0F));
        }
        switch (holding) {
            case 40 -> {
                shot("artifact-first-person");
                // The light must be at the artefact: ahead along the view, about half a block from the eyes.
                net.minecraft.world.phys.Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
                org.joml.Vector3f l = com.wf.wflib.artifact.client.ArtifactRenderer.LAST_LIGHT;
                net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(l.x, l.y, l.z).subtract(eye);
                double ahead = to.normalize().dot(mc.player.getLookAngle());
                // At the artefact (~0.4 out) plus the set-back behind it (0.35).
                lightOk = ahead > 0.7D && to.length() > 0.5D && to.length() < 1.1D;
                LOGGER.info("[anomaly-test] hold: light {} from the eye, {} ahead",
                        String.format(Locale.ROOT, "%.2f", to.length()), String.format(Locale.ROOT, "%.2f", ahead));
            }
            case 42, 44 -> shot("artifact-first-person-" + holding);
            case 100 -> {
                com.wf.wflib.artifact.client.ArtifactRenderer.forceLift(-1.0F);
                select(mc, 1);
            }
            case 140 -> {
                switchChecks.add(com.wf.wflib.artifact.client.ArtifactRenderer.lift() == 0.0F
                        || !(mc.player.getMainHandItem().getItem() instanceof com.wf.wflib.artifact.ArtifactItem)
                        && com.wf.wflib.artifact.client.ArtifactRenderer.lift() <= 0.05F);
                select(mc, 0);
            }
            case 180 -> switchChecks.add(com.wf.wflib.artifact.client.ArtifactRenderer.lift() >= 0.999F);
            case 185 -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            case 205 -> {
                shot("artifact-inventory");
                mc.setScreen(null);
            }
            case 215 -> {
                // Dropped, close: 1.2 blocks ahead, looked down at (where a flat glow would show its edges).
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                net.minecraft.world.phys.Vec3 at = mc.player.position().add(
                        mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2D));
                mc.player.connection.sendCommand(String.format(Locale.ROOT,
                        "summon item %.2f %.2f %.2f {Item:{id:\"%s\",count:1},PickupDelay:32767,NoGravity:0b}",
                        at.x, at.y + 0.2D, at.z, HOLD_ITEM));
                mc.player.setXRot(55.0F);
            }
            case 225 -> shot("artifact-ground");
            case 228 -> {
                mc.player.connection.sendCommand("kill @e[type=item,distance=..4]");
                mc.player.setXRot(10.0F);
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            }
            case 235 -> {
                shot("artifact-third-person");
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                // Worn: one in an artifact slot, in survival with no natural regeneration.
                mc.player.connection.sendCommand("gamemode survival");
                mc.player.connection.sendCommand("gamerule naturalRegeneration false");
                mc.player.connection.sendCommand("curios replace artifact 0 @s with " + HOLD_ITEM);
                mc.player.connection.sendCommand("effect clear @s");
            }
            case 240 -> mc.player.connection.sendCommand("effect give @s minecraft:instant_health 1 10 true");
            case 260 -> {
                healthBefore = mc.player.getHealth();
                // Shock: 4 should land as 4 x (1 - shock). Healing: knocked down 6 first, then measured.
                mc.player.connection.sendCommand(wornArtifact().shock() != 0.0F ? "damage @s 4 wflib:electric"
                        : "damage @s 6 minecraft:generic");
            }
            case 270 -> healthHealing = mc.player.getHealth();
            case 370 -> {
                com.wf.wflib.artifact.Artifact a = wornArtifact();
                float lost = healthBefore - mc.player.getHealth();
                float healed = mc.player.getHealth() - healthHealing;
                mc.player.connection.sendCommand("effect give @s minecraft:instant_health 1 10 true");
                mc.player.connection.sendCommand("gamemode creative");
                mc.player.connection.sendCommand("gamerule naturalRegeneration true");
                holding = -1;
                // Shock: as above. Healing: 5 s at its rate (ticks counted server side, so a tick either way).
                float expectHeal = a.healthPerSecond(mc.player.getMaxHealth()) * 5.0F;
                boolean worn = a.shock() != 0.0F ? Math.abs(lost - 4.0F * (1.0F - a.shock())) < 0.05F
                        : Math.abs(healed - expectHeal) <= a.healthPerSecond(mc.player.getMaxHealth()) + 0.01F;
                boolean away = switchChecks.size() == 2 && switchChecks.get(0);
                boolean back = switchChecks.size() == 2 && switchChecks.get(1);
                boolean lit = lightOk;
                switchChecks.clear();
                finish(List.of(((worn && away && back && lit) ? "[PASS]" : "[FAIL]") + " artifact hold",
                        "  " + (lit ? "ok  " : "FAIL") + " its light is at it, ahead of the eyes",
                        "  " + (away ? "ok  " : "FAIL") + " switched away, it was put away (lift 0)",
                        "  " + (back ? "ok  " : "FAIL") + " switched back, it came up (lift 1)",
                        "  " + (worn ? "ok  " : "FAIL") + (a.shock() != 0.0F
                                ? " worn, 4 shock took " + String.format(Locale.ROOT, "%.2f (%.2f)", lost,
                                4.0F * (1.0F - a.shock()))
                                : " worn, 5 s healed " + String.format(Locale.ROOT, "%.3f (%.3f)", healed,
                                expectHeal))), status());
            }
            default -> {
            }
        }
    }

    private static final List<Boolean> switchChecks = new ArrayList<>();
    private static float healthHealing;
    private static boolean lightOk;

    private static com.wf.wflib.artifact.Artifact wornArtifact() {
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(HOLD_ITEM));
        return ((com.wf.wflib.artifact.ArtifactItem) item).artifact();
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().selected = slot;
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
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

    /** {@code -Dwflib.anomalyTest.tier}: the cluster test's tier (each design at least its own). */
    private static final int CLUSTER_TIER = Integer.getInteger("wflib.anomalyTest.tier", 3);
    /** {@code -Dwflib.anomalyTest.designs=arch,pit}: only these (default all). */
    private static final com.wf.wflib.anomaly.cluster.ClusterPlan.Design[] CLUSTER_DESIGNS =
            java.util.Arrays.stream(com.wf.wflib.anomaly.cluster.ClusterPlan.Design.values())
                    .filter(d -> System.getProperty("wflib.anomalyTest.designs", "").isEmpty()
                            || List.of(System.getProperty("wflib.anomalyTest.designs").split(","))
                            .contains(d.name().toLowerCase(Locale.ROOT)))
                    .toArray(com.wf.wflib.anomaly.cluster.ClusterPlan.Design[]::new);
    private static int[] clustering;
    private static final List<String> clusterLines = new ArrayList<>();

    /**
     * {@code clusters}: one cluster of each design on untouched ground (160 blocks apart, well off the bench), each
     * photographed from a corner and from overhead ({@code anomaly-cluster-<design>-<view>.png}); the result lists
     * the anomalies and artefacts found round each.
     */
    private static boolean clusters() {
        BlockPos p = Minecraft.getInstance().player.blockPosition();
        // design, ticks into it, first site x z, this site's middle x z, retry offset, tries
        clustering = new int[]{0, 0, p.getX() + 400, p.getZ(), 0, 0, 0, 0};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System e) -> {
                    String line = e.getMessage().getString();
                    if (line.startsWith("Tier ") || line.startsWith("No ground")) {
                        clusterReply = line;
                    }
                });
        Minecraft.getInstance().player.connection.sendCommand("gamemode spectator");
        Minecraft.getInstance().player.connection.sendCommand("weather clear 1000000");
        return true;
    }

    /** The server's answer to the last cluster command ("Tier 3 crater at x z, ..." or "No ground ..."). */
    @Nullable
    private static volatile String clusterReply;
    private static final java.util.regex.Pattern CLUSTER_AT =
            java.util.regex.Pattern.compile(" at (-?\\d+) (-?\\d+), radius \\d+: (\\d+) anomalies");

    private static void clusterTick(Minecraft mc) {
        if (clustering == null) {
            return;
        }
        var designs = CLUSTER_DESIGNS;
        var design = designs[clustering[0]];
        String name = design.name().toLowerCase(Locale.ROOT);
        int x = clustering[2] + clustering[0] * 160 + clustering[6];
        int z = clustering[3];
        int t = clustering[1]++;
        if (t == 0) {
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %d 200 %d", x, z));
        } else if (t == 140) {
            clusterReply = null;
            int tier = Math.max(CLUSTER_TIER, design.minTier);
            mc.player.connection.sendCommand(String.format(Locale.ROOT,
                    "execute positioned %d 100 %d run wflib anomalies cluster %d %s", x, z, tier, name));
        } else if (t > 140 && t < 240) {
            String reply = clusterReply;
            if (reply == null) {
                return;
            }
            clusterReply = null;
            if (reply.startsWith("No ground") && ++clustering[7] < 3) {
                // Water or hills all round: try a stretch further on.
                clustering[6] += 300;
                clustering[1] = 0;
                return;
            }
            clusterLines.add(name + ": " + reply);
            say(name + ": " + reply);
            java.util.regex.Matcher m = CLUSTER_AT.matcher(reply);
            if (!m.find()) {
                nextCluster();
                return;
            }
            clustering[4] = Integer.parseInt(m.group(1));
            clustering[5] = Integer.parseInt(m.group(2));
            // Let the new blocks reach the client, then the camera.
            clustering[1] = 200;
        } else if (t == 240) {
            x = clustering[4];
            z = clustering[5];
            // Above whichever is higher, the middle or where the camera stands (a pit's middle is its floor).
            var surface = net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE;
            int y = Math.max(mc.level.getHeight(surface, x, z), mc.level.getHeight(surface, x - 20, z - 20));
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %d %.1f -45 32", x - 19.5D, y + 16,
                    z - 19.5D));
        } else if (t == 300) {
            x = clustering[4];
            z = clustering[5];
            shot("cluster-" + name + "-corner");
            var surface = net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE;
            int y = Math.max(mc.level.getHeight(surface, x, z), mc.level.getHeight(surface, x + 12, z + 12));
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 90", x + 0.5D, y + 30,
                    z + 0.5D));
        } else if (t == 350) {
            shot("cluster-" + name + "-top");
            nextCluster();
        }
    }

    private static void nextCluster() {
        clustering[0]++;
        clustering[1] = 0;
        clustering[6] = 0;
        clustering[7] = 0;
        if (clustering[0] < CLUSTER_DESIGNS.length) {
            return;
        }
        clustering = null;
        boolean all = clusterLines.size() == CLUSTER_DESIGNS.length && clusterLines.stream().allMatch(l -> {
            java.util.regex.Matcher m = CLUSTER_AT.matcher(l);
            return m.find() && Integer.parseInt(m.group(3)) > 0;
        });
        finish(List.of((all ? "[PASS]" : "[FAIL]") + " anomaly clusters: every design placed anomalies"),
                clusterLines);
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
