package com.wf.wflib.ui.api.widget;

import com.wf.gemrender.ui.Canvas;
import com.wf.gemrender.ui.TextBuffer;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.ColorProp;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.TextProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import net.minecraft.ChatFormatting;

/**
 * {@link Text} whose output may carry {@code §} colour codes ({@code §0-9a-f} set the rgb, {@code §r} back to
 * {@code color}; other format codes are dropped). Alpha stays {@code color}'s.
 */
public record CodedText(String text, String color, boolean shadow, String scale) implements WidgetDef {
    private static final char CODE = '§';

    @Override
    public Widget create(Binder binder) {
        TextProp template = binder.text(text);
        ColorProp colour = binder.color(color);
        NumProp size = binder.num(scale);
        return new Widget() {
            private final TextBuffer raw = new TextBuffer(template.capacity());
            private final TextBuffer measured = new TextBuffer(template.capacity());
            private final TextBuffer plain = new TextBuffer(template.capacity());
            private final int[] runStart = new int[template.capacity() + 1];
            private final int[] runRgb = new int[template.capacity() + 1];
            private int runs;
            private int measuredFont = -1;
            private float textWidth;
            private float s;

            @Override
            public void measure(UiEval ev) {
                template.write(ev, raw);
                s = size.getFloat(ev);
                Canvas canvas = ev.canvas;
                if (canvas.fontVersion() != measuredFont || !raw.contentEquals(measured)) {
                    measuredFont = canvas.fontVersion();
                    measured.clear().append(raw);
                    parse();
                    textWidth = plain.length() == 0 ? 0 : canvas.width(plain);
                }
                width = textWidth * s;
                height = plain.length() == 0 ? 0 : (canvas.lineHeight() - 1) * s;
            }

            private void parse() {
                plain.clear();
                runs = 0;
                int rgb = -1;
                for (int i = 0; i < raw.length(); i++) {
                    char c = raw.charAt(i);
                    if (c == CODE && i + 1 < raw.length()) {
                        ChatFormatting f = ChatFormatting.getByCode(raw.charAt(++i));
                        if (f == ChatFormatting.RESET) {
                            rgb = -1;
                        } else if (f != null && f.getColor() != null) {
                            rgb = f.getColor();
                        }
                        continue;
                    }
                    if (runs == 0 || runRgb[runs - 1] != rgb) {
                        runStart[runs] = plain.length();
                        runRgb[runs++] = rgb;
                    }
                    plain.append(c);
                }
                runStart[runs] = plain.length();
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                if (plain.length() == 0) {
                    return;
                }
                int argb = colour.get(ev);
                if ((argb >>> 24) == 0) {
                    return;
                }
                Canvas canvas = ev.canvas;
                if (s != 1) {
                    canvas.push().translate(x, y).scale(s);
                    x = 0;
                    y = 0;
                }
                for (int i = 0; i < runs; i++) {
                    int c = runRgb[i] < 0 ? argb : argb & 0xFF000000 | runRgb[i];
                    x = canvas.text(plain, runStart[i], runStart[i + 1], x, y, c, shadow);
                }
                if (s != 1) {
                    canvas.pop();
                }
            }
        };
    }
}
