package com.wf.wflib.pda.client.theme;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.pda.api.client.PdaPopup;
import com.wf.wflib.pda.api.client.PdaText;
import com.wf.wflib.pda.api.client.PdaThemeState;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.UiSkin;
import com.wf.wflib.ui.api.theme.PdaFont;
import com.wf.wflib.ui.api.theme.PdaTheme;

/** The PDA's chrome on the shared widgets; installed by {@code PdaSystem}. */
public final class PdaUiSkin implements UiSkin {
    @Override
    public boolean active() {
        return PdaFont.active();
    }

    @Override
    public boolean inPda() {
        return PdaThemeState.inPda();
    }

    @Override
    public PdaTheme theme() {
        return PdaThemeState.theme();
    }

    @Override
    public void panel(Canvas c, float x, float y, float w, float h, int back, int edge, float line) {
        PdaSkin.panel(c, x, y, w, h, back, edge, line);
    }

    @Override
    public void button(Canvas c, float x, float y, float w, float h, int back, int edge, float line, boolean hover,
                       boolean enabled, float inset) {
        PdaSkin.button(c, x, y, w, h, back, edge, line, hover, enabled, inset);
    }

    @Override
    public void scroll(Canvas c, float x, float y, float h, float thumbY, float thumbH) {
        PdaSkin.scroll(c, x, y, h, thumbY, thumbH);
    }

    @Override
    public void popup(String title, String stamp, String body) {
        PdaPopup.show(title, stamp, body);
    }

    @Override
    public void textLine(UiEval ev, String value, float x, float y, float width, int colour) {
        PdaText.line(ev, value, x, y, width, colour);
    }
}
