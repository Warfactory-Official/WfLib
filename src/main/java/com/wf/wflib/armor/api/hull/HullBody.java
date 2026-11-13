package com.wf.wflib.armor.api.hull;

import com.wf.wflib.geom.api.OBB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;

/** An armoured hull {@link HullResolver} resolves against. */
public interface HullBody {

    /** Hull -> world; caller-owned copy. */
    Quaternionf rotYXZ();

    /** World box of the main hull, its axes = hull frame; null = none. */
    @Nullable
    OBB mainBox();

    /** Null => {@link #getOBBs} are the hit surface. */
    @Nullable
    HullMesh hits();

    /** World structure boxes. */
    List<OBB> getOBBs();

    HullWear hullWear();

    @Nullable
    HullProfile hullProfile();

    /** Brick {@code cell} of the integral ERA array on plate {@code plateId} still live? Default: every brick. */
    default boolean eraLive(String plateId, int cell) {
        return true;
    }
}
