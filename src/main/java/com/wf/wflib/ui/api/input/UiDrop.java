package com.wf.wflib.ui.api.input;

/** Receiver of a carried payload; {@code x}, {@code y} local to the target's top-left, {@code item} = its repeat item. */
public interface UiDrop {
    /** Every frame the pointer is over the target while a payload is carried. */
    default void hover(Object item, Object payload, float x, float y) {
    }

    void drop(Object item, Object payload, float x, float y);
}
