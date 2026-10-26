package com.wf.wflib.pda.client.theme;

import com.wf.wflib.config.WFClientConfig;
import com.wf.wflib.pda.client.PdaClient;
import com.wf.wflib.ui.Signals;
import com.wf.wflib.ui.Subject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.ModConfigSpec;


/**
 * Active theme and the gear-menu switches, as the {@code pda_theme} subject. Palette constants in
 * {@code PdaShell} read these signals, so every app re-skins with no code of its own and a change shows on the
 * next frame. {@code -Dwflib.pda.theme=<id>} overrides the saved theme (harness).
 */
public final class PdaThemeState {
    public static final String THEME_PROPERTY = "wflib.pda.theme";

    public static final Subject<PdaThemeState> THEME = Subject.register("pda_theme", PdaThemeState.class, PdaThemeState::instance);
    private static final PdaThemeState INSTANCE = new PdaThemeState();

    /** True while the PDA layout draws; shared widgets skip theme chrome elsewhere. */
    static boolean inPda;
    private PdaTheme theme;
    private boolean menu;
    private long lastClick;

    private PdaThemeState() {
    }

    public static PdaThemeState instance() {
        return INSTANCE;
    }

    public static PdaTheme theme() {
        PdaThemeState s = INSTANCE;
        if (s.theme == null) {
            String override = System.getProperty(THEME_PROPERTY);
            s.theme = PdaThemes.get(override != null ? override : read(WFClientConfig.PDA_THEME, "classic"));
        }
        return s.theme;
    }

    private static <T> T read(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static boolean flag(ModConfigSpec.BooleanValue value, boolean fallback) {
        return read(value, fallback);
    }

    private static void write(ModConfigSpec.ConfigValue<Boolean> value, boolean on) {
        try {
            value.set(on);
            value.save();
        } catch (IllegalStateException ignored) {
            // Unbaked spec (too early): the switch still applies for this session through the caller.
        }
    }

    public static void select(PdaTheme next) {
        INSTANCE.theme = next;
        try {
            WFClientConfig.PDA_THEME.set(next.id());
            WFClientConfig.PDA_THEME.save();
        } catch (IllegalStateException ignored) {
            // Unbaked spec: session only.
        }
    }

    public static boolean gamma() { return theme() == PdaTheme.GAMMA; }

    public static boolean inPda() { return inPda; }

    public static boolean effects() { return flag(WFClientConfig.PDA_EFFECTS, true); }
    public static boolean scanlines() { return effects() && flag(WFClientConfig.PDA_SCANLINES, true); }
    public static boolean flicker() { return effects() && flag(WFClientConfig.PDA_FLICKER, false); }
    public static boolean noise() { return effects() && flag(WFClientConfig.PDA_NOISE, true); }
    public static boolean vignette() { return effects() && flag(WFClientConfig.PDA_VIGNETTE, true); }
    public static boolean sounds() { return flag(WFClientConfig.PDA_SOUNDS, true); }

    /** Flips one of the gear-menu switches. */
    static void toggle(ModConfigSpec.BooleanValue value) {
        write(value, !flag(value, true));
    }

    public boolean isMenuOpen() {
        return menu;
    }

    public static void toggleMenu() {
        INSTANCE.menu = !INSTANCE.menu;
    }

    public static void closeMenu() {
        INSTANCE.menu = false;
    }

    /** Resets every switch to its default (the theme itself stays). */
    static void reset() {
        write(WFClientConfig.PDA_EFFECTS, true);
        write(WFClientConfig.PDA_SCANLINES, true);
        write(WFClientConfig.PDA_FLICKER, false);
        write(WFClientConfig.PDA_NOISE, true);
        write(WFClientConfig.PDA_VIGNETTE, true);
        write(WFClientConfig.PDA_SOUNDS, true);
    }

    /** Short click for menu and PDA buttons; rate-limited, off when sounds are off. */
    public static void click() {
        long now = System.currentTimeMillis();
        if (!sounds() || now - INSTANCE.lastClick < 50 || !PdaClient.focused()) {
            return;
        }
        INSTANCE.lastClick = now;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.35f));
    }

    public static void register() {
        Signals.bool(THEME, "gamma", s -> gamma());
        for (String app : new String[]{"map", "alerts", "terminal"}) {
            Signals.bool(THEME, app, s -> PdaClient.app() != null && PdaClient.app().id().toString().equals("wflib:" + app));
        }
        Signals.bool(THEME, "effects", s -> effects());
        Signals.bool(THEME, "scanlines", s -> flag(WFClientConfig.PDA_SCANLINES, true));
        Signals.bool(THEME, "flicker", s -> flag(WFClientConfig.PDA_FLICKER, false));
        Signals.bool(THEME, "noise", s -> flag(WFClientConfig.PDA_NOISE, true));
        Signals.bool(THEME, "vignette", s -> flag(WFClientConfig.PDA_VIGNETTE, true));
        Signals.bool(THEME, "sounds", s -> sounds());
        for (Token token : Token.values()) {
            Signals.num(THEME, token.signal, s -> PdaTheme.unsigned(token.of(theme())));
        }
        Signals.bool(THEME, "menu", s -> s.menu);
        Signals.text(THEME, "name", 12, s -> theme().name());
        Signals.text(THEME, "v_effects", 4, s -> onOff(effects()));
        Signals.text(THEME, "v_scanlines", 4, s -> onOff(flag(WFClientConfig.PDA_SCANLINES, true)));
        Signals.text(THEME, "v_flicker", 4, s -> onOff(flag(WFClientConfig.PDA_FLICKER, false)));
        Signals.text(THEME, "v_noise", 4, s -> onOff(flag(WFClientConfig.PDA_NOISE, true)));
        Signals.text(THEME, "v_vignette", 4, s -> onOff(flag(WFClientConfig.PDA_VIGNETTE, true)));
        Signals.text(THEME, "v_sounds", 4, s -> onOff(sounds()));
    }

    private static String onOff(boolean on) {
        return on ? "ON" : "OFF";
    }

    /** Colour tokens exposed as {@code pda_theme.<signal>}; also readable from Java by canvas-drawn widgets. */
    public enum Token {
        BACK("back", PdaTheme::back), BAR("bar", PdaTheme::bar), PANEL("panel", PdaTheme::panel), HOVER("hover", PdaTheme::hover),
        PRESS("press", PdaTheme::press), EDGE("edge", PdaTheme::edge), HI("hi", PdaTheme::hi), TEXT("text", PdaTheme::text),
        DIM("dim", PdaTheme::dim), ACCENT("accent", PdaTheme::accent), ALERT("alert", PdaTheme::alert),
        FIELD("field", PdaTheme::field), CARET("caret", PdaTheme::caret), SELECT("select", PdaTheme::select);

        public final String signal;
        private final java.util.function.ToIntFunction<PdaTheme> getter;

        Token(String signal, java.util.function.ToIntFunction<PdaTheme> getter) {
            this.signal = signal;
            this.getter = getter;
        }

        int of(PdaTheme theme) {
            return getter.applyAsInt(theme);
        }

        /** Expression for a layout string: {@code pda_theme.<signal>}. */
        public String expr() {
            return "pda_theme." + signal;
        }

        /** Current ARGB of this token, for canvas-drawn widgets. */
        public int argb() {
            return of(theme());
        }
    }
}
