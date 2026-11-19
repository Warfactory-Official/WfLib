package com.wf.wflib.photo.api.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.Random;

/**
 * The camera's shooting display, in the drone view's style (flat green text with a shadow on the screen edges, a small
 * centre cross, no panels): shots taken and whether the shutter is ready (top left), the lens field of view (top right),
 * zoom (bottom left), heading / pitch (bottom right), inside a border (frame + corner brackets) over film grain, a
 * soft vignette and faint scanlines. Drawn in a {@link #WIDTH} x {@link #HEIGHT} design space or any
 * bigger one (edge items stay on the edges, so a bigger space draws it smaller on the same frame).
 */
public final class CameraHud {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;

    private static final int GREEN = 0xDD00FF00;
    private static final int DIM = 0x9966FF99;
    private static final int AMBER = 0xDDFFAA00;
    private static final int MARGIN = 8;
    private static final int GRAIN_SIZE = 512;
    private static final ResourceLocation GRAIN_ID = ResourceLocation.fromNamespaceAndPath("wflib", "camera_grain");
    private static final Random RANDOM = new Random();
    private static DynamicTexture grain;

    /** What the display shows; {@code ready}: the shutter is not cooling down. */
    public record Info(float fov, float zoom, float yawDegrees, float pitchDegrees, int shots, boolean ready) {
        /** Reads the local player; {@code zoom} 1 = the lens at its widest. */
        public static Info of(LocalPlayer player, float fov, float zoom, int shots) {
            boolean ready = !player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem());
            return new Info(fov, zoom, player.getYRot(), player.getXRot(), shots, ready);
        }
    }

    private CameraHud() {
    }

    public static void draw(GuiGraphics g, Info in) {
        draw(g, in, WIDTH, HEIGHT);
    }

    public static void draw(GuiGraphics g, Info in, int w, int h) {
        var font = Minecraft.getInstance().font;
        int cx = w / 2, cy = h / 2, line = font.lineHeight + 1;

        effects(g, w, h);
        border(g, w, h);

        g.drawString(font, String.format("PHOTO %04d", Math.min(9999, in.shots())), MARGIN, MARGIN, GREEN, true);
        g.drawString(font, in.ready() ? "READY" : "WAIT", MARGIN, MARGIN + line, in.ready() ? GREEN : AMBER, true);

        String fov = "FOV " + Math.round(in.fov());
        g.drawString(font, fov, w - MARGIN - font.width(fov), MARGIN, DIM, true);

        g.drawString(font, String.format("ZOOM x%.1f", in.zoom()), MARGIN, h - MARGIN - font.lineHeight, GREEN, true);

        String heading = String.format("%03d / %+03d", Math.floorMod(Math.round(in.yawDegrees()) + 180, 360),
                Math.round(-in.pitchDegrees()));
        g.drawString(font, heading, w - MARGIN - font.width(heading), h - MARGIN - font.lineHeight, GREEN, true);

        // centre cross with a gap, as the drone view's
        g.fill(cx - 8, cy, cx - 2, cy + 1, GREEN);
        g.fill(cx + 3, cy, cx + 9, cy + 1, GREEN);
        g.fill(cx, cy - 8, cx + 1, cy - 2, GREEN);
        g.fill(cx, cy + 3, cx + 1, cy + 9, GREEN);
    }

    /** Grain (a fresh window of a noise texture every draw), vignette, scanlines. */
    private static void effects(GuiGraphics g, int w, int h) {
        if (grain == null) {
            NativeImage image = new NativeImage(GRAIN_SIZE, GRAIN_SIZE, false);
            Random r = new Random(7);
            for (int y = 0; y < GRAIN_SIZE; y++) {
                for (int x = 0; x < GRAIN_SIZE; x++) {
                    float a = r.nextFloat();
                    int alpha = (int) (a * a * 50);
                    int v = r.nextBoolean() ? 0xFF : 0x00;
                    image.setPixelRGBA(x, y, (alpha << 24) | (v << 16) | (v << 8) | v);
                }
            }
            grain = new DynamicTexture(image);
            grain.setFilter(false, false);
            Minecraft.getInstance().getTextureManager().register(GRAIN_ID, grain);
        }
        int sw = Math.min(GRAIN_SIZE, w * 3 / 4), sh = Math.min(GRAIN_SIZE, h * 3 / 4);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(GRAIN_ID, 0, 0, w, h, RANDOM.nextInt(GRAIN_SIZE - sw + 1), RANDOM.nextInt(GRAIN_SIZE - sh + 1),
                sw, sh, GRAIN_SIZE, GRAIN_SIZE);

        // vignette: darker toward the edges, in steps
        int depth = Math.max(8, Math.min(w, h) / 6);
        for (int i = 0; i < depth; i += 2) {
            int a = (int) (70.0f * (1.0f - (float) i / depth) * (1.0f - (float) i / depth));
            int c = a << 24;
            g.fill(i, i, w - i, i + 2, c);
            g.fill(i, h - i - 2, w - i, h - i, c);
            g.fill(i, i + 2, i + 2, h - i - 2, c);
            g.fill(w - i - 2, i + 2, w - i, h - i - 2, c);
        }
        // scanlines
        for (int y = 0; y < h; y += 2) {
            g.fill(0, y, w, y + 1, 0x14000000);
        }
    }

    /** Thin frame with heavier corner brackets. */
    private static void border(GuiGraphics g, int w, int h) {
        int in = 3, arm = Math.max(10, Math.min(w, h) / 14);
        g.fill(in, in, w - in, in + 1, DIM);
        g.fill(in, h - in - 1, w - in, h - in, DIM);
        g.fill(in, in, in + 1, h - in, DIM);
        g.fill(w - in - 1, in, w - in, h - in, DIM);
        for (int sx = 0; sx < 2; sx++) {
            for (int sy = 0; sy < 2; sy++) {
                int x = sx == 0 ? in : w - in, y = sy == 0 ? in : h - in;
                int dx = sx == 0 ? 1 : -1, dy = sy == 0 ? 1 : -1;
                g.fill(Math.min(x, x + dx * arm), Math.min(y, y + 2 * dy), Math.max(x, x + dx * arm) + 1, Math.max(y, y + 2 * dy) + 1, GREEN);
                g.fill(Math.min(x, x + 2 * dx), Math.min(y, y + dy * arm), Math.max(x, x + 2 * dx) + 1, Math.max(y, y + dy * arm) + 1, GREEN);
            }
        }
    }
}
