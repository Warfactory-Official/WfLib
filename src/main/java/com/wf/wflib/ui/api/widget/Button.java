package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.UiSkin;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.theme.Chamfer;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.BoolProp;
import com.wf.wflib.ui.api.ColorProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.Interactive;
import com.wf.wflib.ui.api.input.UiAction;
import com.wf.wflib.ui.api.input.UiInput;

/**
 * {@link Box} that runs {@code action} on click while {@code enabled}; background swaps to {@code hover}
 * under the pointer and {@code pressed} while held over it; border to {@code hoverBorder} / {@code hoverBorderWidth}
 * under the pointer.
 */
public record Button(UiAction action, String enabled, String background, String hover, String pressed, String border,
                     float borderWidth, float radius, float padX, float padY, float minWidth, float minHeight,
                     Point align, WidgetDef child, String hoverBorder, float hoverBorderWidth) implements WidgetDef {
    /** Border unchanged on hover. */
    public Button(UiAction action, String enabled, String background, String hover, String pressed, String border,
                  float borderWidth, float radius, float padX, float padY, float minWidth, float minHeight,
                  Point align, WidgetDef child) {
        this(action, enabled, background, hover, pressed, border, borderWidth, radius, padX, padY, minWidth, minHeight,
                align, child, border, borderWidth);
    }

    @Override
    public Widget create(Binder binder) {
        BoolProp on = binder.bool(enabled);
        ColorProp back = binder.color(background);
        ColorProp over = binder.color(hover);
        ColorProp down = binder.color(pressed);
        ColorProp edge = binder.color(border);
        ColorProp overEdge = binder.color(hoverBorder);
        int itemSlot = binder.itemSlot();
        Widget node = child == null ? null : binder.child(child);
        return new Node(on, back, over, down, edge, overEdge, itemSlot, node);
    }

    private final class Node extends Widget implements Interactive {
        private final BoolProp on;
        private final ColorProp back;
        private final ColorProp over;
        private final ColorProp down;
        private final ColorProp edge;
        private final ColorProp overEdge;
        private final int itemSlot;
        private final Widget node;

        Node(BoolProp on, ColorProp back, ColorProp over, ColorProp down, ColorProp edge, ColorProp overEdge,
             int itemSlot, Widget node) {
            this.on = on;
            this.back = back;
            this.over = over;
            this.down = down;
            this.edge = edge;
            this.overEdge = overEdge;
            this.itemSlot = itemSlot;
            this.node = node;
        }

        @Override
        public void measure(UiEval ev) {
            float cw = 0;
            float ch = 0;
            if (node != null) {
                node.measure(ev);
                cw = node.width();
                ch = node.height();
            }
            width = Math.max(minWidth, cw + padX * 2);
            height = Math.max(minHeight, ch + padY * 2);
        }

        @Override
        public void draw(UiEval ev, float x, float y, float w, float h) {
            Canvas canvas = ev.canvas;
            UiInput input = ev.input;
            Object item = ev.item(itemSlot);
            int b = back.get(ev);
            boolean hovered = false;
            if (input != null && on.get(ev)) {
                input.hit(x, y, x + width, y + height, this, item);
                hovered = input.hovered(this, item);
                if (hovered) {
                    b = input.captured(this, item) ? down.get(ev) : over.get(ev);
                }
            }
            int e = hovered ? overEdge.get(ev) : edge.get(ev);
            float borderWidth = hovered ? hoverBorderWidth : Button.this.borderWidth;
            float x1 = x + width;
            float y1 = y + height;
            if (UiSkin.current().active()) {
                UiSkin.current().button(canvas, x, y, width, height, b, e, borderWidth,
                        input != null && input.hovered(this, item), on.get(ev),
                        padX);
            } else if (radius > 0) {
                if ((b >>> 24) != 0) {
                    canvas.roundRect(x, y, x1, y1, radius, b);
                }
                if ((e >>> 24) != 0 && borderWidth > 0) {
                    canvas.roundFrame(x, y, x1, y1, radius, borderWidth, e);
                }
            } else {
                float cut = UiSkin.current().inPda() ? Chamfer.cut(x1 - x, y1 - y) : 0;
                if (cut > 0) {
                    if ((b >>> 24) != 0) {
                        Chamfer.fill(canvas, x, y, x1, y1, cut, b);
                    }
                    if ((e >>> 24) != 0 && borderWidth > 0) {
                        Chamfer.frame(canvas, x + borderWidth / 2, y + borderWidth / 2, x1 - borderWidth / 2, y1 - borderWidth / 2, cut, borderWidth, e);
                    }
                } else {
                    if ((b >>> 24) != 0) {
                        canvas.fill(x, y, x1, y1, b);
                    }
                    if ((e >>> 24) != 0 && borderWidth > 0) {
                        canvas.frame(x, y, x1, y1, borderWidth, e);
                    }
                }
            }
            if (node != null) {
                float iw = width - padX * 2;
                float ih = height - padY * 2;
                node.draw(ev, x + padX + align.x.offset(iw, node.width()), y + padY + align.y.offset(ih, node.height()),
                        node.width(), node.height());
            }
        }

        @Override public boolean feedback() { return node != null; }

        @Override
        public void release(Object item, float x, float y, boolean inside) {
            if (inside) {
                action.run(item);
            }
        }
    }
}
