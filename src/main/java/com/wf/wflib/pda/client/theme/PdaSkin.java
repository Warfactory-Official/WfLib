package com.wf.wflib.pda.client.theme;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.BoolProp;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;

/** Shared semantic chrome. Wrapping changes drawing only: child measurement and input rectangles are retained. */
public final class PdaSkin {
    public enum Role { AUTO, TAB, ROW, CHECKBOX, TOGGLE, ACTION, CARD }
    private static Role role = Role.AUTO;
    private static boolean selected;
    private PdaSkin() { }
    public static boolean active() { return PdaFont.active(); }
    public static WidgetDef role(Role role, String selected, WidgetDef child) { return new Styled(role, selected, child); }
    private record Styled(Role skinRole, String selection, WidgetDef child) implements WidgetDef {
        public Widget create(Binder binder) {
            Widget node = binder.child(child);
            BoolProp on = binder.bool(selection);
            return new Widget() {
                public void measure(UiEval ev) { node.measure(ev); width = node.width(); height = node.height(); }
                public void draw(UiEval ev, float x, float y, float w, float h) {
                    Role before = role; boolean wasSelected = selected;
                    role = skinRole; selected = on.get(ev);
                    try { node.draw(ev, x, y, w, h); }
                    finally { role = before; selected = wasSelected; }
                }
            };
        }
    }
    public static void panel(Canvas c, float x, float y, float w, float h, int back, int edge, float line) {
        if ((back >>> 24) != 0) c.fill(x, y, x + w, y + h, back);
        if ((edge >>> 24) != 0 && line > 0) c.frame(x, y, x + w, y + h, Math.min(1, line), edge);
        if (role == Role.CARD) c.fill(x + 1, y + 1, x + w - 1, y + 2, 0x337F8589);
    }
    public static void button(Canvas c, float x, float y, float w, float h, int back, int edge, float line,
                              boolean hover, boolean enabled, float inset) {
        // Transparent catchers and icon-only controls keep their original appearance and input behaviour.
        if ((back >>> 24) == 0 && (edge >>> 24) == 0) return;
        Role use = role;
        int ink = enabled ? PdaTheme.GAMMA.text() : PdaTheme.GAMMA.dim();
        if (use == Role.TAB) {
            int fill = selected ? 0xFF4B5053 : hover ? 0xFF353A3D : 0xB0202325;
            c.quad(x + 4, y, x + w, y, x + w - 4, y + h, x, y + h, fill);
            int outline = selected ? 0xFFE0E3E4 : 0xFF747B7F;
            c.line(x + 4, y + .5f, x + w - .5f, y + .5f, 1, outline);
            c.line(x + .5f, y + h - .5f, x + w - 4, y + h - .5f, 1, outline);
            c.line(x + .5f, y + h - .5f, x + 4, y + .5f, 1, outline);
            c.line(x + w - 4, y + h - .5f, x + w - .5f, y + .5f, 1, outline);
        } else if (use == Role.ROW) {
            if (hover || selected) c.fill(x, y, x + w, y + h, selected ? 0xFF444A4D : 0xFF313639);
            c.fill(x, y + h - .5f, x + w, y + h, 0x707A8084);
            // Four units were already reserved as row padding; no text space is taken away.
            c.roundFrame(x + .5f, y + h / 2 - 1.25f, x + 3, y + h / 2 + 1.25f, 1.25f, .5f, ink);
            c.line(x + w - 3, y + h / 2 - 1.5f, x + w - 1.5f, y + h / 2, .5f, ink);
            c.line(x + w - 1.5f, y + h / 2, x + w - 3, y + h / 2 + 1.5f, .5f, ink);
        } else {
            panel(c, x, y, w, h, back, edge, line);
            if (use == Role.TOGGLE || use == Role.CHECKBOX) {
                float cy = y + h / 2;
                if (inset < 10) {
                    c.roundFrame(x + .5f, cy - 1.25f, x + 3, cy + 1.25f, 1.25f, .5f, ink);
                    if (selected) c.fill(x + 1, cy - .5f, x + 2.5f, cy + .5f, ink);
                } else if (use == Role.CHECKBOX) {
                    c.frame(x + 3, cy - 3, x + 9, cy + 3, .7f, ink);
                    if (selected) {
                        c.line(x + 4, cy, x + 6, cy + 2, .8f, ink);
                        c.line(x + 6, cy + 2, x + 8, cy - 2, .8f, ink);
                    }
                } else {
                    c.roundFrame(x + 3, cy - 3, x + 9, cy + 3, 3, .7f, ink);
                    if (selected) c.roundRect(x + 4.5f, cy - 1.5f, x + 7.5f, cy + 1.5f, 1.5f, ink);
                }
            }
            if (hover && line > 0) c.frame(x, y, x + w, y + h, 1, ink);
        }
    }
    /** Same two-unit gutter as classic. Arrow end targets are registered by the shared scrollbar. */
    public static void scroll(Canvas c, float x, float y, float h, float thumbY, float thumbH) {
        c.fill(x, y, x + 2, y + h, 0xFF171A1C);
        c.line(x, y + 3, x + 1, y + 1, .5f, 0xFFCBD0D2);
        c.line(x + 1, y + 1, x + 2, y + 3, .5f, 0xFFCBD0D2);
        c.line(x, y + h - 3, x + 1, y + h - 1, .5f, 0xFFCBD0D2);
        c.line(x + 1, y + h - 1, x + 2, y + h - 3, .5f, 0xFFCBD0D2);
        c.fill(x + .25f, thumbY, x + 1.75f, thumbY + thumbH, 0xFFADB3B7);
    }
}
