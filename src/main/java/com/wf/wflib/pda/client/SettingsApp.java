package com.wf.wflib.pda.client;

import com.wf.wflib.WFLib;
import com.wf.wflib.ui.Signals;
import com.wf.wflib.ui.Subject;
import com.wf.wflib.ui.WidgetDef;
import com.wf.wflib.ui.input.TextLine;
import com.wf.wflib.ui.input.UiAction;
import com.wf.wflib.ui.widget.Button;
import com.wf.wflib.ui.widget.Point;
import com.wf.wflib.ui.widget.TextField;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.wf.wflib.ui.Ui.at;
import static com.wf.wflib.ui.Ui.clip;
import static com.wf.wflib.ui.Ui.list;
import static com.wf.wflib.ui.Ui.rect;
import static com.wf.wflib.ui.Ui.slider;
import static com.wf.wflib.ui.Ui.stack;
import static com.wf.wflib.ui.Ui.text;
import static com.wf.wflib.ui.Ui.when;

/** PDA look: colours by hex code, panel opacity, background image from the server ({@link PdaTheme}). */
final class SettingsApp implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "settings");
    private static final SettingsApp INSTANCE = new SettingsApp();
    private static final Subject<SettingsApp> APP = Subject.register("pda_settings", SettingsApp.class,
            () -> INSTANCE);
    private static final Subject<PdaTheme.Choice> CHOICE = Subject.register("pda_bg", PdaTheme.Choice.class);

    private SettingsApp() {
    }

    static void register() {
        Signals.bool(APP, "bad_accent", a -> PdaTheme.badAccent());
        Signals.bool(APP, "bad_text", a -> PdaTheme.badText());
        Signals.bool(APP, "bad_panel", a -> PdaTheme.badPanel());
        Signals.num(APP, "opacity", a -> PdaTheme.opacity());
        Signals.text(APP, "opacity_text", 8, a -> Math.round(PdaTheme.opacity() * 100) + "%");
        Signals.items(APP, "backgrounds", a -> PdaTheme.choices(), CHOICE);
        Signals.text(APP, "status", 40, a -> !PdaTheme.status().isEmpty() ? PdaTheme.status()
                : PdaTheme.listing() ? "ASKING THE SERVER..." : PdaTheme.choices().size() > 1 ? ""
                : "NONE ON SERVER: <WORLD>/pda_backgrounds");
        Signals.text(CHOICE, "label", 40, PdaTheme.Choice::label);
        Signals.bool(CHOICE, "on", c -> c.name().equals(PdaTheme.background()));
        PdaApps.register(INSTANCE);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("wflib.pda.app.settings");
    }

    @Override
    public void opened() {
        PdaTheme.refresh();
    }

    @Override
    public WidgetDef build() {
        float h = PdaShell.APP_HEIGHT - 16;
        float leftW = 176;
        float rightX = 8 + leftW + 8;
        float rightW = PdaShell.APP_WIDTH - rightX - 8;
        float listW = rightW - 8;
        WidgetDef choice = new Button(i -> PdaTheme.select(((PdaTheme.Choice) i).name()), "true",
                "pda_bg.on ? " + PdaShell.PRESS + " : 0", PdaShell.HOVER, PdaShell.PRESS, "0", 0, 0, 0, 0, listW, 14,
                Point.TOP_LEFT, at("4", "3", clip(listW - 8, 12, text("{pda_bg.label}",
                "pda_bg.on ? " + PdaShell.ACCENT + " : " + PdaShell.TEXT))));
        return stack(
                at("8", "8", PdaShell.panel(leftW, h)),
                at("12", "12", text("COLOURS  #RGB / #RRGGBB", PdaShell.DIM)),
                colourRow("ACCENT", 30, PdaTheme.ACCENT, "pda_settings.bad_accent", PdaShell.ACCENT),
                colourRow("TEXT", 50, PdaTheme.TEXT, "pda_settings.bad_text", PdaShell.TEXT),
                colourRow("PANEL", 70, PdaTheme.PANEL, "pda_settings.bad_panel", PdaShell.PANEL),
                at("12", "94", text("OPACITY", PdaShell.DIM)),
                at("56", "93", slider("84", "8", "pda_settings.opacity",
                        (item, v) -> PdaTheme.setOpacity((float) v), PdaShell.BAR_BACK, PdaShell.ACCENT,
                        PdaShell.TEXT)),
                at("146", "94", text("{pda_settings.opacity_text}", PdaShell.TEXT)),
                at("12", "116", btn("RESET ALL", 60, 14, i -> PdaTheme.reset())),

                at(Float.toString(rightX), "8", PdaShell.panel(rightW, h)),
                at(Float.toString(rightX + 4), "12", text("BACKGROUND", PdaShell.DIM)),
                at(Float.toString(rightX + rightW - 28), "10", btn("R", 22, 13, i -> PdaTheme.refresh())),
                at(Float.toString(rightX + 4), "26", list("pda_settings.backgrounds", 14, 0, Float.toString(listW),
                        "150", PdaShell.EDGE, choice)),
                at(Float.toString(rightX + 4), "184", clip(listW, 12, text("{pda_settings.status}", PdaShell.DIM))));
    }

    private static WidgetDef colourRow(String label, float y, TextLine line, String bad, String swatch) {
        return stack(
                at("12", Float.toString(y + 3), text(label, PdaShell.DIM)),
                at("56", Float.toString(y), new TextField(line, "#", 74, 14, 0xFF101210, 0xFF787F76, 0xFFA9D46C,
                        0xFFD2D6CC, 0xFF8D9488)),
                at("134", Float.toString(y), rect(14, 14, swatch)),
                when(bad, at("152", Float.toString(y + 3), text("BAD", PdaShell.ALERT))));
    }

    private static WidgetDef btn(String label, float w, float h, UiAction action) {
        return new Button(action, "true", PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE, 1, 0, 0, 0, w,
                h, Point.CENTER, text(label, PdaShell.TEXT));
    }
}
