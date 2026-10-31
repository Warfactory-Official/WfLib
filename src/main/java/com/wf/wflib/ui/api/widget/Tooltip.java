package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiInput;
import com.wf.wflib.ui.api.input.UiTooltip;

/** {@code child}, with {@code lines} shown by the host while the pointer is over it; clicks pass through. */
public record Tooltip(UiTooltip lines, WidgetDef child) implements WidgetDef {
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
                if (input != null) {
                    input.offerTooltip(x, y, x + width, y + height, lines, ev.item(itemSlot));
                }
                node.draw(ev, x, y, w, h);
            }
        };
    }
}
