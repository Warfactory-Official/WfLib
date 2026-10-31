package com.wf.wflib.pda.client;

import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.TextBox;
import com.wf.wflib.ui.api.widget.TextBoxField;

/**
 * PDA-styled input box for apps: click it and type (the keyboard goes to the box, not to walking or hotkeys; Esc or a
 * click elsewhere leaves it). {@code TextBox.integer(6, 1, 1000)} for a count, {@code TextBox.text(16)} for a name.
 *
 * <pre>{@code
 * private static final TextBox COUNT = TextBox.integer(6, 1, 1000).onSubmit(s -> buy(Long.parseLong(s)));
 * ... at("8", "20", PdaTextBox.field(COUNT, "COUNT", 80))
 * }</pre>
 */
public final class PdaTextBox {
    /** Height of one box, units: tall enough to click with the cursor. */
    public static final float HEIGHT = 14;
    private static final TextBoxField.Colours COLOURS = new TextBoxField.Colours(0xFF2C2F2C, 0xFF787F76, 0xFFA9D46C,
            0xFFD0584A, 0xFFD2D6CC, 0xFF8D9488, 0x80A9D46C);

    private PdaTextBox() {
    }

    public static WidgetDef field(TextBox box, String placeholder, float width) {
        return new TextBoxField(box, placeholder, width, HEIGHT, COLOURS);
    }
}
