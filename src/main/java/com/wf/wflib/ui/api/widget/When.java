package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.BoolProp;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;

/** {@code then} while {@code condition} holds, else {@code otherwise} (nullable => nothing, size 0). */
public record When(String condition, WidgetDef then, WidgetDef otherwise) implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        BoolProp test = binder.bool(condition);
        Widget yes = binder.child(then);
        Widget no = otherwise == null ? null : binder.child(otherwise);
        return new Widget() {
            private Widget active;

            @Override
            public void measure(UiEval ev) {
                active = test.get(ev) ? yes : no;
                if (active == null) {
                    width = 0;
                    height = 0;
                    return;
                }
                active.measure(ev);
                width = active.width();
                height = active.height();
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                if (active != null) {
                    active.draw(ev, x, y, w, h);
                }
            }
        };
    }
}
