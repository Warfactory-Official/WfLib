package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.ItemsRef;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.RepeatScope;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;

import java.util.List;

/**
 * One {@code child} per element of list signal {@code items}, its {@code pivot} at ({@code x}, {@code y}) relative to
 * the box origin; {@code x}/{@code y} read the element's item subject. Size 0.
 */
public record Scatter(String items, String x, String y, Point pivot, WidgetDef child) implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        ItemsRef<?> ref = binder.items(items);
        RepeatScope scope = binder.enterScope(ref);
        NumProp px = binder.num(x);
        NumProp py = binder.num(y);
        Widget node = binder.child(child);
        binder.exitScope();
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
            }

            @Override
            public void draw(UiEval ev, float bx, float by, float w, float h) {
                List<?> list = ref.get(ev);
                int count = list.size();
                Object saved = ev.saveItem(ref);
                for (int i = 0; i < count; i++) {
                    ev.enterItem(ref, scope, list.get(i), i, count);
                    node.measure(ev);
                    node.draw(ev, bx + px.getFloat(ev) - pivot.x.pivot(node.width()),
                            by + py.getFloat(ev) - pivot.y.pivot(node.height()), node.width(), node.height());
                }
                ev.restoreItem(ref, saved);
            }
        };
    }
}
