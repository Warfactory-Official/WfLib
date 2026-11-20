package com.wf.wflib.ui.api.input;

/** Handler of a {@code drag_source}; {@code item} = element of the innermost enclosing repeat. */
public interface UiDragSource {
    /** Drag threshold crossed: the payload to carry; null => no drag, release stays a click. */
    Object begin(Object item);

    default void click(Object item) {
    }

    /** Carry over, dropped or cancelled. */
    default void end(Object payload) {
    }
}
