package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.ItemsRef;
import com.wf.wflib.ui.api.RepeatScope;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;

import java.util.List;

/**
 * One {@code child} per element of list signal {@code items} (at most {@code max}, 0 = all), laid
 * out as a row or column; {@code columns > 0} => grid of that many columns ({@code gap} both ways,
 * columns as wide as their widest element, rows as tall as the tallest); {@code rows > 0} (columns 0) => same grid filled down columns of that many rows. Inside, the list's item subject reads the current element and
 * {@code index}/{@code count} the iteration. Each element is measured again right before it draws.
 */
public record Repeat(String items, boolean vertical, float gap, Align align, int max, int columns, int rows, WidgetDef child)
        implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        ItemsRef<?> ref = binder.items(items);
        RepeatScope scope = binder.enterScope(ref);
        Widget node = binder.child(child);
        binder.exitScope();
        return new Widget() {
            private float[] colW = new float[4];
            private float cellH;

            @Override
            public void measure(UiEval ev) {
                if (columns > 0 || rows > 0) {
                    measureGrid(ev);
                    return;
                }
                List<?> list = ref.get(ev);
                int count = max > 0 ? Math.min(max, list.size()) : list.size();
                Object saved = ev.saveItem(ref);
                float main = 0;
                float cross = 0;
                boolean any = false;
                for (int i = 0; i < count; i++) {
                    ev.enterItem(ref, scope, list.get(i), i, count);
                    node.measure(ev);
                    float m = vertical ? node.height() : node.width();
                    float c = vertical ? node.width() : node.height();
                    if (m == 0 && c == 0) {
                        continue;
                    }
                    main += (any ? gap : 0) + m;
                    cross = Math.max(cross, c);
                    any = true;
                }
                ev.restoreItem(ref, saved);
                width = vertical ? cross : main;
                height = vertical ? main : cross;
            }

            private void measureGrid(UiEval ev) {
                List<?> list = ref.get(ev);
                int count = max > 0 ? Math.min(max, list.size()) : list.size();
                Object saved = ev.saveItem(ref);
                int cols = gridColumns(count);
                if (colW.length < cols) {
                    colW = new float[cols];
                }
                java.util.Arrays.fill(colW, 0, cols, 0);
                cellH = 0;
                for (int i = 0; i < count; i++) {
                    ev.enterItem(ref, scope, list.get(i), i, count);
                    node.measure(ev);
                    int col = columns > 0 ? i % columns : i / rows;
                    colW[col] = Math.max(colW[col], node.width());
                    cellH = Math.max(cellH, node.height());
                }
                ev.restoreItem(ref, saved);
                float total = 0;
                for (int c = 0; c < cols; c++) {
                    total += colW[c];
                }
                int lines = cols == 0 ? 0 : (count + cols - 1) / cols;
                if (columns == 0) {
                    lines = Math.min(rows, count);
                }
                width = cols == 0 ? 0 : total + (cols - 1) * gap;
                height = lines == 0 ? 0 : lines * cellH + (lines - 1) * gap;
            }

            private int gridColumns(int count) {
                return columns > 0 ? Math.min(columns, count) : (count + rows - 1) / rows;
            }

            private void drawGrid(UiEval ev, float x, float y) {
                List<?> list = ref.get(ev);
                int count = max > 0 ? Math.min(max, list.size()) : list.size();
                Object saved = ev.saveItem(ref);
                int col = -1;
                float cx = x;
                for (int i = 0; i < count; i++) {
                    ev.enterItem(ref, scope, list.get(i), i, count);
                    node.measure(ev);
                    int c = columns > 0 ? i % columns : i / rows;
                    int row = columns > 0 ? i / columns : i % rows;
                    if (c != col) {
                        cx = x;
                        for (int k = 0; k < c; k++) {
                            cx += colW[k] + gap;
                        }
                        col = c;
                    }
                    float cy = y + row * (cellH + gap);
                    node.draw(ev, cx + align.offset(colW[c], node.width()), cy, colW[c], cellH);
                }
                ev.restoreItem(ref, saved);
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                if (columns > 0 || rows > 0) {
                    drawGrid(ev, x, y);
                    return;
                }
                List<?> list = ref.get(ev);
                int count = max > 0 ? Math.min(max, list.size()) : list.size();
                Object saved = ev.saveItem(ref);
                float cross = vertical ? width : height;
                float at = 0;
                boolean any = false;
                for (int i = 0; i < count; i++) {
                    ev.enterItem(ref, scope, list.get(i), i, count);
                    node.measure(ev);
                    float m = vertical ? node.height() : node.width();
                    float c = vertical ? node.width() : node.height();
                    if (m == 0 && c == 0) {
                        continue;
                    }
                    if (any) {
                        at += gap;
                    }
                    any = true;
                    float off = align.offset(cross, c);
                    if (vertical) {
                        node.draw(ev, x + off, y + at, c, m);
                    } else {
                        node.draw(ev, x + at, y + off, m, c);
                    }
                    at += m;
                }
                ev.restoreItem(ref, saved);
            }
        };
    }
}
