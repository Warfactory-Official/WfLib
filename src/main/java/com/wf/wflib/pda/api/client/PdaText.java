package com.wf.wflib.pda.api.client;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.UiEval;

/** Font-measured bounds for custom canvas app renderers, with full-text disclosure rather than silent clipping. */
public final class PdaText {
    private PdaText() { }
    public static String fit(Canvas c, String value, float width) {
        if (c.width(value) <= width) return value;
        String suffix = "...";
        while (!suffix.isEmpty() && c.width(suffix) > width) suffix = suffix.substring(1);
        int keep = value.length();
        while (keep > 0 && c.width(value, 0, keep) + c.width(suffix) > width) keep = value.offsetByCodePoints(keep, -1);
        return value.substring(0, keep) + suffix;
    }
    public static void line(UiEval ev, String value, float x, float y, float width, int colour) {
        String shown = fit(ev.canvas, value, Math.max(0, width));
        ev.canvas.text(shown, x, y, colour, false);
        if (!shown.equals(value) && ev.input != null && ev.input.over(x, y, x + width, y + ev.canvas.lineHeight()))
            PdaPopup.show("DETAILS", "", value);
    }
}
