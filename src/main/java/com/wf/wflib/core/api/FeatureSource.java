package com.wf.wflib.core.api;

import java.util.List;

/**
 * A group of switches another mod keeps in its own config but wants on the master switchboard screen ({@code /wflibfeatures}), next to wflib's: a bridge
 * to another mod, say. The screen asks it for the group, shows the boxes, and writes the choices back and asks it to save when the screen closes. wflib's own
 * groups come from {@code FeatureCatalogue} and {@code wflib-features.toml}; a source's are the source's. Register once at construction with {@link Features#register}.
 */
public interface FeatureSource {
    /** One box. */
    record Box(String id, String title, String blurb) {
    }

    /** Unique across wflib's groups and every source. */
    String id();

    String title();

    String blurb();

    List<Box> boxes();

    /** The group's master switch as saved. */
    boolean master();

    /** A box as saved. */
    boolean get(String box);

    /** What the game started with: what a restart would leave unchanged. Boxes read once at launch return the same as {@link #get} until edited. */
    boolean startup(String box);

    boolean startupMaster();

    void set(String box, boolean on);

    void master(boolean on);

    /** Called once after a screen's changes were written. */
    void save();
}
