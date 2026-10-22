package com.wf.wflib.config;

/**
 * Blocks from a player within which wflib keeps missiles and drones real entities and sends rounds. Distant entity
 * tracking past the server view distance = Boxy {@code boxy:distant} tag.
 */
public final class ViewRange {
    public static double blocks = 1536.0;
    /** Past {@link #blocks}: off-world missiles/drones drawn as client ghosts out to this. */
    public static double sim = 4096.0;

    private ViewRange() {
    }
}
