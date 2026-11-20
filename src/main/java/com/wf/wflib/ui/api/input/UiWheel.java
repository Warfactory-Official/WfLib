package com.wf.wflib.ui.api.input;

/** Wheel handler; true = consumed. {@code item} as for {@link UiAction}. */
@FunctionalInterface
public interface UiWheel {
    boolean wheel(Object item, float amount);
}
