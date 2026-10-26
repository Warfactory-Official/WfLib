package com.wf.wflib.ui.widget;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.pda.client.theme.PdaSkin;
import com.wf.wflib.pda.client.theme.PdaFont;
import com.wf.wflib.pda.client.theme.PdaTheme;
import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;
import com.wf.wflib.ui.input.Interactive;
import com.wf.wflib.ui.input.TextBox;
import com.wf.wflib.ui.input.UiInput;
import net.minecraft.Util;

/**
 * Click-to-focus input box over a {@link TextBox}: caret, selection (drag, shift + arrows), placeholder while empty,
 * error edge while {@link TextBox#error()}. Long input scrolls to keep the caret visible and is cut at the box edge by
 * measured width. Code-only (the model is a Java object), no JSON name.
 */
public record TextBoxField(TextBox box, String placeholder, float w, float h, Colours colours) implements WidgetDef {
    /** ARGB. */
    public record Colours(int back, int edge, int focusEdge, int errorEdge, int text, int placeholder, int selection) {
    }

    private static final float PAD = 4;
    private static final long BLINK_MS = 500;

    @Override
    public Widget create(Binder binder) {
        return new Node();
    }

    private final class Node extends Widget implements Interactive {
        /** First visible character, and where the text starts on screen (for pointer to caret). */
        private int start;
        private float textX;
        private Canvas canvas;
        private boolean gamma;

        @Override
        public void measure(UiEval ev) {
            width = w;
            height = h;
        }

        @Override
        public void draw(UiEval ev, float x, float y, float bw, float bh) {
            this.canvas = ev.canvas;
            Canvas canvas = ev.canvas;
            boolean focused = box.focused();
            gamma = PdaSkin.active();
            PdaTheme t = PdaTheme.GAMMA;
            Colours colours = gamma ? new Colours(t.field(), t.edge(), t.text(), t.alert(), t.text(), t.dim(), t.select()) : TextBoxField.this.colours;
            int edge = box.error() ? colours.errorEdge() : focused ? colours.focusEdge() : colours.edge();
            canvas.fill(x, y, x + w, y + h, edge);
            canvas.fill(x + 1, y + 1, x + w - 1, y + h - 1, colours.back());
            float ty = y + (h - canvas.lineHeight()) / 2 + 1;
            CharSequence text = box.text();
            textX = x + PAD;
            float room = w - 2 * PAD;
            if (text.length() == 0 && !focused) {
                if (PdaSkin.active()) com.wf.wflib.pda.client.PdaText.line(ev, placeholder, textX, ty, room, colours.placeholder());
                else canvas.text(placeholder, textX, ty, colours.placeholder(), false);
            }
            int cursor = box.cursor();
            start = Math.min(start, text.length());
            if (cursor < start) {
                start = cursor;
            }
            while (start < cursor && canvas.width(text, start, cursor) > room) {
                start++;
            }
            int end = text.length();
            while (end > cursor && canvas.width(text, start, end) > room) {
                end--;
            }
            if (focused && box.hasSelection()) {
                int from = Math.max(start, box.selectionStart());
                int to = Math.min(end, box.selectionEnd());
                if (from < to) {
                    float sx = textX + canvas.width(text, start, from);
                    canvas.fill(sx, ty - 1, textX + canvas.width(text, start, to), ty + canvas.lineHeight() - 1, colours.selection());
                }
            }
            canvas.text(text, start, end, textX, ty, colours.text(), false);
            if (focused && Util.getMillis() / BLINK_MS % 2 == 0) {
                float cx = textX + canvas.width(text, start, cursor);
                canvas.fill(cx, ty - 1, cx + 1, ty + canvas.lineHeight() - 1, colours.text());
            }
            UiInput input = ev.input;
            if (input != null) {
                input.hit(x, y, x + w, y + h, this, null);
            }
        }

        /** Character boundary nearest the pointer, measured with the canvas of the last draw. */
        private int indexAt(float localX) {
            CharSequence text = box.text();
            if (canvas == null) {
                return text.length();
            }
            float target = localX - PAD;
            int best = start;
            float bestGap = Float.MAX_VALUE;
            for (int i = start; i <= text.length(); i++) {
                float gap = Math.abs(PdaFont.width(canvas, text, start, i, gamma) - target);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = i;
                } else {
                    break;
                }
            }
            return best;
        }

        @Override
        public void press(Object item, float x, float y) {
            box.focus();
            box.place(indexAt(x), false);
        }

        @Override
        public void drag(Object item, float x, float y) {
            if (!Float.isNaN(x)) {
                box.place(indexAt(x), true);
            }
        }
    }
}
