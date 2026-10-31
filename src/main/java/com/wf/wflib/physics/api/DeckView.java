package com.wf.wflib.physics.api;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/** Carrier deck under a body, frozen at capture: pieces in the carrier frame + carrier pose. */
public interface DeckView {

    boolean active();

    @Nullable
    PhysicsBody carrier();

    int deckCount();

    /** Piece i: centre (0-2), extents (3-5), rotation (6-9), carrier frame. */
    float deckFrame(int i, int k);

    @Nullable
    ConvexHull deckHull(int i);

    /** Landing piece: contacts over its top read as its top. */
    boolean landing(int i);

    Quaternionf deckRotation();

    double pivotX();

    double pivotY();

    double pivotZ();

    /** Island task: support rows standing on the deck this step. */
    void supportContacts(int count);

}
