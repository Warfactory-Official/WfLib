package com.wf.wflib.pda.client.theme;

import com.wf.wflib.pda.client.*;
import com.wf.wflib.ui.*;
import com.wf.wflib.ui.widget.Button;
import com.wf.wflib.ui.widget.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import static com.wf.wflib.ui.Ui.*;

/** One global navigation row; app content below it retains its original coordinates. */
public final class GammaHeader {
    private GammaHeader() { }
    private static WidgetDef tab(float x, float w, String label, String app, String selected) {
        return at(Float.toString(x), "2", PdaSkin.role(PdaSkin.Role.TAB, selected,
                new Button(item -> {
                    if (app == null) PdaClient.home();
                    else {
                        PdaApp target = PdaApps.get(ResourceLocation.parse(app));
                        if (target != null && target.visible(Minecraft.getInstance().player)) PdaClient.open(target);
                    }
                }, "true", PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE,
                        1, 0, 0, 0, w, 14, Point.CENTER, text(label, PdaShell.TEXT))));
    }
    public static WidgetDef build() {
        return stack(tab(2, 51, "HOME", null, "pda.home"),
                tab(55, 44, "MAP", "wflib:map", "pda_theme.map"),
                tab(101, 54, "ALERTS", "wflib:alerts", "pda_theme.alerts"),
                tab(157, 65, "TERMINAL", "wflib:terminal", "pda_theme.terminal"),
                at("230", "5", new com.wf.wflib.ui.widget.Text("{pda.title}", PdaShell.DIM, false, "1", "86")),
                at("341", "5", text("{world.hour|02}:{world.minute|02}", PdaShell.TEXT)),
                new Battery());
    }
    private record Battery() implements WidgetDef {
        public Widget create(Binder binder) { return new Widget() {
            public void measure(UiEval ev) { width = height = 0; }
            public void draw(UiEval ev, float x, float y, float w, float h) {
                var c = ev.canvas;
                c.frame(371, 6, 381, 12, .6f, 0xFFBBC1C5);
                c.fill(381, 8, 382, 10, 0xFFBBC1C5);
                c.fill(373, 7.5f, 374, 10.5f, 0xFFBBC1C5);
                c.fill(375, 7.5f, 376, 10.5f, 0xFFBBC1C5);
                c.fill(377, 7.5f, 378, 10.5f, 0xFFBBC1C5);
            }
        }; }
    }
}
