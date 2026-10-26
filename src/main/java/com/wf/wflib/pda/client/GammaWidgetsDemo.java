package com.wf.wflib.pda.client;

import com.wf.wflib.pda.client.theme.*;
import com.wf.wflib.ui.*;
import com.wf.wflib.ui.widget.*;
import com.wf.wflib.ui.input.TextBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.ArrayList;
import static com.wf.wflib.ui.Ui.*;

/** Opt-in interactive conformance page for the shared controls, absent from normal installations. */
public final class GammaWidgetsDemo implements PdaApp {
    private static final GammaWidgetsDemo INSTANCE = new GammaWidgetsDemo();
    private static final Subject<GammaWidgetsDemo> DEMO = Subject.register("pda_widgets", GammaWidgetsDemo.class, () -> INSTANCE);
    private static boolean checked = true, radio = true;
    private static int selected, refreshes, accepted, tracked;
    private static double volume = .55;
    public static final TextBox SEARCH = TextBox.text(80);
    public static String state() { return checked + "/" + radio + "/" + selected + "/" + refreshes + "/" + accepted + "/" + tracked; }
    static void register() {
        if (!Boolean.getBoolean("wflib.pda.widgetsDemo")) return;
        Signals.bool(DEMO, "checked", d -> checked);
        Signals.bool(DEMO, "radio", d -> radio);
        Signals.num(DEMO, "volume", d -> volume);
        Signals.text(DEMO, "state", 80, d -> "REFRESH " + refreshes + "   ACCEPT " + accepted + "   TRACK " + tracked);
        PdaApps.register(INSTANCE);
    }
    public ResourceLocation id() { return ResourceLocation.parse("wflib:widgets_demo"); }
    public Component title() { return Component.literal("WIDGETS"); }
    public WidgetDef build() {
        List<WidgetDef> cards = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int n = i;
            cards.add(at("0", Integer.toString(i * 68), PdaControls.card(364,
                    "FIELD REPORT / " + (i + 1),
                    "A field team has reported an unstable signal near the northern perimeter. Review the available information before accepting this assignment. Bring suitable equipment and confirm the route with your contact.",
                    "RECEIVED 08:" + (10 + i),
                    new PdaControls.Action("ACCEPT", item -> accepted = n + 1),
                    new PdaControls.Action("TRACK", item -> tracked = n + 1))));
        }
        return stack(
                at("8", "7", PdaControls.button(91, 16, "REFRESH TASKS", item -> refreshes++)),
                at("108", "10", text("{pda_widgets.state}", PdaShell.DIM)),
                at("8", "29", PdaControls.toggle(100, "AVAILABLE", "pda_widgets.checked", item -> checked = !checked, true)),
                at("112", "29", PdaControls.toggle(99, "LOCAL", "pda_widgets.radio", item -> radio = !radio, false)),
                at("218", "29", new Slider("56", "14", "pda_widgets.volume", (item, value) -> volume = value, .05f, PdaShell.BAR_BACK, PdaShell.TEXT, PdaShell.TEXT)),
                at("8", "49", PdaTextBox.field(SEARCH, "FILTER REPORTS", 364)),
                at("8", "71", new ScrollBox("368", "144", 34, PdaShell.EDGE, stack(cards.toArray(WidgetDef[]::new)))),
                at("281", "29", new PdaControls.Dropdown(91, List.of("ALL REPORTS", "ACTIVE", "ARCHIVED"), () -> selected, value -> selected = value)));
    }
}
