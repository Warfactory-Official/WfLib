package com.wf.wflib.ui.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Form-field text model for {@link com.wf.wflib.ui.widget.TextBoxField}: free text or a number, caret, selection,
 * Enter => {@link #onSubmit}, Esc / click-away => blur (a number is clamped into range then). Unlike {@link TextLine}
 * (a command line) Enter keeps the text and there is no history.
 *
 * <pre>{@code
 * TextBox amount = TextBox.integer(6, 1, 100_000).onSubmit(text -> send(Long.parseLong(text)));
 * TextBox name = TextBox.text(16).accepting(s -> s.matches("[A-Za-z0-9_]*"));
 * }</pre>
 */
public final class TextBox implements UiKeyboard.Target {
    public enum Mode { TEXT, INTEGER, DECIMAL }

    private final Mode mode;
    private final int maxLength;
    private final int decimals;
    private final double min;
    private final double max;
    private final StringBuilder text = new StringBuilder();
    /** Selection runs between anchor and cursor; equal = none. */
    private int cursor;
    private int anchor;
    private Consumer<String> submit = s -> {
    };
    private Consumer<String> change = s -> {
    };
    private Predicate<String> accepted = s -> true;

    private TextBox(Mode mode, int maxLength, int decimals, double min, double max) {
        this.mode = mode;
        this.maxLength = maxLength;
        this.decimals = decimals;
        this.min = min;
        this.max = max;
    }

    /** Free text of up to {@code maxLength} characters. */
    public static TextBox text(int maxLength) {
        return new TextBox(Mode.TEXT, maxLength, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    /** Whole number in {@code [min, max]} (a minus sign only if {@code min < 0}). */
    public static TextBox integer(int maxLength, long min, long max) {
        return new TextBox(Mode.INTEGER, maxLength, 0, min, max);
    }

    /** Decimal number with at most {@code decimals} places in {@code [min, max]}; ',' types '.'. */
    public static TextBox decimal(int maxLength, int decimals, double min, double max) {
        return new TextBox(Mode.DECIMAL, maxLength, Math.max(0, decimals), min, max);
    }

    /** Called with the text when Enter is pressed (numbers already clamped). */
    public TextBox onSubmit(Consumer<String> submit) {
        this.submit = submit;
        return this;
    }

    /** Called after every edit. */
    public TextBox onChange(Consumer<String> change) {
        this.change = change;
        return this;
    }

    /** Extra whole-text filter: an edit that would make it false is refused (free text, e.g. a name pattern). */
    public TextBox accepting(Predicate<String> accepted) {
        this.accepted = accepted;
        return this;
    }

    public Mode mode() {
        return mode;
    }

    public CharSequence text() {
        return text;
    }

    public boolean empty() {
        return text.length() == 0;
    }

    public int cursor() {
        return cursor;
    }

    public int selectionStart() {
        return Math.min(cursor, anchor);
    }

    public int selectionEnd() {
        return Math.max(cursor, anchor);
    }

    public boolean hasSelection() {
        return cursor != anchor;
    }

    /** Replaces the text (filtered like typing); the caret goes to the end. */
    public void set(String value) {
        text.setLength(0);
        cursor = anchor = 0;
        insert(value);
        change.accept(text.toString());
    }

    /** Numbers: the value, NaN when empty, partial ("-", ".") or not a number. */
    public double number() {
        if (mode == Mode.TEXT || text.length() == 0) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Whole-number value, or {@code fallback} when empty or not a number. */
    public long integer(long fallback) {
        double v = number();
        return Double.isNaN(v) ? fallback : Math.round(v);
    }

    /** True for a number that is incomplete or out of range (shown as the error state); never for empty text. */
    public boolean error() {
        if (mode == Mode.TEXT || text.length() == 0) {
            return false;
        }
        double v = number();
        return Double.isNaN(v) || v < min || v > max;
    }

    public void focus() {
        UiKeyboard.focus(this);
    }

    public boolean focused() {
        return UiKeyboard.focused(this);
    }

    /** Caret from a pointer: {@code extend} keeps the anchor (drag), otherwise collapses the selection. */
    public void place(int index, boolean extend) {
        cursor = Math.clamp(index, 0, text.length());
        if (!extend) {
            anchor = cursor;
        }
    }

    @Override
    public void blurred() {
        clamp();
        anchor = cursor;
    }

    private void clamp() {
        if (mode == Mode.TEXT || text.length() == 0) {
            return;
        }
        double v = number();
        if (Double.isNaN(v)) {
            text.setLength(0);
        } else {
            double c = Math.max(min, Math.min(max, v));
            if (c != v) {
                text.setLength(0);
                text.append(mode == Mode.INTEGER ? Long.toString(Math.round(c)) : trim(c));
            }
        }
        cursor = anchor = Math.min(cursor, text.length());
        change.accept(text.toString());
    }

    private String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%." + decimals + "f", v);
        return decimals > 0 && s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    @Override
    public void key(int key, int scan, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & GLFW.GLFW_MOD_ALT) == 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> UiKeyboard.blur();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                clamp();
                submit.accept(text.toString());
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!deleteSelection() && cursor > 0) {
                    int from = ctrl ? wordStart() : cursor - 1;
                    text.delete(from, cursor);
                    cursor = anchor = from;
                    change.accept(text.toString());
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!deleteSelection() && cursor < text.length()) {
                    text.deleteCharAt(cursor);
                    anchor = cursor;
                    change.accept(text.toString());
                }
            }
            case GLFW.GLFW_KEY_LEFT -> move(hasSelection() && !shift ? selectionStart() : cursor - 1, shift);
            case GLFW.GLFW_KEY_RIGHT -> move(hasSelection() && !shift ? selectionEnd() : cursor + 1, shift);
            case GLFW.GLFW_KEY_HOME -> move(0, shift);
            case GLFW.GLFW_KEY_END -> move(text.length(), shift);
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    anchor = 0;
                    cursor = text.length();
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl && hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selectionStart(), selectionEnd()));
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl && hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selectionStart(), selectionEnd()));
                    deleteSelection();
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    insert(Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n]", " "));
                    change.accept(text.toString());
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void character(int codepoint, int modifiers) {
        // GLFW's char-mods callback can emit the printable shortcut letter after key().
        // It must not overwrite a selection or insert another c/x/v. Ctrl+Alt is AltGr text input.
        boolean shortcut = (modifiers & GLFW.GLFW_MOD_SUPER) != 0
                || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && (modifiers & GLFW.GLFW_MOD_ALT) == 0;
        if (shortcut || Character.isISOControl(codepoint)) {
            return;
        }
        insert(new String(Character.toChars(codepoint)));
        change.accept(text.toString());
    }

    private void move(int to, boolean extend) {
        cursor = Math.clamp(to, 0, text.length());
        if (!extend) {
            anchor = cursor;
        }
    }

    private boolean deleteSelection() {
        if (!hasSelection()) {
            return false;
        }
        int from = selectionStart();
        text.delete(from, selectionEnd());
        cursor = anchor = from;
        change.accept(text.toString());
        return true;
    }

    /** Typed or pasted text: characters the mode refuses are dropped, the rest replaces the selection. */
    private void insert(String s) {
        int from = selectionStart(), to = selectionEnd();
        StringBuilder next = new StringBuilder(text);
        next.delete(from, to);
        int at = from;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp)) {
                continue;
            }
            if (mode != Mode.TEXT) {
                if (cp == ',' && mode == Mode.DECIMAL) {
                    cp = '.';
                }
                boolean digit = cp >= '0' && cp <= '9';
                boolean sign = cp == '-' && min < 0 && at == 0 && next.indexOf("-") < 0;
                boolean dot = cp == '.' && mode == Mode.DECIMAL && decimals > 0 && next.indexOf(".") < 0;
                if (!digit && !sign && !dot) {
                    continue;
                }
                if (digit && mode == Mode.DECIMAL) {
                    int point = next.indexOf(".");
                    if (point >= 0 && at > point && next.length() - point - 1 >= decimals) {
                        continue;
                    }
                }
            }
            if (next.length() + Character.charCount(cp) > maxLength) {
                break;
            }
            next.insert(at, Character.toChars(cp));
            at += Character.charCount(cp);
        }
        if (!accepted.test(next.toString())) {
            return;
        }
        text.setLength(0);
        text.append(next);
        cursor = anchor = at;
    }

    private int wordStart() {
        int i = cursor;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return i;
    }
}
