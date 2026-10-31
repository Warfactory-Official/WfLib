package com.wf.wflib.pda.api.client;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.pda.api.client.PdaThemeState.Token;
import com.wf.wflib.pda.client.theme.ThemeLayer;
import com.wf.wflib.pda.client.theme.ThemeMenu;
import com.wf.wflib.pda.client.theme.GammaHeader;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Box;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Image;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.ScrollBox;
import com.wf.wflib.ui.api.widget.Sized;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.wf.wflib.pda.client.PdaBackground;
import com.wf.wflib.core.api.WFLib;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.grid;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * PDA screen, absolute units {@link #WIDTH} x {@link #HEIGHT}: top bar (back, title, clock), home grid of
 * {@link PdaApps} or the open app below it, cursor last. One instance; apps bound once.
 */
public final class PdaShell {
    public static final float WIDTH = 384;
    public static final int TEXT_MAX = 1200;
    public static final float HEIGHT = 240;
    public static final float BAR = 18;
    public static final float APP_WIDTH = WIDTH;
    public static final float APP_HEIGHT = HEIGHT - BAR;

    /**
     * Palette, ARGB expressions that read the active theme ({@code pda_theme.*}): apps reuse it and re-skin with the
     * theme. {@code classic} holds the original STALKER-panel values.
     */
    public static final String BACK = Token.BACK.expr();
    public static final String BAR_BACK = Token.BAR.expr();
    public static final String PANEL = Token.PANEL.expr();
    public static final String HOVER = Token.HOVER.expr();
    public static final String PRESS = Token.PRESS.expr();
    public static final String EDGE = Token.EDGE.expr();
    public static final String TEXT = Token.TEXT.expr();
    public static final String DIM = Token.DIM.expr();
    public static final String ACCENT = Token.ACCENT.expr();
    public static final String ALERT = Token.ALERT.expr();
    private static final int CURSOR_EDGE = 0xFF141614;

    public static final Subject<PdaShell> PDA = Subject.register("pda", PdaShell.class, PdaShell::instance);
    public static final Subject<PdaApp> APP = Subject.register("pda_app", PdaApp.class);

    private static final PdaShell INSTANCE = new PdaShell();
    private static final Map<PdaApp, String> TITLES = new IdentityHashMap<>();
    private static UiLayout layout;

    private PdaShell() {
    }

    private static PdaShell instance() {
        return INSTANCE;
    }

    /** Surface clear colour: the active theme's background. */
    public static int clear() {
        return Token.BACK.argb();
    }

    public static void register() {
        PdaThemeState.register();
        Signals.text(PDA, "title", TEXT_MAX, s -> PdaClient.app() == null ? "PDA" : title(PdaClient.app()));
        Signals.bool(PDA, "home", s -> PdaClient.app() == null);
        Signals.bool(PDA, "focused", s -> PdaClient.focused());
        Signals.items(PDA, "apps", s -> PdaApps.visible(), APP);
        Signals.text(APP, "title", TEXT_MAX, PdaShell::title);
        Signals.num(APP, "badge", PdaApp::badge);
        PdaTheme.register();
    }

    /** App panel: themed translucent fill, 1-unit {@link #EDGE} border. */
    public static WidgetDef panel(float w, float h) {
        return new Box("pda.th_panel", EDGE, 1, 0, 0, 0, w, h, Point.TOP_LEFT, null);
    }

    /** Bound native app labels to their actual column; full text stays available on hover. */
    public static WidgetDef fit(String value, String colour, float w) {
        return new com.wf.wflib.ui.api.widget.Text(value, colour, false, "1", Float.toString(Math.max(1, w)));
    }

    public static WidgetDef wrap(String value, String colour, float w, int lines) {
        return new com.wf.wflib.pda.client.theme.PdaControls.Paragraph(value, w, lines, "DETAILS", "", colour);
    }

    private static String title(PdaApp app) {
        return TITLES.computeIfAbsent(app, a -> a.title().getString());
    }

    /** Built on first draw: every app registered by then. */
    public static UiLayout layout() {
        if (layout == null) {
            List<PdaApp> apps = PdaApps.all();
            List<WidgetDef> roots = new ArrayList<>(apps.size());
            for (PdaApp app : apps) {
                roots.add(app.build());
            }
          layout = new UiLayout(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda"), new ThemeScope(stack(
                    rect(WIDTH, HEIGHT, BACK),
                    when("pda.has_bg", new Image(PdaBackground.TEXTURE, WIDTH, HEIGHT, 0, 0, 1, 1, "#ffffffff", "0")),
                    rect(WIDTH, BAR, "pda.th_bar"),
                    at("0", Float.toString(BAR - 1), rect(WIDTH, 1, EDGE)),
                    when("!pda_theme.gamma", stack(
                    when("pda.home", at("8", "5", text("{pda.title}", TEXT)),
                            stack(at("2", "2", new Button(item -> PdaClient.home(), "true", "0", HOVER, PRESS, EDGE,
                                            1, 0, 6, 2, 0, 13, Point.CENTER, text("< HOME", TEXT))),
                                    at("64", "5", text("{pda.title}", TEXT)))),
                    place(Float.toString(WIDTH - 8), "5", Point.TOP_RIGHT,
                            text("{world.hour|02}:{world.minute|02}", TEXT)))),
                    when("pda_theme.gamma", GammaHeader.build()),
                    at("0", Float.toString(BAR), new AppHost(apps, roots, home())),
                    com.wf.wflib.pda.api.client.PdaPopup.layer(),
                    ThemeMenu.panel(),
                    ThemeMenu.gear(),
                    new ThemeLayer(),
                    new Cursor())), 0);
        }
       return layout;
    }

    private static WidgetDef home() {
        float gap = 8;
        float margin = 12;
        float viewW = WIDTH - margin * 2;
        float viewH = APP_HEIGHT - margin * 2;
        float w = (float) Math.floor((viewW - 3 - gap * 3) / 4);
        float h = (float) Math.floor((APP_HEIGHT - margin * 2 - gap * 2) / 3);
        WidgetDef tile = new Button(item -> PdaClient.open((PdaApp) item), "true", PANEL, HOVER, PRESS, EDGE, 1, 0,
                0, 0, w, h, Point.TOP_LEFT, new Sized(Float.toString(w), Float.toString(h), Point.TOP_LEFT, false, stack(
                place(Float.toString(w / 2), Float.toString(h / 2 - 4), Point.TOP, fit("{pda_app.title}", TEXT, w - 10)),
                when("pda_app.badge > 0", place(Float.toString(w - 5), "5", Point.TOP_RIGHT,
                        text("{pda_app.badge}", ALERT))))));
        return at(Float.toString(margin), Float.toString(margin), new ScrollBox(Float.toString(viewW),
                Float.toString(viewH), h + gap, EDGE, grid("pda.apps", 4, gap, tile)));
    }

    /** Open app's root, else the home grid; {@link #APP_WIDTH} x {@link #APP_HEIGHT}. */
    private record AppHost(List<PdaApp> apps, List<WidgetDef> roots, WidgetDef home) implements WidgetDef {
        @Override
        public Widget create(Binder binder) {
            Widget homeNode = binder.child(home);
            Widget[] nodes = new Widget[roots.size()];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = binder.child(roots.get(i));
            }
            return new Widget() {
                private Widget active;

                @Override
                public void measure(UiEval ev) {
                    int index = apps.indexOf(PdaClient.app());
                    active = index < 0 ? homeNode : nodes[index];
                    active.measure(ev);
                    width = APP_WIDTH;
                    height = APP_HEIGHT;
                }

                @Override
                public void draw(UiEval ev, float x, float y, float w, float h) {
                    active.draw(ev, x, y, APP_WIDTH, APP_HEIGHT);
                }
            };
        }
    }

    /** Arrow at the cursor while focused. */
    private record Cursor() implements WidgetDef {
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
                    if (!PdaClient.focused()) {
                        return;
                    }
                    Canvas canvas = ev.canvas;
                    float cx = PdaClient.cursorX();
                    float cy = PdaClient.cursorY();
                    canvas.triangle(cx - 1, cy - 2, cx - 1, cy + 14, cx + 10, cy + 10, CURSOR_EDGE);
                    canvas.triangle(cx, cy, cx, cy + 11, cx + 7.5f, cy + 8.5f, Token.ACCENT.argb());
                }
            };
        }
    }
}
