package com.wf.wflib.ui.api.input;

import java.util.Objects;

/**
 * One carried payload per {@link UiInput}. Targets {@link #offer} themselves while drawing under the pointer; the
 * published offers of the last frame resolve a release (topmost = last offered), like the hit table.
 */
public final class UiDnd {
    public record Offer(UiDrop target, Object item, float x, float y) {
    }

    private Object payload;
    private Offer recording;
    private Offer shown;

    public boolean active() {
        return payload != null;
    }

    public Object payload() {
        return payload;
    }

    public void begin(Object payload) {
        this.payload = Objects.requireNonNull(payload);
        recording = null;
        shown = null;
    }

    /** Ends the carry; the target under the pointer in the last frame, or null. */
    public Offer end() {
        Offer at = shown;
        payload = null;
        recording = null;
        shown = null;
        return at;
    }

    public void offer(UiDrop target, Object item, float x, float y) {
        recording = new Offer(target, item, x, y);
    }

    void endFrame() {
        shown = recording;
        recording = null;
    }

    public void cancel() {
        payload = null;
        recording = null;
        shown = null;
    }
}
