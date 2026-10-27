package com.wf.wflib.anomaly.client;

import com.wf.gemrender.lens.Lenses;
import com.wf.gemrender.light.Lights;
import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.AnomalyConfig;
import com.wf.wflib.anomaly.AnomalyBlockEntity;
import com.wf.wflib.anomaly.AnomalyBlockEntity.Phase;
import com.wf.wflib.anomaly.AnomalyBehaviour;
import com.wf.wflib.anomaly.AnomalyLook;
import com.wf.wflib.anomaly.MincerTuning;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Anomalies as seen, each by its {@link AnomalyLook}: a GemRender {@link Lenses lens} (idle shimmer, spinning up
 * through a mincer's blowout, a shockwave at the explosion, then as much of the idle look as stays while spent),
 * orbiting debris, a flash ({@code blowout_light}) and vanilla sounds standing in for STALKER's. Also moves the local
 * player, whose motion only this side owns, and throws its view about while a mincer has it.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class AnomalyClient {
    private static final int SHOCK_TICKS = 12;
    private static final double DRAW_DISTANCE = 96.0D;


    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final Set<AnomalyBlockEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    /** Debug ({@link AnomalyDebug}): per anomaly, when last ticked / lensed and why last skipped. */
    static final Map<AnomalyBlockEntity, Stamp> STAMPS = new WeakHashMap<>();
    /** {@code /wfdebug anomaly trace}: log the live set's comings and goings. */
    static boolean trace;
    static int disabledFrames;

    static final class Stamp {
        final int identity;
        long ticked;
        long lensed;
        String skipped = "-";

        Stamp(AnomalyBlockEntity be) {
            identity = System.identityHashCode(be);
        }
    }

    static Stamp stamp(AnomalyBlockEntity be) {
        return STAMPS.computeIfAbsent(be, Stamp::new);
    }

    static Set<AnomalyBlockEntity> live() {
        return LIVE;
    }

    /** STALKER particle groups playing for an anomaly: its looped idle look, its blowout. */
    private static final Map<AnomalyBlockEntity, List<com.wf.wflib.client.xray.XrParticles.Playing>> IDLE_FX =
            new WeakHashMap<>();
    private static final Map<AnomalyBlockEntity, com.wf.wflib.client.xray.XrParticles.Playing> BLOWOUT_FX =
            new WeakHashMap<>();

    private static final Map<AnomalyBlockEntity, AnomalyIdleSound> IDLE_SOUND = new WeakHashMap<>();

    /** Burnt Fuzz / Grabber hair, per anomaly. */
    private static final Map<AnomalyBlockEntity, FuzzStrands> STRANDS = new WeakHashMap<>();
    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/anomaly/white.png");
    private static final ResourceLocation RING =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/anomaly/ring.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/anomaly/glow.png");

    /** Blowout sound still playing, faded out if the blowout fizzles (or, if it {@code endsWithBlowout}, ends). */
    private static final Map<AnomalyBlockEntity, AnomalyBlowoutSound> BUILDUP = new WeakHashMap<>();

    private AnomalyClient() {
    }

    /** Client setup. */
    public static void register() {
        Lights.register((sink, partialTick) -> {
            if (!AnomalyConfig.enabled) {
                return;
            }
            for (AnomalyBlockEntity be : LIVE) {
                AnomalyLook look = be.type().look();
                Vec3 c = be.center(partialTick);
                int since = be.sinceExplosion();
                // Irradiance ~ intensity / (d^2 + 1): intensities on WFLights' scale (a flash range^2 / 2).
                float range2 = look.flashRange() * look.flashRange();
                if (since >= 0 && since < look.flashTicks()) {
                    // A burn holds its light (flickering); a blast's fades.
                    float k = 1.0F - (since + partialTick) / look.flashTicks();
                    float level = look.style() == AnomalyLook.Style.FIRE
                            ? range2 * (0.35F + 0.05F * (float) Math.sin((since + partialTick) * 1.7F))
                            : range2 * 0.08F * k * k;
                    sink.point(c.x, c.y + (be.type().quirk().moves() ? 0.0D : 2.0D), c.z, look.flashRange(),
                            look.flashRgb(), level, false);
                } else if (look.idleLight() > 0.0F && be.phase() != Phase.BLOWOUT) {
                    float flicker = look.style() == AnomalyLook.Style.ELECTRIC
                            ? 0.6F + 0.4F * (float) Math.abs(Math.sin((be.age() + partialTick) * 2.3F)) : 1.0F;
                    // Over each idle copy and well up, so the whole patch glows, not a hot spot in its middle.
                    int copies = copies(look);
                    double ring = ring(be, copies);
                    float level = look.idleLight() * look.idleLight() * 0.15F / copies * flicker
                            * be.presence(partialTick);
                    double lift = be.type().quirk().moves() ? 0.0D : 2.5D;
                    for (int i = 0; i < copies; i++) {
                        double a = Mth.TWO_PI * i / copies;
                        sink.point(c.x + Math.cos(a) * ring, c.y + lift, c.z + Math.sin(a) * ring,
                                look.idleLight(), look.flashRgb(), level, false);
                    }
                }
            }
        });
    }

    public static void forget(AnomalyBlockEntity be) {
        if (LIVE.remove(be) && trace) {
            LOGGER.info("[anomaly] forget {} @{} (live {})", Integer.toHexString(System.identityHashCode(be)),
                    be.getBlockPos().toShortString(), LIVE.size());
        }
        quiet(be);
        AnomalyIdleSound idle = IDLE_SOUND.remove(be);
        if (idle != null) {
            Minecraft.getInstance().getSoundManager().stop(idle);
        }
        stopIdleFx(be);
        stopFx(BLOWOUT_FX, be);
        STRANDS.remove(be);
    }

    private static void stopIdleFx(AnomalyBlockEntity be) {
        List<com.wf.wflib.client.xray.XrParticles.Playing> playing = IDLE_FX.remove(be);
        if (playing != null) {
            playing.forEach(com.wf.wflib.client.xray.XrParticles.Playing::stop);
        }
    }

    private static void stopFx(Map<AnomalyBlockEntity, com.wf.wflib.client.xray.XrParticles.Playing> fx,
                               AnomalyBlockEntity be) {
        com.wf.wflib.client.xray.XrParticles.Playing playing = fx.remove(be);
        if (playing != null) {
            playing.stop();
        }
    }

    /** Spread wide, one copy of the idle look thins out: three round the middle keep STALKER's density. */
    private static int copies(AnomalyLook look) {
        return look.particleScale() > 2.0F ? 3 : 1;
    }

    private static double ring(AnomalyBlockEntity be, int copies) {
        return copies > 1 ? be.behaviour().radius() * 0.35D : 0.0D;
    }

    /**
     * Where an anomaly's STALKER particle groups are played from: its centre, but for the Comet the ground under its
     * fireball, as STALKER's path point is (heat_02/03 build the fire 2 m above their origin).
     */
    private static Vec3 emitter(AnomalyBlockEntity be, Vec3 c) {
        return be.type().quirk() == com.wf.wflib.anomaly.AnomalyType.Quirk.ORBIT
                ? c.subtract(0.0D, 2.0D * be.type().look().particleHeight(), 0.0D) : c;
    }

    /** STALKER's particles: the idle group looped while present, the blowout group from the explosion to its end. */
    private static void particles(AnomalyBlockEntity be, Vec3 at, AnomalyLook look) {
        Vec3 c = emitter(be, at);
        if (look.idleParticles() != null) {
            boolean present = be.presence(0.0F) > 0.0F;
            if (present && !IDLE_FX.containsKey(be)) {
                List<com.wf.wflib.client.xray.XrParticles.Playing> playing = new ArrayList<>();
                int copies = copies(look);
                double ring = ring(be, copies);
                for (int i = 0; i < copies; i++) {
                    double a = Mth.TWO_PI * i / copies;
                    com.wf.wflib.client.xray.XrParticles.play(look.idleParticles(),
                            c.add(Math.cos(a) * ring, 0.0D, Math.sin(a) * ring), look.particleScale() / copies * 1.5F,
                            look.particleHeight(), true).ifPresent(playing::add);
                }
                IDLE_FX.put(be, playing);
            } else if (!present) {
                stopIdleFx(be);
            }
        }
        if (look.blowoutParticles() != null) {
            if (be.exploding()) {
                stopFx(BLOWOUT_FX, be);
                com.wf.wflib.client.xray.XrParticles.play(look.blowoutParticles(), c, look.particleScale(),
                        look.particleHeight(), false)
                        .ifPresent(p -> BLOWOUT_FX.put(be, p));
            } else if (be.phase() != Phase.BLOWOUT) {
                stopFx(BLOWOUT_FX, be);
            }
        }
    }

    private static void quiet(AnomalyBlockEntity be) {
        AnomalyBlowoutSound playing = BUILDUP.remove(be);
        if (playing != null) {
            playing.fadeOut();
        }
    }

    public static void tick(AnomalyBlockEntity be) {
        if (LIVE.add(be) && trace) {
            LOGGER.info("[anomaly] live {} @{} phase {} (live {})",
                    Integer.toHexString(System.identityHashCode(be)), be.getBlockPos().toShortString(), be.phase(),
                    LIVE.size());
        }
        stamp(be).ticked = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        Level level = be.getLevel();
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return;
        }
        Vec3 c = be.center();
        AnomalyBehaviour behaviour = be.behaviour();
        AnomalyLook look = be.type().look();
        RandomSource random = level.random;
        double playerDist = be.reach(player, c);
        if (AnomalyBlockEntity.affected(player) && playerDist <= behaviour.radius()
                && (!be.striking() || be.claim(player))) {
            if (be.striking() && be.type().quirk() == com.wf.wflib.anomaly.AnomalyType.Quirk.TIME_SLIP
                    && be.behaviour().trips(playerDist)) {
                TimeSlipOverlay.start();
            }
            be.affect(player);
            if (be.phase() == Phase.BLOWOUT && !be.exploding() && behaviour instanceof MincerTuning mincer
                    && mincer.caught(playerDist, true)) {
                // Thrown about: the view goes with the body.
                float p = (float) be.ticks() / mincer.explosionTick();
                player.setYRot(player.getYRot() + (random.nextFloat() - 0.5F) * (10.0F + 50.0F * p));
                player.setXRot(Mth.clamp(player.getXRot() + (random.nextFloat() - 0.5F) * (6.0F + 24.0F * p),
                        -90.0F, 90.0F));
            }
        }
        particles(be, c, look);
        if (look.style() == AnomalyLook.Style.FUZZ) {
            STRANDS.computeIfAbsent(be, FuzzStrands::new).tick(be, level);
        }
        boolean blowout = be.phase() == Phase.BLOWOUT;
        if (blowout) {
            AnomalyEffects.blowout(level, be, c, random);
        } else {
            AnomalyEffects.idle(level, be, c, random);
        }
        if (!blowout && BUILDUP.containsKey(be)) {
            quiet(be);
        }
        AnomalyIdleSound idle = IDLE_SOUND.get(be);
        if (be.type().sounds().idle() != null && be.presence(0.0F) > 0.0F
                && (idle == null || !mc.getSoundManager().isActive(idle))) {
            AnomalyIdleSound loop = new AnomalyIdleSound(be);
            mc.getSoundManager().play(loop);
            IDLE_SOUND.put(be, loop);
        }
        if (blowout && be.ticks() == Math.max(1, be.type().sounds().blowoutTick())
                && be.type().sounds().blowout() != null) {
            quiet(be);
            AnomalyBlowoutSound sound = new AnomalyBlowoutSound(be, random);
            mc.getSoundManager().play(sound);
            BUILDUP.put(be, sound);
        }
        if (be.exploding()) {
            if (!be.type().sounds().endsWithBlowout()) {
                BUILDUP.remove(be);
            }
            AnomalyEffects.explode(level, be, c, random);
        }
    }

    /** Before {@link Lenses#draw} (AFTER_LEVEL); fuzz after the entities, Rebounder discs with the particles. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            if (AnomalyConfig.enabled) {
                shapes(event, event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES);
            }
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (!AnomalyConfig.enabled) {
            disabledFrames++;
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (AnomalyBlockEntity be : LIVE) {
            Vec3 c = be.center(partial);
            Stamp stamp = stamp(be);
            String skip = be.isRemoved() ? "removed" : be.getLevel() != Minecraft.getInstance().level
                    ? "other level" : c.distanceTo(camera) > DRAW_DISTANCE ? "far"
                    : be.phase() == Phase.SPENT ? "spent" : null;
            stamp.skipped = skip == null ? "-" : skip;
            if (skip != null && !"spent".equals(skip)) {
                continue;
            }
            lens(be, c, partial);
            if (skip == null) {
                stamp.lensed = System.nanoTime();
            }
        }
    }

    /** Burnt Fuzz hair and wandering anomalies' particles ({@code solid}), or Rebounder discs and glows. */
    private static void shapes(RenderLevelStageEvent event, boolean solid) {
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (solid) {
            // Before the particles draw: a wandering anomaly's emitters follow it each frame, trailing their fire.
            for (AnomalyBlockEntity be : LIVE) {
                if (be.type().quirk().moves() && !be.isRemoved()) {
                    Vec3 c = emitter(be, be.center(partial));
                    IDLE_FX.getOrDefault(be, List.of()).forEach(p -> p.trailTo(c));
                    com.wf.wflib.client.xray.XrParticles.Playing blowout = BLOWOUT_FX.get(be);
                    if (blowout != null) {
                        blowout.trailTo(c);
                    }
                }
            }
        }
        shapes(event, solid, partial, solid ? net.minecraft.client.renderer.RenderType.entityCutoutNoCull(WHITE)
                : com.wf.wflib.client.render.WFRenderTypes.xray(RING, com.wf.wflib.client.xray.XrDefs.Blend.ALPHA_ADD));
        if (!solid) {
            shapes(event, false, partial, com.wf.wflib.client.render.WFRenderTypes.xray(GLOW,
                    com.wf.wflib.client.xray.XrDefs.Blend.ALPHA_ADD));
        }
    }

    private static void shapes(RenderLevelStageEvent event, boolean fuzz, float partial,
                               net.minecraft.client.renderer.RenderType type) {
        boolean glow = type == com.wf.wflib.client.render.WFRenderTypes.xray(GLOW,
                com.wf.wflib.client.xray.XrDefs.Blend.ALPHA_ADD);
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        com.mojang.blaze3d.vertex.PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        org.joml.Matrix4f matrix = pose.last().pose();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean any = false;
        for (AnomalyBlockEntity be : LIVE) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.center().distanceTo(cam) > DRAW_DISTANCE) {
                continue;
            }
            if (fuzz) {
                FuzzStrands strands = STRANDS.get(be);
                if (strands != null) {
                    int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, be.getBlockPos());
                    strands.emit(buffers.getBuffer(type), matrix, cam, partial, light);
                    any = true;
                }
            } else if (glow) {
                if (be.type().quirk() == com.wf.wflib.anomaly.AnomalyType.Quirk.ORBIT) {
                    glow(buffers.getBuffer(type), matrix, event.getCamera(), be, partial);
                    any = true;
                }
            } else if (be.type().quirk() == com.wf.wflib.anomaly.AnomalyType.Quirk.REBOUNDS
                    && be.presence(partial) > 0.0F) {
                disc(buffers.getBuffer(type), matrix, be, partial);
                any = true;
            }
        }
        if (any) {
            buffers.endBatch(type);
        }
        pose.popPose();
    }

    /**
     * The Rebounder's disc of force waves, upright and turned its own way: rings swelling out from its middle and
     * fading, flaring through a blowout.
     */
    private static void disc(VertexConsumer out, org.joml.Matrix4f matrix, AnomalyBlockEntity be, float partial) {
        Vec3 c = be.center(partial).add(0.0D, be.type().look().lensHeight(), 0.0D);
        double yaw = RandomSource.create(be.getBlockPos().asLong()).nextDouble() * Math.PI;
        float ux = (float) Math.cos(yaw);
        float uz = (float) Math.sin(yaw);
        float big = be.type().look().lensRadius();
        float t = (be.age() + partial) / 30.0F;
        float flare = be.phase() == Phase.BLOWOUT ? 3.0F : 1.0F;
        for (int k = 0; k < 4; k++) {
            float ph = (t + k * 0.25F) % 1.0F;
            float size = big * (0.15F + 0.85F * ph);
            float a = Math.min(1.0F, 0.22F * flare * Mth.sin((float) Math.PI * ph) * be.presence(partial));
            ring(out, matrix, c, -ux * size, -size, -uz * size, 0.0F, 1.0F, a);
            ring(out, matrix, c, ux * size, -size, uz * size, 1.0F, 1.0F, a);
            ring(out, matrix, c, ux * size, size, uz * size, 1.0F, 0.0F, a);
            ring(out, matrix, c, -ux * size, size, -uz * size, 0.0F, 0.0F, a);
        }
    }

    /**
     * The Comet's glow (STALKER's HDR bloom round its fireball): a wide warm halo and a white-hot core, flickering,
     * brighter as it strikes.
     */
    private static void glow(VertexConsumer out, org.joml.Matrix4f matrix, net.minecraft.client.Camera camera,
                             AnomalyBlockEntity be, float partial) {
        Vec3 c = be.center(partial);
        org.joml.Vector3f up = camera.getUpVector();
        org.joml.Vector3f left = camera.getLeftVector();
        float t = be.age() + partial;
        float flicker = 0.85F + 0.1F * Mth.sin(t * 0.9F) + 0.05F * Mth.sin(t * 2.3F + 1.0F);
        float strike = be.phase() == Phase.BLOWOUT ? 1.3F : 1.0F;
        billboard(out, matrix, c, up, left, 2.6F * strike, 1.0F, 0.5F, 0.15F, 0.55F * flicker);
        billboard(out, matrix, c, up, left, 1.2F * strike, 1.0F, 0.8F, 0.45F, 0.8F * flicker);
        billboard(out, matrix, c, up, left, 0.5F, 1.0F, 0.97F, 0.85F, 1.0F);
    }

    private static void billboard(VertexConsumer out, org.joml.Matrix4f matrix, Vec3 c, org.joml.Vector3f up,
                                  org.joml.Vector3f left, float size, float r, float g, float b, float a) {
        float ux = up.x() * size;
        float uy = up.y() * size;
        float uz = up.z() * size;
        float lx = left.x() * size;
        float ly = left.y() * size;
        float lz = left.z() * size;
        float x = (float) c.x;
        float y = (float) c.y;
        float z = (float) c.z;
        out.addVertex(matrix, x + lx - ux, y + ly - uy, z + lz - uz).setUv(0.0F, 1.0F).setColor(r, g, b, a);
        out.addVertex(matrix, x - lx - ux, y - ly - uy, z - lz - uz).setUv(1.0F, 1.0F).setColor(r, g, b, a);
        out.addVertex(matrix, x - lx + ux, y - ly + uy, z - lz + uz).setUv(1.0F, 0.0F).setColor(r, g, b, a);
        out.addVertex(matrix, x + lx + ux, y + ly + uy, z + lz + uz).setUv(0.0F, 0.0F).setColor(r, g, b, a);
    }

    private static void ring(VertexConsumer out, org.joml.Matrix4f matrix, Vec3 c, float dx, float dy, float dz,
                             float u, float v, float alpha) {
        out.addVertex(matrix, (float) c.x + dx, (float) c.y + dy, (float) c.z + dz).setUv(u, v)
                .setColor(0.85F, 0.95F, 1.0F, alpha);
    }

    private static void lens(AnomalyBlockEntity be, Vec3 c, float partial) {
        AnomalyBehaviour behaviour = be.behaviour();
        AnomalyLook look = be.type().look();
        double y = c.y + look.lensHeight();
        float ticks = be.ticks() + partial;
        float presence = be.presence(partial);
        int since = be.sinceExplosion();
        if (look.style() == AnomalyLook.Style.GRAVITY && since >= 0 && since < SHOCK_TICKS) {
            float q = (since + partial) / SHOCK_TICKS;
            // The front: a strong shimmer racing outward.
            Lenses.getInstance().submit(c.x, y, c.z, Mth.lerp(q, 1.0F, (float) behaviour.radius() * 2.2F),
                    0.04F * (1.0F - q), q * 2.0F);
        }
        if (be.phase() == Phase.SPENT && presence <= 0.0F) {
            return;
        }
        float t = (be.age() + partial) / 20.0F;
        float radius = look.lensRadius() * (0.5F + 0.5F * presence);
        // Idle: a shimmering bubble, turning slowly as STALKER's billboards do.
        float strength = look.idleStrength() * presence;
        float turn = t * look.idleTurn();
        if (be.phase() == Phase.BLOWOUT) {
            float p = Math.min(ticks / behaviour.explosionTick(), 1.0F);
            radius = Mth.lerp(p, look.lensRadius(), (float) Math.max(behaviour.radius() * 0.8D, look.lensRadius()));
            strength += look.blowoutStrength() * p * p;
            // Spinning up as it builds.
            turn += look.blowoutTurn() * p * p;
            if (look.style() == AnomalyLook.Style.FIRE) {
                // The haze rides up the flame column.
                y = c.y + 1.8D;
                radius = 2.2F;
            }
        }
        if (strength > 0.0F) {
            Lenses.getInstance().submit(c.x, y, c.z, radius, strength, turn);
        }
    }
}
