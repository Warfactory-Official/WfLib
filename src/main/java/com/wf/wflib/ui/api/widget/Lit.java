package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;

/** {@code child}'s area marked non-emissive on a world screen: shaded by the world light, not glowing. Draw emissive parts after it. */
public record Lit(WidgetDef child) implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        Widget node = binder.child(child);
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                node.measure(ev);
                width = node.width();
                height = node.height();
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                node.draw(ev, x, y, w, h);
                ev.canvas.mask(x, y, x + width, y + height);
            }
        };
    }
}
