package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiHover;
import com.wf.wflib.ui.api.input.UiInput;

/** {@code child}, reporting each frame the pointer is over it to {@code hover}; takes no clicks. Code-only. */
public record Hover(UiHover hover, WidgetDef child) implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        int itemSlot = binder.itemSlot();
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
                UiInput input = ev.input;
                if (input != null && input.over(x, y, x + width, y + height)) {
                    hover.over(ev.item(itemSlot));
                }
                node.draw(ev, x, y, w, h);
            }
        };
    }
}
