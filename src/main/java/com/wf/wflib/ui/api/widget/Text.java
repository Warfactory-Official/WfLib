package com.wf.wflib.ui.api.widget;

import com.wf.wflib.ui.api.UiSkin;
import com.wf.gemrender.ui.Canvas;
import com.wf.gemrender.ui.TextBuffer;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.ColorProp;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.TextProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;

/**
 * One line of templated text (see {@link TextProp}); size = text extent x {@code scale}.
 * {@code maxWidth} (canvas units after scale; 0 or NaN = unlimited) cuts the text and ends it with
 * {@code ".."}.
 */
public record Text(String text, String color, boolean shadow, String scale, String maxWidth) implements WidgetDef {
    private static final String ELLIPSIS = "..";

    public Text(String text, String color, boolean shadow, String scale) {
        this(text, color, shadow, scale, "0");
    }

    @Override
    public Widget create(Binder binder) {
        TextProp template = binder.text(text);
        ColorProp colour = binder.color(color);
        NumProp size = binder.num(scale);
        NumProp limit = binder.num(maxWidth);
        return new Widget() {
            private final TextBuffer raw = new TextBuffer(template.capacity());
            private final TextBuffer measured = new TextBuffer(template.capacity());
            private final TextBuffer buffer = new TextBuffer(template.capacity() + ELLIPSIS.length());
            private float measuredMax = -1;
            private float measuredScale = Float.NaN;
            private int measuredFont = -1;
            private float textWidth;
            private float s;

            @Override
            public void measure(UiEval ev) {
                template.write(ev, raw);
                s = size.getFloat(ev);
                float max = limit.getFloat(ev);
                if (!(max > 0)) {
                    max = 0;
                }
                Canvas canvas = ev.canvas;
                if (max != measuredMax || max > 0 && s != measuredScale || canvas.fontVersion() != measuredFont
                        || !raw.contentEquals(measured)) {
                    measuredMax = max;
                    measuredScale = s;
                    measuredFont = canvas.fontVersion();
                    measured.clear().append(raw);
                    fit(canvas, max);
                }
                width = textWidth * s;
                height = buffer.length() == 0 ? 0 : (canvas.lineHeight() - 1) * s;
            }

            private void fit(Canvas canvas, float max) {
                buffer.clear().append(raw);
                textWidth = buffer.length() == 0 ? 0 : canvas.width(buffer);
                if (max > 0 && textWidth * s > max) {
                    float room = max / s - canvas.width(ELLIPSIS);
                    int keep = buffer.length();
                    while (keep > 0 && canvas.width(buffer, 0, keep) > room) {
                        keep--;
                    }
                    buffer.setLength(keep);
                    buffer.append(ELLIPSIS);
                    textWidth = canvas.width(buffer);
                }
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                if (buffer.length() == 0) {
                    return;
                }
                int argb = colour.get(ev);
                if ((argb >>> 24) == 0) {
                    return;
                }
                Canvas canvas = ev.canvas;
                if (UiSkin.current().active() && !raw.contentEquals(buffer) && ev.input != null) {
                    // The label remains part of its parent button's hit rectangle.
                    if (ev.input.over(x, y, x + width, y + height))
                        UiSkin.current().popup("DETAILS", "", raw.toString());
                }
                if (s == 1) {
                    canvas.text(buffer, x, y, argb, shadow);
                    return;
                }
                canvas.push().translate(x, y).scale(s);
                canvas.text(buffer, 0, 0, argb, shadow);
                canvas.pop();
            }
        };
    }
}
