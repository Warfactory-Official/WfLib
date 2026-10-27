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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
                Vec3 c = be.center();
                int since = be.sinceExplosion();
                // Irradiance ~ intensity / (d^2 + 1): intensities on WFLights' scale (a flash range^2 / 2).
                float range2 = look.flashRange() * look.flashRange();
                if (since >= 0 && since < look.flashTicks()) {
                    // A burn holds its light (flickering); a blast's fades.
                    float k = 1.0F - (since + partialTick) / look.flashTicks();
                    float level = look.style() == AnomalyLook.Style.FIRE
                            ? range2 * (0.35F + 0.05F * (float) Math.sin((since + partialTick) * 1.7F))
                            : range2 * 0.08F * k * k;
                    sink.point(c.x, c.y + 2.0D, c.z, look.flashRange(), look.flashRgb(), level, false);
                } else if (look.idleLight() > 0.0F && be.phase() != Phase.BLOWOUT) {
                    float flicker = look.style() == AnomalyLook.Style.ELECTRIC
                            ? 0.6F + 0.4F * (float) Math.abs(Math.sin((be.age() + partialTick) * 2.3F)) : 1.0F;
                    // Over each idle copy and well up, so the whole patch glows, not a hot spot in its middle.
                    int copies = copies(look);
                    double ring = ring(be, copies);
                    float level = look.idleLight() * look.idleLight() * 0.15F / copies * flicker
                            * be.presence(partialTick);
                    for (int i = 0; i < copies; i++) {
                        double a = Mth.TWO_PI * i / copies;
                        sink.point(c.x + Math.cos(a) * ring, c.y + 2.5D, c.z + Math.sin(a) * ring,
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

    /** STALKER's particles: the idle group looped while present, the blowout group from the explosion to its end. */
    private static void particles(AnomalyBlockEntity be, Vec3 c, AnomalyLook look) {
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
        double playerDist = player.getBoundingBox().getCenter().distanceTo(c);
        if (AnomalyBlockEntity.affected(player) && playerDist <= behaviour.radius()
                && (!be.striking() || be.claim(player))) {
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
        if (be.presence(0.0F) > 0.0F && (idle == null || !mc.getSoundManager().isActive(idle))) {
            AnomalyIdleSound loop = new AnomalyIdleSound(be);
            mc.getSoundManager().play(loop);
            IDLE_SOUND.put(be, loop);
        }
        if (blowout && be.ticks() == Math.max(1, be.type().sounds().blowoutTick())) {
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

    /** Before {@link Lenses#draw} (AFTER_LEVEL). */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
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
            Vec3 c = be.center();
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
