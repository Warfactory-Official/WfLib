package com.wf.wflib.ui.api.widget;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.ColorProp;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.Interactive;
import com.wf.wflib.ui.api.input.UiAction;
import com.wf.wflib.ui.api.input.UiDrop;
import com.wf.wflib.ui.api.input.UiInput;
import com.wf.wflib.ui.api.input.UiWheel;

/**
 * {@code w x h} panel (expressions) that may be clicked ({@code click}) and scrolled ({@code wheel}), both nullable
 * (neither: no hit recorded), and may receive a carried payload ({@code drop}, nullable). While a payload is carried over a drop-enabled tile {@code dropHover} /
 * {@code dropBorder} replace {@code background} / {@code border}; otherwise {@code hover} replaces {@code background}
 * under the pointer of a clickable tile. {@code child} draws at the top-left.
 */
public record Tile(String w, String h, String background, String hover, String dropHover, String border,
                   String dropBorder, float borderWidth, UiAction click, UiWheel wheel, UiDrop drop, WidgetDef child)
        implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        NumProp pw = binder.num(w);
        NumProp ph = binder.num(h);
        ColorProp back = binder.color(background);
        ColorProp over = binder.color(hover);
        ColorProp dropBack = binder.color(dropHover);
        ColorProp edge = binder.color(border);
        ColorProp dropEdge = binder.color(dropBorder);
        int itemSlot = binder.itemSlot();
        Widget node = child == null ? null : binder.child(child);
        return new Node(pw, ph, back, over, dropBack, edge, dropEdge, itemSlot, node);
    }

    private final class Node extends Widget implements Interactive {
        private final NumProp pw;
        private final NumProp ph;
        private final ColorProp back;
        private final ColorProp over;
        private final ColorProp dropBack;
        private final ColorProp edge;
        private final ColorProp dropEdge;
        private final int itemSlot;
        private final Widget node;
        private final float[] local = new float[2];

        Node(NumProp pw, NumProp ph, ColorProp back, ColorProp over, ColorProp dropBack, ColorProp edge,
             ColorProp dropEdge, int itemSlot, Widget node) {
            this.pw = pw;
            this.ph = ph;
            this.back = back;
            this.over = over;
            this.dropBack = dropBack;
            this.edge = edge;
            this.dropEdge = dropEdge;
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
            UiInput input = ev.input;
            Object item = ev.item(itemSlot);
            int b = back.get(ev);
            int e = edge.get(ev);
            if (input != null) {
                boolean carrying = input.dnd().active();
                if (click != null || wheel != null) {
                    input.hit(x, y, x + width, y + height, this, item);
                    if (click != null && !carrying && input.hovered(this, item)) {
                        b = over.get(ev);
                    }
                }
                if (carrying && drop != null && input.pointerLocal(local)
                        && local[0] >= x && local[0] < x + width && local[1] >= y && local[1] < y + height) {
                    float lx = local[0] - x;
                    float ly = local[1] - y;
                    input.dnd().offer(drop, item, lx, ly);
                    drop.hover(item, input.dnd().payload(), lx, ly);
                    b = dropBack.get(ev);
                    e = dropEdge.get(ev);
                }
            }
            Canvas canvas = ev.canvas;
            if ((b >>> 24) != 0) {
                canvas.fill(x, y, x + width, y + height, b);
            }
            if ((e >>> 24) != 0 && borderWidth > 0) {
                canvas.frame(x, y, x + width, y + height, borderWidth, e);
            }
            if (node != null) {
                node.draw(ev, x, y, width, height);
            }
        }

        @Override
        public void release(Object item, float x, float y, boolean inside) {
            if (inside && click != null) {
                click.run(item);
            }
        }

        @Override
        public boolean wheel(Object item, float amount) {
            return wheel != null && wheel.wheel(item, amount);
        }

        @Override
        public boolean feedback() {
            return click != null;
        }
    }
}
