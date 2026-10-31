package com.wf.wflib.pda.client.theme;

import com.wf.wflib.ui.api.theme.PdaTheme;

/** Independent, bounded screen effects. Preferences and palette are separate. */
public final class PdaEffects {
    private PdaEffects() { }
    public record Frame(float scanlines, float vignette, float noise, float flicker) { }
    public static Frame frame(PdaTheme theme, boolean master, boolean scanlines, boolean vignette,
                              boolean noise, boolean flicker, double time) {
        if (!master) return new Frame(0, 0, 0, 0);
        // Slow, low-amplitude luminance drift: no flashes, blank frames or text movement.
        float wave = (float) (.5 + .5 * Math.sin(time * 3.1) * Math.sin(time * .73));
        return new Frame(scanlines ? Math.clamp(theme.scanlines(), .055f, PdaTheme.MAX_SCANLINES) : 0,
                vignette ? Math.clamp(theme.vignette(), .12f, PdaTheme.MAX_VIGNETTE) : 0,
                noise ? Math.clamp(theme.noise(), .015f, PdaTheme.MAX_NOISE) : 0,
                flicker ? Math.clamp(theme.flicker(), .018f, PdaTheme.MAX_FLICKER) * wave : 0);
    }
}
