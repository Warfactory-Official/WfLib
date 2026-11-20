package com.wf.wflib.ui.api.input;

/** Called every frame the pointer is over a {@code Hover} box; {@code item} as for {@link UiAction}. */
@FunctionalInterface
public interface UiHover {
    void over(Object item);
}
