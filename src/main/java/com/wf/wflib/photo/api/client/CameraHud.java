package com.wf.wflib.photo.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;

/**
 * The camera's shooting display, drawn in a fixed {@link #WIDTH} x {@link #HEIGHT} space (scale the pose to fit a
 * bigger area): mode, size, shots and battery on top; framing grid, AF frame and an electronic level (a green horizon
 * that follows pitch, level when it meets the fixed ticks) in the middle; exposure meter, shutter, aperture, ISO,
 * audio levels and a zoom scale at the bottom. Values come from the game: the light at the player sets ISO, shutter
 * and the exposure pointer; pitch moves the horizon; zoom drives the lens readout.
 */
public final class CameraHud {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xB0FFFFFF;
    private static final int GRID = 0x38FFFFFF;
    private static final int GREEN = 0xFF4CE070;
    private static final int RED = 0xFFD8302A;
    private static final int AMBER = 0xFFFFC040;
    private static final int PANEL = 0x78000000;

    /** What the display shows. {@code light} 0..15, {@code speed} 0..1 (how fast the player moves: the audio meter). */
    public record Info(float fov, float zoom, float zoomMax, float pitchDegrees, int light, int shots, float battery,
                       float speed, String mode) {
        /** Reads the local player's surroundings; {@code zoom} 1 = the lens at its widest. */
        public static Info of(LocalPlayer player, float fov, float zoom, float zoomMax, int shots) {
            int light = Math.max(player.level().getBrightness(LightLayer.BLOCK, player.blockPosition()),
                    player.level().getBrightness(LightLayer.SKY, player.blockPosition())
                            - player.level().getSkyDarken());
            double speed = player.getDeltaMovement().horizontalDistance();
            return new Info(fov, zoom, zoomMax, player.getXRot(), Mth.clamp(light, 0, 15), shots, 1.0f,
                    (float) Math.min(1.0, speed * 5.0), "P");
        }
    }

    private CameraHud() {
    }

    /** ISO for a light level: 100 in daylight, a stop more for every 2.5 levels darker, to 6400. */
    static int iso(int light) {
        int stops = Math.round((15 - light) * 0.4f);
        return Math.min(6400, 100 << Math.max(0, Math.min(6, stops)));
    }

