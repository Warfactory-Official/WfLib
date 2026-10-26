package com.wf.wflib.pda.client.theme;

import com.wf.gemrender.ui.Canvas;

/** Chamfered rectangle drawn over the ordinary box: the same rectangle for layout and hits, only the corners differ. */
public final class Chamfer {
    private Chamfer() {
    }

    /** Active theme's corner cut in units, limited so small boxes stay boxes. */
    public static float cut(float w, float h) {
        int k = PdaThemeState.theme().chamfer();
        return k <= 0 ? 0 : Math.min(k, Math.min(w, h) / 4);
    }

    public static void fill(Canvas c, float x, float y, float x1, float y1, float k, int argb) {
        c.fill(x + k, y, x1 - k, y1, argb);
        c.fill(x, y + k, x + k, y1 - k, argb);
        c.fill(x1 - k, y + k, x1, y1 - k, argb);
        c.triangle(x, y + k, x + k, y, x + k, y + k, argb);
        c.triangle(x1, y + k, x1 - k, y, x1 - k, y + k, argb);
        c.triangle(x, y1 - k, x + k, y1, x + k, y1 - k, argb);
        c.triangle(x1, y1 - k, x1 - k, y1, x1 - k, y1 - k, argb);
    }

    public static void frame(Canvas c, float x, float y, float x1, float y1, float k, float w, int argb) {
        float[] p = {x + k, y, x1 - k, y, x1, y + k, x1, y1 - k, x1 - k, y1, x + k, y1, x, y1 - k, x, y + k};
        c.polyline(p, 8, w, true, argb);
    }
}
