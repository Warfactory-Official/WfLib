package com.wf.wflib.ui.api.widget;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.ColorProp;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.Interactive;
import com.wf.wflib.ui.api.input.UiDnd;
import com.wf.wflib.ui.api.input.UiDragSource;
import com.wf.wflib.ui.api.input.UiInput;

/**
 * {@code w x h} panel (background, optional border) that is clicked or, once the pointer leaves the press point by
 * {@code threshold} units, dragged: {@link UiDragSource#begin} supplies the payload, {@link Tile}s under the pointer
 * receive it on release. {@code hover} replaces {@code background} under the pointer while nothing is carried.
 */
public record DragSource(String w, String h, String background, String hover, String border, float borderWidth,
                         float threshold, UiDragSource handler, WidgetDef child) implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        NumProp pw = binder.num(w);
        NumProp ph = binder.num(h);
        ColorProp back = binder.color(background);
        ColorProp over = binder.color(hover);
        ColorProp edge = binder.color(border);
        int itemSlot = binder.itemSlot();
        Widget node = child == null ? null : binder.child(child);
        return new Node(pw, ph, back, over, edge, itemSlot, node);
    }

    private final class Node extends Widget implements Interactive {
        private final NumProp pw;
        private final NumProp ph;
        private final ColorProp back;
        private final ColorProp over;
        private final ColorProp edge;
        private final int itemSlot;
        private final Widget node;
        private UiInput input;
        private float pressX;
        private float pressY;
        private Object payload;

        Node(NumProp pw, NumProp ph, ColorProp back, ColorProp over, ColorProp edge, int itemSlot, Widget node) {
            this.pw = pw;
            this.ph = ph;
            this.back = back;
            this.over = over;
            this.edge = edge;
            this.itemSlot = itemSlot;
            this.node = node;
        }

        @Override
        public void measure(UiEval ev) {
            width = Math.max(0, pw.getFloat(ev));
            height = Math.max(0, ph.getFloat(ev));
            if (node != null) {
                node.measure(ev);
            }
        }

        @Override
        public void draw(UiEval ev, float x, float y, float bw, float bh) {
            input = ev.input;
            Object item = ev.item(itemSlot);
            int b = back.get(ev);
            if (input != null) {
                input.hit(x, y, x + width, y + height, this, item);
                if (!input.dnd().active() && input.hovered(this, item)) {
                    b = over.get(ev);
                }
            }
            Canvas canvas = ev.canvas;
            if ((b >>> 24) != 0) {
                canvas.fill(x, y, x + width, y + height, b);
            }
            int e = edge.get(ev);
            if ((e >>> 24) != 0 && borderWidth > 0) {
                canvas.frame(x, y, x + width, y + height, borderWidth, e);
            }
            if (node != null) {
                node.draw(ev, x, y, width, height);
            }
        }

        @Override
        public void press(Object item, float x, float y) {
            pressX = x;
            pressY = y;
            payload = null;
        }

        @Override
        public void drag(Object item, float x, float y) {
            if (payload != null || Float.isNaN(x) || Float.isNaN(y)) {
                return;
            }
            if (Math.hypot(x - pressX, y - pressY) > threshold) {
                payload = handler.begin(item);
                if (payload != null) {
                    input.dnd().begin(payload);
                }
            }
        }

        @Override
        public void release(Object item, float x, float y, boolean inside) {
            if (payload == null) {
                if (inside) {
                    handler.click(item);
                }
                return;
            }
            Object carried = payload;
            payload = null;
            UiDnd.Offer at = input.dnd().end();
            if (at != null && !Float.isNaN(x)) {
                at.target().drop(at.item(), carried, at.x(), at.y());
            }
            handler.end(carried);
        }
    }
}
