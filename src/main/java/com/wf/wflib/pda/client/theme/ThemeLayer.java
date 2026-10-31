package com.wf.wflib.pda.client.theme;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.pda.api.client.PdaThemeState;
import com.wf.wflib.ui.api.theme.PdaTheme;

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
                PdaEffects.Frame frame = PdaEffects.frame(t, PdaThemeState.effects(), PdaThemeState.scanlines(),
                        PdaThemeState.vignette(), PdaThemeState.noise(), PdaThemeState.flicker(), time);
                if (frame.vignette() > 0) vignette(c, frame.vignette());
                if (frame.scanlines() > 0) scanlines(c, frame.scanlines());
                if (frame.noise() > 0) noise(c, frame.noise(), time);
                if (frame.flicker() > 0) c.fill(0, 0, W, H, alpha(0, frame.flicker()));
                if (PdaThemeState.effects() && t.bezel() != 0) bezel(c, t);
            }
        };
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
