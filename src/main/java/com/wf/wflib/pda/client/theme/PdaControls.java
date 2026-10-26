package com.wf.wflib.pda.client.theme;

import com.wf.wflib.pda.client.PdaShell;
import com.wf.wflib.ui.*;
import com.wf.wflib.ui.input.*;
import com.wf.wflib.ui.widget.*;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import static com.wf.wflib.ui.Ui.*;

/** Reusable semantic PDA controls. Consumers supply their real state/actions, never a painted mock control. */
public final class PdaControls {
    private PdaControls() { }
    public static WidgetDef button(float w, float h, String label, UiAction action) {
        return new Button(action, "true", PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE,
                1, 0, 4, 1, w, h, Point.CENTER, new Text(label, PdaShell.TEXT, false, "1", Float.toString(w - 10)));
    }
    public static WidgetDef toggle(float w, String label, String selected, UiAction action, boolean checkbox) {
        return PdaSkin.role(checkbox ? PdaSkin.Role.CHECKBOX : PdaSkin.Role.TOGGLE, selected,
                new Button(action, "true", PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE,
                        1, 0, 13, 2, w, 14, Point.LEFT, new Text(label, PdaShell.TEXT, false, "1", Float.toString(w - 27))));
    }
    public record Action(String label, UiAction run) { }
    /** Wide card with portrait, measured paragraph, timestamp, and separately clickable right-side actions. */
    public static WidgetDef card(float w, String title, String body, String timestamp, Action primary, Action secondary) {
        WidgetDef portrait = new Portrait();
        return PdaSkin.role(PdaSkin.Role.CARD, "false", new Box(PdaShell.PANEL, PdaShell.EDGE, 1, 0, 0, 0, w, 64,
                Point.TOP_LEFT, stack(at("5", "6", portrait),
                at("40", "5", new Text(title, PdaShell.TEXT, false, "1", Float.toString(w - 132))),
                at("40", "17", new Paragraph(body, w - 132, 3, title, timestamp)),
                at("40", "52", new Text(timestamp, PdaShell.DIM, false, "1", Float.toString(w - 132))),
                at(Float.toString(w - 85), "8", button(79, 20, "> " + primary.label, primary.run)),
                at(Float.toString(w - 85), "35", button(79, 20, "+ " + secondary.label, secondary.run)))));
    }
    private record Portrait() implements WidgetDef {
        public Widget create(Binder binder) { return new Widget() {
            public void measure(UiEval ev) { width = 29; height = 47; }
            public void draw(UiEval ev, float x, float y, float w, float h) {
                var c = ev.canvas;
                c.fill(x, y, x + 29, y + 47, 0xFF171C20);
                c.frame(x, y, x + 29, y + 47, .6f, 0xFF89949B);
                c.roundRect(x + 9, y + 7, x + 20, y + 20, 5, 0xFF69757C);
                c.triangle(x + 4, y + 38, x + 14, y + 18, x + 25, y + 38, 0xFF4B575F);
                c.line(x + 4, y + 42, x + 25, y + 42, .5f, 0xFF89949B);
            }
        }; }
    }
    /** Text wrapping uses the loaded font; full body is available through the shared tooltip layer. */
    public record Paragraph(String text, float w, int maxLines, String title, String stamp, String colour) implements WidgetDef {
        public Paragraph(String text, float w, int maxLines) { this(text, w, maxLines, "DETAILS", "", PdaShell.TEXT); }
        public Paragraph(String text, float w, int maxLines, String title, String stamp) { this(text, w, maxLines, title, stamp, PdaShell.TEXT); }
        public Widget create(Binder binder) {
            TextProp template = binder.text(text);
            ColorProp ink = binder.color(colour);
            var buffer = new com.wf.gemrender.ui.TextBuffer(template.capacity());
            return new Widget() {
                String full = ""; List<String> lines = List.of();
                public void measure(UiEval ev) {
                    template.write(ev, buffer); full = buffer.toString();
                    lines = wrap(ev.canvas, full, w, maxLines); width = w; height = lines.size() * ev.canvas.lineHeight();
                }
                public void draw(UiEval ev, float x, float y, float bw, float bh) {
                    for (int i = 0; i < lines.size(); i++) ev.canvas.text(lines.get(i), x, y + i * ev.canvas.lineHeight(), ink.get(ev), false);
                    if (ev.input != null) {
                        if (ev.input.over(x, y, x + width, y + height)) PdaPopup.show(title, stamp, full);
                    }
                }
            };
        }
    }
    public static List<String> wrap(com.wf.gemrender.ui.Canvas c, String value, float w, int maxLines) {
        List<String> result = new ArrayList<>(); String rest = value;
        while (!rest.isEmpty() && result.size() < maxLines) {
            int end = 0; int space = -1;
            while (end < rest.length()) {
                if (rest.charAt(end) == '\n') break;
                int next = end + Character.charCount(rest.codePointAt(end));
                if (c.width(rest, 0, next) > w) break;
                if (Character.isWhitespace(rest.charAt(end))) space = end;
                end = next;
            }
            boolean newline = end < rest.length() && rest.charAt(end) == '\n';
            if (end == 0 && !newline) end = Character.charCount(rest.codePointAt(0));
            if (!newline && end < rest.length() && space > 0) end = space;
            String line = rest.substring(0, end).stripTrailing(); rest = rest.substring(end + (newline ? 1 : 0)).stripLeading();
            if (result.size() == maxLines - 1 && !rest.isEmpty()) {
                while (!line.isEmpty() && c.width(line + "...") > w) line = line.substring(0, line.offsetByCodePoints(line.length(), -1));
                line += "...";
            }
            result.add(line);
        }
        return result;
    }
    private static String fit(com.wf.gemrender.ui.Canvas c, String value, float width) {
        if (c.width(value) <= width) return value;
        String fitted = value;
        while (!fitted.isEmpty() && c.width(fitted + "...") > width)
            fitted = fitted.substring(0, fitted.offsetByCodePoints(fitted.length(), -1));
        return fitted + "...";
    }
    /** Popup is drawn/hit after the selector. Add after sibling fields so options remain on top. */
    public record Dropdown(float w, List<String> options, IntSupplier selected, IntConsumer set) implements WidgetDef {
        public Dropdown { options = List.copyOf(options); if (options.isEmpty()) throw new IllegalArgumentException("empty dropdown"); }
        public Widget create(Binder binder) { return new Node(); }
        private final class Node extends Widget implements Interactive {
            boolean open;
            final Interactive dismiss = new Interactive() {
                public boolean feedback() { return false; }
                public void press(Object item, float x, float y) { open = false; }
            };
            public void measure(UiEval ev) { width = w; height = 14; }
            public void draw(UiEval ev, float x, float y, float bw, float bh) {
                var c = ev.canvas; int count = open ? options.size() + 1 : 1;
                if (open && ev.input != null) ev.input.hit(0, 0, PdaShell.WIDTH, PdaShell.HEIGHT, dismiss, null);
                c.fill(x, y, x + w, y + count * 14, PdaThemeState.Token.BAR.argb());
                c.frame(x, y, x + w, y + count * 14, .7f, PdaThemeState.Token.EDGE.argb());
                int at = Math.clamp(selected.getAsInt(), 0, options.size() - 1);
                String current = options.get(at);
                c.text(fit(c, current, w - 18), x + 4, y + 3, PdaThemeState.Token.TEXT.argb(), false);
                c.triangle(x + w - 10, y + 5, x + w - 4, y + 5, x + w - 7, y + 9, PdaThemeState.Token.DIM.argb());
                if (open) for (int i = 0; i < options.size(); i++) {
                    if (i == at) c.fill(x + 1, y + (i + 1) * 14, x + w - 1, y + (i + 2) * 14, PdaThemeState.Token.HOVER.argb());
                    c.text(fit(c, options.get(i), w - 8), x + 4, y + (i + 1) * 14 + 3, PdaThemeState.Token.TEXT.argb(), false);
                }
                if (ev.input != null) for (int row = 0; row < count; row++)
                    ev.input.hit(x, y + row * 14, x + w, y + (row + 1) * 14, this, row);
            }
            public void release(Object item, float x, float y, boolean inside) {
                if (!inside) { open = false; return; }
                int row = (Integer) item;
                if (row == 0) open = !open;
                else { int at = row - 1; if (at >= 0 && at < options.size()) set.accept(at); open = false; }
            }
        }
    }
}
