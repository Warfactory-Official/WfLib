package com.wf.wflib.pda.client.theme;

import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;

/** Marks everything drawn inside as PDA content, so shared widgets (Box, Button) apply the theme's chrome only there. */
public record ThemeScope(WidgetDef child) implements WidgetDef {
    private static final com.wf.wflib.ui.input.UiInput.Feedback AUDIO = new com.wf.wflib.ui.input.UiInput.Feedback() {
        public void hover() { PdaSounds.request(PdaSounds.Event.HOVER); }
        public void click() { PdaThemeState.click(); }
    };
    @Override
    public Widget create(Binder binder) {
        Widget node = binder.child(child);
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                boolean before = PdaThemeState.inPda;
                PdaThemeState.inPda = true;
                try {
                    node.measure(ev);
                    width = node.width();
                    height = node.height();
                } finally {
                    PdaThemeState.inPda = before;
                }
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                boolean before = PdaThemeState.inPda;
                PdaThemeState.inPda = true;
                var feedback = ev.input == null ? null : ev.input.feedback(AUDIO);
                try {
                    node.draw(ev, x, y, w, h);
                } finally {
                    if (ev.input != null) ev.input.feedback(feedback);
                    PdaThemeState.inPda = before;
                }
            }
        };
    }
}
