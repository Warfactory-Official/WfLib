package com.wf.wflib.suppression.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wf.wflib.cam.api.client.FeedPass;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.geom.api.Segments;
import com.wf.wflib.round.api.client.ClientRounds;
import com.wf.wflib.round.api.client.RoundObserver;
import com.wf.wflib.suppression.api.Suppression;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * {@link Suppression} level -> world-only dual Kawase blur + vignette at {@code AFTER_LEVEL} (hand and HUD drawn
 * after, sharp; GUI-stage post effects of other mods compose over it).
 */
public final class SuppressionFx implements RoundObserver {
    /** Ticks at full level before decay; per-tick decay factor after. */
    static final int HOLD_TICKS = 20;
    static final float DECAY = 0.93F;
    static final float BLUR_ALPHA = 0.9F;
    static final float VIGNETTE = 0.55F;

    private static float level;
    private static float levelO;
    private static int sinceHit = Integer.MAX_VALUE;
    @Nullable
    private static ShaderInstance down;
    @Nullable
    private static ShaderInstance up;
    @Nullable
    private static ShaderInstance vignette;
    @Nullable
    private static RenderTarget half;
    @Nullable
    private static RenderTarget quarter;

    private SuppressionFx() {
    }

    public static void register(IEventBus modBus) {
        ClientRounds.OBSERVERS.register(id("suppression"), 0, new SuppressionFx());
        modBus.addListener(SuppressionFx::onRegisterShaders);
        NeoForge.EVENT_BUS.addListener(SuppressionFx::onTick);
        NeoForge.EVENT_BUS.addListener(SuppressionFx::onRenderLevel);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static float level(float pt) {
        return Mth.lerp(pt, levelO, level);
    }

    public static void add(float k) {
        level = Math.min(1, level + k);
        sinceHit = 0;
    }

    @Override
    public void tick(ClientRounds.Round r) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 closest = Segments.closest(r.previous(), r.position(), eye);
        float k = Suppression.nearMiss(p.getId(), r.shooterId(), r.resting(), closest.distanceTo(eye),
                r.velocity().length());
        if (k <= 0 || mc.level.clip(new ClipContext(eye, closest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.BLOCK) {
            return;
        }
        Suppression.add(k);
    }

    private static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || Suppression.muted()) {
            level = levelO = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        levelO = level;
        if (sinceHit < HOLD_TICKS) {
            sinceHit++;
        } else {
            level = level < 0.005F ? 0 : level * DECAY;
        }
    }

    private static void onRegisterShaders(RegisterShadersEvent e) {
        var fmt = DefaultVertexFormat.POSITION;
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), id("suppress_down"), fmt), s -> down = s);
            e.registerShader(new ShaderInstance(e.getResourceProvider(), id("suppress_up"), fmt), s -> up = s);
            e.registerShader(new ShaderInstance(e.getResourceProvider(), id("suppress_vignette"), fmt),
                    s -> vignette = s);
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }

    private static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || down == null || FeedPass.active()
                || Suppression.muted()) {
            return;
        }
        float s = level(e.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (s < 0.01F) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        half = fit(half, main.width / 2, main.height / 2);
        quarter = fit(quarter, main.width / 4, main.height / 4);
        float offset = 1 + 2 * s;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        pass(down, main.getColorTextureId(), main.width, main.height, half, offset, 1);
        pass(down, half.getColorTextureId(), half.width, half.height, quarter, offset, 1);
        pass(up, quarter.getColorTextureId(), quarter.width, quarter.height, half, offset, 1);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE);
        pass(up, half.getColorTextureId(), half.width, half.height, main, offset, s * BLUR_ALPHA);
        vignette.safeGetUniform("Strength").set(s * VIGNETTE);
        draw(vignette);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static RenderTarget fit(@Nullable RenderTarget t, int w, int h) {
        w = Math.max(w, 1);
        h = Math.max(h, 1);
        if (t == null) {
            t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        } else if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        }
        return t;
    }

    private static void pass(ShaderInstance sh, int src, int srcW, int srcH, RenderTarget dst, float offset,
                             float alpha) {
        dst.bindWrite(true);
        sh.setSampler("DiffuseSampler", src);
        sh.safeGetUniform("HalfPixel").set(0.5F / srcW, 0.5F / srcH);
        sh.safeGetUniform("Offset").set(offset);
        sh.safeGetUniform("Alpha").set(alpha);
        draw(sh);
    }

    private static void draw(ShaderInstance sh) {
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(0, 0, 0);
        b.addVertex(1, 0, 0);
        b.addVertex(1, 1, 0);
        b.addVertex(0, 1, 0);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
