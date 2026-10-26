package com.wf.wflib.pda.client.theme;

import com.wf.gemrender.ui.Canvas;
import java.util.List;

/** Common translucent disclosure popup for WFLib and external PDA pages. Caller supplies measured, fitted lines. */
public final class PdaPopup {
    public static final float HEADER = 14;
    private static String pendingTitle, pendingStamp, pendingBody;
    private PdaPopup() { }
    public static void show(String title, String stamp, String body) {
        pendingTitle = title; pendingStamp = stamp; pendingBody = body;
    }
    public static com.wf.wflib.ui.WidgetDef layer() {
        return binder -> new com.wf.wflib.ui.Widget() {
            public void measure(com.wf.wflib.ui.UiEval ev) { width = height = 0; }
            public void draw(com.wf.wflib.ui.UiEval ev, float x, float y, float w, float h) {
                if (pendingBody == null) return;
                String title = pendingTitle, stamp = pendingStamp, body = pendingBody;
                pendingBody = null;
                if (!PdaSkin.active() || PdaThemeState.instance().isMenuOpen()) return;
                var c = ev.canvas;
                var lines = PdaControls.wrap(c, body, 290, 20);
                float width = 300, height = HEADER + 6 + lines.size() * c.lineHeight();
                float px = Math.clamp(com.wf.wflib.pda.client.PdaClient.cursorX() + 8, 4, 380 - width);
                float py = Math.clamp(com.wf.wflib.pda.client.PdaClient.cursorY() + 12, 20, 236 - height);
                PdaPopup.draw(c, px, py, width, height, title, stamp, lines);
            }
        };
    }
    public static void draw(Canvas c, float x, float y, float w, float h, String title, String stamp, List<String> lines) {
        c.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x60000000);
        c.fill(x, y, x + w, y + h, 0xED303538);
        c.frame(x, y, x + w, y + h, .7f, 0xFF9CA5AA);
        c.roundFrame(x + 4, y + 4, x + 10, y + 10, 3, .7f, 0xFFCCD2D5);
        c.text("i", x + 6, y + 3, 0xFFE1E5E6, false);
        c.text(title, x + 14, y + 3, 0xFFE1E5E6, false);
        if (!stamp.isEmpty()) c.text(stamp, x + w - c.width(stamp) - 4, y + 3, 0xFFA4ADB3, false);
        c.fill(x + 4, y + HEADER - 1, x + w - 4, y + HEADER - .5f, 0x707D878D);
        for (int i = 0; i < lines.size(); i++) c.text(lines.get(i), x + 4, y + HEADER + 3 + i * c.lineHeight(), 0xFFDADCDD, false);
    }
}
