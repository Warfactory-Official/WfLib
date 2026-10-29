package com.wf.wflib.client.xray;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.wf.wflib.WFLib;
import com.wf.wflib.config.WFClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * STALKER's screen and camera effectors, client side: post-process effectors ({@code .ppe}) by id, each with a factor
 * it eases to ({@code add_pp_effector}, {@code set_pp_effector_factor}, {@code remove_pp_effector}), summed as
 * {@code CCameraManager::UpdatePPEffectors} sums them and drawn over world and hands, under the GUI (as X-Ray); camera
 * effectors ({@code .anm}) added to the view's yaw, pitch and roll. Files: {@code assets/<ns>/xray/anims/}.
 * Strength: {@link WFClientConfig#SCREEN_EFFECTS} / {@link WFClientConfig#CAMERA_SHAKE}.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class XrScreen {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
            "shaders/post/xray_pp.json");

    private static final class Post {
        final XrPostEffect effect;
        final boolean loop;
        final double start;
        float factor;
        float dest;
        float speed = 1.0F;
        boolean stopping;

        Post(XrPostEffect effect, boolean loop, double start, float factor) {
            this.effect = effect;
            this.loop = loop;
            this.start = start;
            this.factor = factor;
            this.dest = factor;
        }
    }

    private record Cam(XrCameraMotion motion, double start, float power) {
    }

    private static final Map<String, Post> POSTS = new LinkedHashMap<>();
    private static final List<Cam> CAMS = new ArrayList<>();
    private static final Map<ResourceLocation, XrPostEffect> PPE = new HashMap<>();
    private static final Map<ResourceLocation, XrCameraMotion> ANM = new HashMap<>();
    private static final XrPostEffect.Pp ONE = new XrPostEffect.Pp();
    private static final XrPostEffect.Pp SUM = new XrPostEffect.Pp();
    private static final float[] CAM = new float[6];
    private static PostChain chain;
    private static int chainWidth;
    private static int chainHeight;
    private static double lastFrame;
    private static float noiseSeed;
    private static double noiseNext;

    private XrScreen() {
    }

    private static double now() {
        return System.nanoTime() * 1.0E-9D;
    }

    /** {@code wflib:alcohol} -> {@code assets/wflib/xray/anims/alcohol.ppe}. */
    public static ResourceLocation ppe(String namespace, String name) {
        return ResourceLocation.fromNamespaceAndPath(namespace, "xray/anims/" + name + ".ppe");
    }

    public static ResourceLocation anm(String namespace, String name) {
        return ResourceLocation.fromNamespaceAndPath(namespace, "xray/anims/camera_effects/" + name + ".anm");
    }

    /**
     * Starts effector {@code id} (unless it runs) on {@code file}, easing to {@code factor}; a one-shot ends after
     * its length, a loop runs until {@link #stop}.
     */
    public static void post(String id, ResourceLocation file, boolean loop, float factor) {
        Post p = POSTS.get(id);
        if (p == null || p.stopping) {
            XrPostEffect effect = load(PPE, file, XrPostEffect::parse);
            if (effect == null) {
                return;
            }
            POSTS.put(id, new Post(effect, loop, now(), factor));
            p = POSTS.get(id);
        }
        p.dest = factor;
    }

    /** {@code set_pp_effector_factor}: {@code id} eases to {@code factor} at {@code speed} per second. */
    public static void factor(String id, float factor, float speed) {
        Post p = POSTS.get(id);
        if (p != null && !p.stopping) {
            p.dest = factor;
            p.speed = speed;
        }
    }

    /** {@code CPostprocessAnimator::Stop}: fades {@code id} out at {@code speed} per second. */
    public static void stop(String id, float speed) {
        Post p = POSTS.get(id);
        if (p != null) {
            p.stopping = true;
            p.speed = speed;
        }
    }

    public static boolean running(String id) {
        Post p = POSTS.get(id);
        return p != null && !p.stopping;
    }

    /** Plays camera effector {@code file} once, scaled by {@code power}; its length, seconds (0: not loaded). */
    public static float camera(ResourceLocation file, float power) {
        XrCameraMotion m = load(ANM, file, XrCameraMotion::parse);
        if (m == null) {
            return 0.0F;
        }
        CAMS.add(new Cam(m, now(), power));
        return m.length();
    }

    private interface Parser<T> {
        T parse(byte[] bytes);
    }

    private static <T> T load(Map<ResourceLocation, T> cache, ResourceLocation file, Parser<T> parser) {
        return cache.computeIfAbsent(file, f -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(f)) {
                return parser.parse(in.readAllBytes());
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("X-Ray effector {} not loaded", f, e);
                return null;
            }
        });
    }

    /** The effectors' sum now ({@code CCameraManager::UpdatePPEffectors} + {@code SPPInfo::lerp}); false: none. */
    static boolean sum(double t, float dt) {
        SUM.identity();
        boolean any = false;
        for (Iterator<Post> it = POSTS.values().iterator(); it.hasNext(); ) {
            Post p = it.next();
            float age = (float) (t - p.start);
            float len = p.effect.length();
            if (!p.loop && age > len) {
                it.remove();
                continue;
            }
            if (p.stopping) {
                p.factor -= dt * p.speed;
            } else {
                p.factor += p.speed * dt * (p.dest - p.factor);
            }
            p.factor = Math.clamp(p.factor, 0.0001F, 1.0F);
            if (p.stopping && p.factor <= 0.0001F) {
                it.remove();
                continue;
            }
            p.effect.sample(p.loop && len > 0.0F ? age % len : age, ONE);
            add(ONE, p.factor);
            any = true;
        }
        return any;
    }

    /** {@code SUM += lerp(identity, one, k) - identity}; noise takes the largest. */
    private static void add(XrPostEffect.Pp one, float k) {
        for (int i = 0; i < 3; i++) {
            SUM.base[i] += (one.base[i] - 0.5F) * k;
            SUM.add[i] += one.add[i] * k;
            SUM.grayColor[i] += (one.grayColor[i] - 0.333F) * k;
        }
        SUM.gray += one.gray * k;
        SUM.blur += one.blur * k;
        SUM.dualH += one.dualH * k;
        SUM.dualV += one.dualV * k;
        SUM.noise = Math.max(SUM.noise, one.noise);
        SUM.grain = Math.max(SUM.grain, one.grain);
        SUM.fps = Math.max(SUM.fps, one.fps);
    }

    /** After the level and hands are drawn ({@code MixinGameRendererXrScreen}). */
    public static void afterLevel(float partialTick) {
        double t = now();
        float dt = lastFrame == 0.0D ? 0.0F : (float) Math.min(0.1D, t - lastFrame);
        lastFrame = t;
        float strength = WFClientConfig.SCREEN_EFFECTS.get().floatValue();
        if (!sum(t, dt) || strength <= 0.0F) {
            return;
        }
        PostChain c = chain();
        if (c == null) {
            return;
        }
        if (t >= noiseNext) {
            noiseSeed = (float) Math.random() * 1000.0F;
            noiseNext = t + 1.0D / Math.max(1.0F, SUM.fps);
        }
        String[] rgb = {"R", "G", "B"};
        for (int i = 0; i < 3; i++) {
            c.setUniform("Base" + rgb[i], 0.5F + (SUM.base[i] - 0.5F) * strength);
            c.setUniform("Add" + rgb[i], SUM.add[i] * strength);
            c.setUniform("GrayColor" + rgb[i], SUM.grayColor[i]);
        }
        c.setUniform("Gray", Math.clamp(SUM.gray * strength, 0.0F, 1.0F));
        c.setUniform("Blur", SUM.blur * strength);
        c.setUniform("DualH", SUM.dualH * strength);
        c.setUniform("DualV", SUM.dualV * strength);
        c.setUniform("Noise", Math.clamp(SUM.noise * strength, 0.0F, 1.0F));
        c.setUniform("Grain", Math.max(SUM.grain, 0.01F));
        c.setUniform("Seed", noiseSeed);
        c.process(partialTick);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    private static PostChain chain() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
                chainWidth = -1;
            }
            if (main.width != chainWidth || main.height != chainHeight) {
                chain.resize(main.width, main.height);
                chainWidth = main.width;
                chainHeight = main.height;
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("X-Ray post chain {} not loaded", CHAIN, e);
            POSTS.clear();
            return null;
        }
        return chain;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (CAMS.isEmpty()) {
            return;
        }
        double t = now();
        float power = WFClientConfig.CAMERA_SHAKE.get().floatValue();
        float yaw = 0.0F;
        float pitch = 0.0F;
        float roll = 0.0F;
        for (Iterator<Cam> it = CAMS.iterator(); it.hasNext(); ) {
            Cam c = it.next();
            float age = (float) (t - c.start);
            if (age > c.motion.length()) {
                it.remove();
                continue;
            }
            // X-Ray applies setXYZi(R) on the camera: heading right, pitch down, bank as stored.
            c.motion.sample(age, CAM);
            yaw += (float) Math.toDegrees(CAM[3]) * c.power;
            pitch += (float) Math.toDegrees(CAM[4]) * c.power;
            roll += (float) Math.toDegrees(CAM[5]) * c.power;
        }
        event.setYaw(event.getYaw() + yaw * power);
        event.setPitch(event.getPitch() + pitch * power);
        event.setRoll(event.getRoll() + roll * power);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        POSTS.clear();
        CAMS.clear();
        if (chain != null) {
            chain.close();
            chain = null;
        }
    }
}
