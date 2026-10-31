package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.UiSkin;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.input.Interactive;
import java.util.function.DoubleConsumer;

/** Shared GAMMA rail, confined to the existing two-unit gutter. Wheel still belongs to the parent. */
final class ScrollRail implements Interactive {
    private float height, content, scroll, step, grab;
    private boolean dragging;
    private DoubleConsumer setter;
    void draw(UiEval ev, float x, float y, float h, float content, float scroll, float step, DoubleConsumer setter) {
        this.height = h; this.content = content; this.scroll = scroll; this.step = step; this.setter = setter;
        float track = Math.max(1, h - 8);
        float size = Math.min(track, Math.max(4, track * h / content));
        float at = y + 4 + (track - size) * scroll / Math.max(1, content - h);
        UiSkin.current().scroll(ev.canvas, x, y, h, at, size);
        if (ev.input != null) ev.input.hit(x, y, x + 2, y + h, this, null);
    }
    public void press(Object item, float x, float y) {
        dragging = false;
        if (y < 4) set(scroll - step);
        else if (y > height - 4) set(scroll + step);
        else {
            float track = Math.max(1, height - 8);
            float size = Math.min(track, Math.max(4, track * height / content));
            float top = 4 + (track - size) * scroll / Math.max(1, content - height);
            if (y < top) set(scroll - height);
            else if (y > top + size) set(scroll + height);
            else { dragging = true; grab = y - top; }
        }
    }
    public void drag(Object item, float x, float y) {
        if (!dragging || Float.isNaN(y)) return;
        float track = Math.max(1, height - 8);
        float size = Math.min(track, Math.max(4, track * height / content));
        set((y - 4 - grab) / Math.max(1, track - size) * (content - height));
    }
    public void release(Object item, float x, float y, boolean inside) { dragging = false; }
    private void set(float value) { setter.accept(ListView.clamp(value, content - height)); }
}
