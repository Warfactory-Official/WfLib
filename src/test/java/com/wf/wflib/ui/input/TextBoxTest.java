package com.wf.wflib.ui.input;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.*;

class TextBoxTest {
    private static void type(TextBox box, String s) {
        s.codePoints().forEach(cp -> box.character(cp, 0));
    }

    @Test
    void integerBoxKeepsOnlyDigitsAndClampsOnEnter() {
        var submitted = new String[1];
        var box = TextBox.integer(5, 1, 100).onSubmit(s -> submitted[0] = s);
        type(box, "1a2-.5");
        assertEquals("125", box.text().toString());
        assertTrue(box.error());
        box.key(GLFW.GLFW_KEY_ENTER, 0, 0);
        assertEquals("100", submitted[0]);
        assertFalse(box.error());
    }

    @Test
    void minusOnlyWhereANegativeNumberIsAllowed() {
        var positive = TextBox.integer(5, 0, 9);
        type(positive, "-1");
        assertEquals("1", positive.text().toString());
        var signed = TextBox.integer(5, -9, 9);
        type(signed, "5-");
        assertEquals("5", signed.text().toString());
        signed.set("");
        type(signed, "-3");
        assertEquals("-3", signed.text().toString());
        assertEquals(-3, signed.integer(0));
    }

    @Test
    void decimalBoxTakesOnePointAndLimitsPlaces() {
        var box = TextBox.decimal(8, 2, 0, 999.99);
        type(box, "12,345.6");
        assertEquals("12.34", box.text().toString());
        assertEquals(12.34, box.number(), 1e-9);
    }

    @Test
    void emptyIsNotAnErrorButAPartialNumberIs() {
        var box = TextBox.integer(5, -9, 9);
        assertFalse(box.error());
        assertTrue(Double.isNaN(box.number()));
        type(box, "-");
        assertTrue(box.error());
    }

    @Test
    void maxLengthAndTheNameFilterRefuseEdits() {
        var box = TextBox.text(4).accepting(s -> s.matches("[a-z]*"));
        type(box, "ab1cdef");
        assertEquals("abcd", box.text().toString());
    }

    @Test
    void editKeysMoveDeleteAndSelect() {
        var box = TextBox.text(16);
        type(box, "hello world");
        box.key(GLFW.GLFW_KEY_HOME, 0, 0);
        box.key(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_SHIFT);
        box.key(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_SHIFT);
        assertEquals(0, box.selectionStart());
        assertEquals(2, box.selectionEnd());
        type(box, "J");
        assertEquals("Jllo world", box.text().toString());
        box.key(GLFW.GLFW_KEY_END, 0, 0);
        box.key(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL);
        assertEquals("Jllo ", box.text().toString());
        box.key(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        box.key(GLFW.GLFW_KEY_DELETE, 0, 0);
        assertTrue(box.empty());
    }

    @Test
    void enterKeepsTheText() {
        var box = TextBox.text(8).onSubmit(s -> { });
        type(box, "abc");
        box.key(GLFW.GLFW_KEY_ENTER, 0, 0);
        assertEquals("abc", box.text().toString());
    }

    @Test
    void placingTheCaretClampsAndDragExtendsTheSelection() {
        var box = TextBox.text(8);
        type(box, "abcd");
        box.place(2, false);
        box.place(99, true);
        assertEquals(2, box.selectionStart());
        assertEquals(4, box.selectionEnd());
    }
}
