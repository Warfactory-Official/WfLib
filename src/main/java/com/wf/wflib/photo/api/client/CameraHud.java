package com.wf.wflib.photo.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * The camera's shooting display, in the drone view's style (flat green text with a shadow on the screen edges, a small
 * centre cross, no panels): shots taken and whether the shutter is ready (top left), the lens field of view (top right),
 * zoom (bottom left), heading / pitch (bottom right). Drawn in a {@link #WIDTH} x {@link #HEIGHT} design space or any
 * bigger one (edge items stay on the edges, so a bigger space draws it smaller on the same frame).
 */
public final class CameraHud {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;

    private static final int GREEN = 0xDD00FF00;
    private static final int DIM = 0x9966FF99;
    private static final int AMBER = 0xDDFFAA00;
    private static final int MARGIN = 8;

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
}
