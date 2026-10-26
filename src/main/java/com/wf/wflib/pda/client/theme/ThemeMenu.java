package com.wf.wflib.pda.client.theme;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.config.WFClientConfig;
import com.wf.wflib.pda.client.PdaShell;
import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;
import com.wf.wflib.ui.input.UiAction;
import com.wf.wflib.ui.widget.Button;
import com.wf.wflib.ui.widget.Point;
import net.neoforged.neoforge.common.ModConfigSpec;

import static com.wf.wflib.ui.Ui.at;
import static com.wf.wflib.ui.Ui.rect;
import static com.wf.wflib.ui.Ui.stack;
import static com.wf.wflib.ui.Ui.text;
import static com.wf.wflib.ui.Ui.when;

/** The gear in the top bar and the settings panel it opens. Shell-level, so it works on every page. */
public final class ThemeMenu {
    private static final float GEAR_X = 322;
    private static final float GEAR_Y = 2;
    private static final float PANEL_X = 190;
    private static final float PANEL_Y = PdaShell.BAR + 2;
    private static final float PANEL_W = 188;
    private static final float ROW = 14;

    private ThemeMenu() {
    }

    private static final String T = PdaThemeState.Token.TEXT.expr();
    private static final String ACC = PdaThemeState.Token.ACCENT.expr();

    private static Button button(UiAction action, float w, float h, WidgetDef child) {
        return new Button(item -> {
            PdaThemeState.click();
            action.run(item);
        }, "true", PdaThemeState.Token.PANEL.expr(), PdaThemeState.Token.HOVER.expr(), PdaThemeState.Token.PRESS.expr(),
                PdaThemeState.Token.EDGE.expr(), 1, 0, 0, 0, w, h, Point.CENTER, child);
    }

    /** Value button that flips one switch; shows ON / OFF from {@code signal}. */
    private static WidgetDef toggle(float y, String label, String signal, ModConfigSpec.BooleanValue value) {
        return stack(
                at(Float.toString(PANEL_X + 8), Float.toString(y + 3), text(label, T)),
                at(Float.toString(PANEL_X + PANEL_W - 64), Float.toString(y),
                        PdaSkin.role(PdaSkin.Role.TOGGLE, "pda_theme." + signal.replace("v_", ""),
                                button(item -> PdaThemeState.toggle(value), 56, 12, text("{pda_theme." + signal + "}", ACC)))));
    }

    /** Gear button, drawn last in the bar. */
    public static WidgetDef gear() {
        Button b = new Button(item -> {
            PdaThemeState.click();
            PdaThemeState.toggleMenu();
        }, "true", "0", PdaThemeState.Token.HOVER.expr(), PdaThemeState.Token.PRESS.expr(), "0", 0, 0, 0, 0, 14, 14,
                Point.CENTER, new GearIcon());
        return at(Float.toString(GEAR_X), Float.toString(GEAR_Y), b);
    }

    /** Full-screen catcher (clicks outside close the menu) and the panel. */
    public static WidgetDef panel() {
        float h = 18 + 7 * ROW + 24;
        UiAction close = item -> PdaThemeState.closeMenu();
        Button catcher = new Button(close, "true", "0", "0", "0", "0", 0, 0, 0, 0, PdaShell.WIDTH, PdaShell.HEIGHT,
                Point.TOP_LEFT, null);
        return when("pda_theme.menu", stack(
                catcher,
                at(Float.toString(PANEL_X), Float.toString(PANEL_Y), new Button(item -> { }, "true",
                        PdaThemeState.Token.BAR.expr(), PdaThemeState.Token.BAR.expr(), PdaThemeState.Token.BAR.expr(),
                        PdaThemeState.Token.ACCENT.expr(), 1, 0, 0, 0, PANEL_W, h, Point.TOP_LEFT, null)),
                at(Float.toString(PANEL_X + 8), Float.toString(PANEL_Y + 6), text("SETTINGS", ACC)),
                // Theme row: < name >
                at(Float.toString(PANEL_X + 8), Float.toString(PANEL_Y + 21), text("THEME", T)),
                at(Float.toString(PANEL_X + PANEL_W - 104), Float.toString(PANEL_Y + 18),
                        button(item -> PdaThemeState.select(PdaThemes.step(PdaThemeState.theme().id(), -1)), 14, 12, text("<", ACC))),
                at(Float.toString(PANEL_X + PANEL_W - 88), Float.toString(PANEL_Y + 18),
                        button(item -> PdaThemeState.select(PdaThemes.step(PdaThemeState.theme().id(), 1)), 80, 12,
                                text("{pda_theme.name}", ACC))),
                at(Float.toString(PANEL_X + PANEL_W - 22), Float.toString(PANEL_Y + 18),
                        button(item -> PdaThemeState.select(PdaThemes.step(PdaThemeState.theme().id(), 1)), 14, 12, text(">", ACC))),
                toggle(PANEL_Y + 18 + ROW, "EFFECTS", "v_effects", WFClientConfig.PDA_EFFECTS),
                toggle(PANEL_Y + 18 + 2 * ROW, "  SCANLINES", "v_scanlines", WFClientConfig.PDA_SCANLINES),
                toggle(PANEL_Y + 18 + 3 * ROW, "  FLICKER", "v_flicker", WFClientConfig.PDA_FLICKER),
                toggle(PANEL_Y + 18 + 4 * ROW, "  NOISE", "v_noise", WFClientConfig.PDA_NOISE),
                toggle(PANEL_Y + 18 + 5 * ROW, "  VIGNETTE", "v_vignette", WFClientConfig.PDA_VIGNETTE),
                toggle(PANEL_Y + 18 + 6 * ROW, "SOUNDS", "v_sounds", WFClientConfig.PDA_SOUNDS),
                toggle(PANEL_Y + 18 + 7 * ROW, "BOOT ANIMATION", "v_boot", WFClientConfig.PDA_BOOT),
                at(Float.toString(PANEL_X + 8), Float.toString(PANEL_Y + 18 + 8 * ROW + 4),
                        button(item -> PdaThemeState.reset(), 60, 12, text("RESET", T))),
                at(Float.toString(PANEL_X + PANEL_W - 68), Float.toString(PANEL_Y + 18 + 8 * ROW + 4),
                        button(close, 60, 12, text("CLOSE", T)))));
    }

    /** Eight-tooth gear: a ring, a hub and short spokes. */
    private record GearIcon() implements WidgetDef {
        @Override
        public Widget create(Binder binder) {
            return new Widget() {
                @Override
                public void measure(UiEval ev) {
                    width = 14;
                    height = 14;
                }

                @Override
                public void draw(UiEval ev, float x, float y, float w, float h) {
                    Canvas c = ev.canvas;
                    int color = PdaThemeState.instance().isMenuOpen() ? PdaThemeState.Token.ACCENT.argb() : PdaThemeState.Token.DIM.argb();
                    float cx = x + 7;
                    float cy = y + 7;
                    c.roundFrame(cx - 4, cy - 4, cx + 4, cy + 4, 4, 1.2f, color);
                    c.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians(i * 45);
                        float dx = (float) Math.cos(a);
                        float dy = (float) Math.sin(a);
                        c.line(cx + dx * 4, cy + dy * 4, cx + dx * 6.4f, cy + dy * 6.4f, 1.6f, color);
                    }
                }
            };
        }
    }
}