    public static void draw(GuiGraphics g, Info in) {
        var font = Minecraft.getInstance().font;
        int w = WIDTH, h = HEIGHT, cx = w / 2, cy = h / 2;

        // framing grid
        for (int i = 1; i < 3; i++) {
            g.fill(w * i / 3, 0, w * i / 3 + 1, h, GRID);
            g.fill(0, h * i / 3, w, h * i / 3 + 1, GRID);
        }
        // top and bottom panels
        g.fill(0, 0, w, 15, PANEL);
        g.fill(0, h - 16, w, h, PANEL);

        // ---- top row: mode, size, shots, battery
        g.fill(3, 3, 16, 12, RED);
        g.drawString(font, in.mode(), 7, 4, WHITE, false);
        g.drawString(font, "L", 22, 4, WHITE, false);
        g.fill(31, 4, 32, 11, DIM);
        g.drawString(font, "16:9", 36, 4, DIM, false);
        // metering icon: a ring with a dot
        ring(g, 66, 4, 8, WHITE);
        g.fill(69, 7, 71, 9, WHITE);
        String shots = String.format("%04d", Math.min(9999, in.shots()));
        g.drawString(font, shots, w - 44 - font.width(shots) + 26, 4, WHITE, false);
        battery(g, w - 20, 4, in.battery());

        // ---- AF frame
        int fw = 22, fh = 13, arm = 6;
        corner(g, cx - fw, cy - fh, arm, 1, 1, WHITE);
        corner(g, cx + fw, cy - fh, arm, -1, 1, WHITE);
        corner(g, cx - fw, cy + fh, arm, 1, -1, WHITE);
        corner(g, cx + fw, cy + fh, arm, -1, -1, WHITE);
        g.fill(cx - 1, cy, cx + 1, cy + 1, DIM);

        // ---- electronic level: horizon follows pitch, fixed ticks at the centre line
        double half = Math.tan(Math.toRadians(in.fov() * 0.5));
        double horizon = cy - Math.tan(Math.toRadians(in.pitchDegrees())) / half * (h * 0.5);
        int hy = (int) Mth.clamp(horizon, 18, h - 20);
        boolean level = Math.abs(hy - cy) <= 1;
        int lc = level ? GREEN : AMBER;
        g.fill(cx - 62, hy, cx - 18, hy + 1, lc);
        g.fill(cx + 18, hy, cx + 62, hy + 1, lc);
        g.fill(cx - 78, cy, cx - 66, cy + 1, WHITE);
        g.fill(cx + 66, cy, cx + 78, cy + 1, WHITE);
        // curved brackets either side of the horizon, as short chamfered bars
        for (int side = -1; side <= 1; side += 2) {
            int x = cx + side * 34;
            g.fill(x, hy - 12, x + 1, hy + 12, DIM);
            g.fill(x - side, hy - 14, x - side + 1, hy - 12, DIM);
            g.fill(x - side, hy + 12, x - side + 1, hy + 14, DIM);
            g.fill(x + side * 6, hy - 9, x + side * 6 + 2, hy + 9, level ? GREEN : DIM);
        }

        // ---- bottom row: audio, exposure triplet, exposure meter
        int by = h - 12;
        for (int row = 0; row < 2; row++) {
            int lit = (int) (in.speed() * 12 + (row == 0 ? 0 : -1) + 1.5 * Math.sin(System.nanoTime() * 1e-8 + row));
            for (int i = 0; i < 12; i++) {
                g.fill(4 + i * 3, by + row * 4, 6 + i * 3, by + row * 4 + 2, i < lit ? (i > 9 ? RED : GREEN) : 0x40FFFFFF);
            }
        }
        int iso = iso(in.light());
        double shutter = Math.max(30.0, 500.0 / (iso / 100.0));
        double aperture = 3.3 + (in.zoom() - 1) / Math.max(1, in.zoomMax() - 1) * 3.6;
        g.drawString(font, "1/" + (int) shutter, 46, by, WHITE, false);
        g.drawString(font, String.format("F%.1f", aperture), 84, by, WHITE, false);
        g.drawString(font, "ISO " + iso, 118, by, WHITE, false);
        // exposure meter, centre aligned on the right of the readouts
        int mx = 178, my = by + 7;
        for (int i = -3; i <= 3; i++) {
            g.fill(mx + i * 6, my - (i == 0 ? 4 : 2), mx + i * 6 + 1, my, DIM);
        }
        g.fill(mx - 18, my, mx + 19, my + 1, DIM);
        double ev = Mth.clamp((in.light() - 10) / 5.0 * 0.6, -3.0, 3.0);
        int px = mx + (int) Math.round(ev * 6);
        g.fill(px - 1, my + 2, px + 2, my + 3, AMBER);
        g.fill(px, my + 1, px + 1, my + 4, AMBER);

        // ---- zoom readout and scale
        g.drawString(font, String.format("x%.1f", in.zoom()), w - 30, by, WHITE, false);
        int zx = w - 8, zt = 28, zb = h - 30;
        g.fill(zx, zt, zx + 1, zb, DIM);
        for (int i = 0; i <= 4; i++) {
            int y = zt + (zb - zt) * i / 4;
            g.fill(zx - (i % 2 == 0 ? 3 : 1), y, zx + 1, y + 1, DIM);
        }
        float t = (in.zoom() - 1) / Math.max(1e-3f, in.zoomMax() - 1);
        int zy = zb - (int) ((zb - zt) * Mth.clamp(t, 0, 1));
        g.fill(zx - 5, zy - 1, zx + 3, zy + 1, WHITE);
    }

    private static void corner(GuiGraphics g, int x, int y, int arm, int dx, int dy, int c) {
        g.fill(Math.min(x, x + dx * arm), y, Math.max(x, x + dx * arm) + 1, y + 1, c);
        g.fill(x, Math.min(y, y + dy * arm), x + 1, Math.max(y, y + dy * arm) + 1, c);
    }

    private static void ring(GuiGraphics g, int x, int y, int size, int c) {
        g.fill(x + 2, y, x + size - 2, y + 1, c);
        g.fill(x + 2, y + size - 1, x + size - 2, y + size, c);
        g.fill(x, y + 2, x + 1, y + size - 2, c);
        g.fill(x + size - 1, y + 2, x + size, y + size - 2, c);
    }

    private static void battery(GuiGraphics g, int x, int y, float level) {
        g.fill(x, y, x + 15, y + 1, WHITE);
        g.fill(x, y + 7, x + 15, y + 8, WHITE);
        g.fill(x, y, x + 1, y + 8, WHITE);
        g.fill(x + 14, y, x + 15, y + 8, WHITE);
        g.fill(x + 15, y + 2, x + 17, y + 6, WHITE);
        int segs = Math.max(1, Math.round(level * 3));
        for (int i = 0; i < 3; i++) {
            g.fill(x + 2 + i * 4, y + 2, x + 5 + i * 4, y + 6, i < segs ? (level < 0.34f ? RED : GREEN) : 0x30FFFFFF);
        }
    }
}
