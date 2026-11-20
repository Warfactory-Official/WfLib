package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiInput;

/**
 * {@code child} at the pointer ({@code dx}, {@code dy} off it) while a payload is carried; size 0. Pointer and layout
 * share units only at the root of an absolute layout: place it last there, outside any transform.
 */
public record DragGhost(float dx, float dy, WidgetDef child) implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        Widget node = binder.child(child);
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                UiInput input = ev.input;
                if (input == null || !input.dnd().active() || Float.isNaN(input.pointerX())) {
                    return;
                }
                node.measure(ev);
                node.draw(ev, input.pointerX() + dx, input.pointerY() + dy, node.width(), node.height());
            }
        };
    }
}
