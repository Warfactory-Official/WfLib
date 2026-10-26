package com.wf.wflib.pda.client.theme;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.pda.client.PdaShell;
import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;

/**
 * Last layer of the PDA surface (under the cursor): hazard strip, bezel, scanlines, vignette and noise. Draws only decoration over fixed rectangles; strengths are capped by {@link PdaTheme}.
 */
public record ThemeLayer() implements WidgetDef {
    private static final float W = PdaShell.WIDTH;
    private static final float H = PdaShell.HEIGHT;
    private static final float RIM = 3;

    @Override
    public Widget create(Binder binder) {
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                width = 0;
                height = 0;
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                Canvas c = ev.canvas;
                PdaTheme t = PdaThemeState.theme();
                double time = ev.time();
                if (t.hazard()) {
                    hazard(c, t);
                }
                if (PdaThemeState.effects()) {
                    float flick = PdaThemeState.flicker() ? 1 - PdaTheme.MAX_FLICKER * Math.max(t.flicker() / 0.04f, 1) * pulse(time) : 1;
                    if (PdaThemeState.vignette() && t.vignette() > 0) {
                        vignette(c, Math.min(t.vignette(), PdaTheme.MAX_VIGNETTE) * flick);
                    }
                    if (PdaThemeState.scanlines() && t.scanlines() > 0) {
                        scanlines(c, Math.min(t.scanlines(), PdaTheme.MAX_SCANLINES) * flick);
                    }
                    if (PdaThemeState.noise() && t.noise() > 0) {
                        noise(c, Math.min(t.noise(), PdaTheme.MAX_NOISE), time);
                    }
                    if (t.bezel() != 0) {
                        bezel(c, t);
                    }
                }
            }
        };
    }

    /** 0..1 spiky pulse, about 7 Hz with a slow beat, from the layout clock. */
    private static float pulse(double time) {
        double a = Math.sin(time * 43) * Math.sin(time * 7.3);
        return (float) Math.max(0, a);
    }

    private static int alpha(int argb, float a) {
        int alpha = Math.clamp(Math.round(a * 255), 0, 255);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static void scanlines(Canvas c, float a) {
        int color = alpha(0x000000, a);
        for (float y = 1; y < H; y += 2) {
            c.fill(0, y, W, y + 1, color);
        }
    }

    private static void vignette(Canvas c, float a) {
        int dark = alpha(0x000000, a);
        int clear = 0x00000000;
        float band = 38;
        c.gradient(0, 0, W, band, dark, clear);
        c.gradient(0, H - band, W, H, clear, dark);
        // Sides in steps: the canvas gradient is vertical only.
        for (int i = 0; i < 12; i++) {
            int step = alpha(0x000000, a * (1 - i / 12f) * 0.8f);
            c.fill(i * 3, 0, i * 3 + 3, H, step);
            c.fill(W - i * 3 - 3, 0, W - i * 3, H, step);
        }
    }

    private static void noise(Canvas c, float a, double time) {
        long seed = (long) (time * 24) * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 46; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float px = (seed >>> 33) % (int) W;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float py = (seed >>> 33) % (int) H;
            int v = (int) ((seed >>> 20) & 0x7F) + 0x60;
            c.fill(px, py, px + 1, py + 1, alpha((v << 16) | (v << 8) | v, a * 3));
        }
    }

    private static void hazard(Canvas c, PdaTheme t) {
        float y0 = PdaShell.BAR - 3;
        float y1 = PdaShell.BAR - 1;
        int orange = alpha(t.hi(), 0.9f);
        int dark = alpha(t.bar(), 1f);
        c.fill(0, y0, W, y1, dark);
        for (float x = -2; x < W; x += 6) {
            c.quad(x, y1, x + 3, y1, x + 5, y0, x + 2, y0, orange);
        }
    }

    private static void bezel(Canvas c, PdaTheme t) {
        int rim = t.bezel();
        c.fill(0, 0, W, RIM, rim);
        c.fill(0, H - RIM, W, H, rim);
        c.fill(0, RIM, RIM, H - RIM, rim);
        c.fill(W - RIM, RIM, W, H - RIM, rim);
        c.frame(RIM, RIM, W - RIM, H - RIM, 1, 0xA0000000);
        float k = 7;
        c.triangle(RIM, RIM, RIM + k, RIM, RIM, RIM + k, rim);
        c.triangle(W - RIM, RIM, W - RIM - k, RIM, W - RIM, RIM + k, rim);
        c.triangle(RIM, H - RIM, RIM + k, H - RIM, RIM, H - RIM - k, rim);
        c.triangle(W - RIM, H - RIM, W - RIM - k, H - RIM, W - RIM, H - RIM - k, rim);
        int screw = t.bezelScrew();
        for (float[] p : new float[][]{{1.5f, 1.5f}, {W - 1.5f, 1.5f}, {1.5f, H - 1.5f}, {W - 1.5f, H - 1.5f}}) {
            c.fill(p[0] - 0.75f, p[1] - 0.75f, p[0] + 0.75f, p[1] + 0.75f, screw);
        }
    }

}
