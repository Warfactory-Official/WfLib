package com.wf.wflib.photo.api.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import com.wf.wflib.cam.api.client.OffscreenView;
import com.wf.wflib.core.api.WFLib;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The live picture on a camera's back screen: what the lens sees, drawn into a small texture each frame while
 * something asks for it. A camera model binds {@link #texture()} to its {@code screen} texture slot (emissive) and
 * calls {@link #request} every frame it draws that screen; a frame without a request releases the render again.
 */
public final class CameraScreen {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo/live_screen");
    private static final long ALIVE_NANOS = 250_000_000L;

    /** The texture manager entry; its GL id follows the render target's. */
    private static final class Alias extends AbstractTexture {
        void adopt(int glId) {
            this.id = glId;
        }

        @Override
        public void load(ResourceManager manager) {
            // the id is borrowed from the render target
        }

        @Override
        public void releaseId() {
            this.id = -1; // never delete: the render target owns it
        }

        @Override
        public int getId() {
            return this.id;
        }
    }

    private static final Alias ALIAS = new Alias();
    private static boolean registered;
    @Nullable
    private static TextureTarget target;
    @Nullable
    private static OffscreenView view;
    private static long requested = Long.MIN_VALUE / 2;
    private static long rendered = Long.MIN_VALUE / 2;
    private static float fov = 70.0f;
    private static float zoom = 1.0f;
    private static float zoomMax = 4.0f;

    private CameraScreen() {
    }

    /** Asks for a live picture at vertical field of view {@code fovDegrees}; call each frame the screen is drawn. */
    public static void request(float fovDegrees) {
        requested = Util.getNanos();
        fov = fovDegrees;
    }

    /** What the display's lens readout shows; call along with {@link #request}. */
    public static void readout(float zoomNow, float zoomMaxNow) {
        zoom = zoomNow;
        zoomMax = zoomMaxNow;
    }

    /** Photographs in the local player's gallery (the display's shot counter). */
    public static int shots() {
        return com.wf.wflib.photo.client.PhotoClient.rows().size();
    }

    /** The texture to bind to the screen slot. */
    public static ResourceLocation texture() {
        return TEXTURE;
    }

    /** True once a frame has been drawn into {@link #texture()} recently: before that, bind a still image instead. */
    public static boolean live() {
        return Util.getNanos() - rendered < ALIVE_NANOS * 2;
    }

    /** Internal: {@code RenderFrameEvent.Pre}. */
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (mc.level == null || p == null || Util.getNanos() - requested > ALIVE_NANOS) {
            release();
            return;
        }
        if (target == null) {
            target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
            target.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            target.setFilterMode(9728); // GL_NEAREST: chunky pixels, like the rest of the model
            view = OffscreenView.sharedGrid();
            if (!registered) {
                mc.getTextureManager().register(TEXTURE, ALIAS);
                registered = true;
            }
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 eye = p.getEyePosition(pt);
        if (view.render(eye, p.getViewYRot(pt), p.getViewXRot(pt), 0.0f, fov, target, event.getPartialTick())) {
            drawHud(mc, p);
            ALIAS.adopt(target.getColorTextureId());
            rendered = Util.getNanos();
        }
    }

    /** The shooting display, drawn over the picture with the game's own GUI code into the same target. */
    private static void drawHud(Minecraft mc, LocalPlayer p) {
        Matrix4f saved = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        target.bindWrite(true);
        // The level clears with alpha 0 and leaves it wherever neither sky nor terrain draws (the seam at the horizon);
        // make the picture opaque before the HUD blends into it.
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
        RenderSystem.clear(16384, Minecraft.ON_OSX);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, WIDTH, HEIGHT, 0.0f, 1000.0f, 21000.0f),
                VertexSorting.ORTHOGRAPHIC_Z);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0.0f, 0.0f, -11000.0f);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        CameraHud.draw(g, CameraHud.Info.of(p, fov, zoom, zoomMax, shots()));
        g.flush();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(saved, sorting);
        RenderSystem.enableDepthTest();
        mc.getMainRenderTarget().bindWrite(true);
    }

    private static void release() {
        if (target != null) {
            ALIAS.adopt(-1);
            view.close();
            target.destroyBuffers();
            target = null;
            view = null;
        }
    }
}
