package com.wf.wflib.armor.api.hull;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/** mm RHAe per face vs kinetic rounds; {@code ce}: vs shaped charges, 0 = kinetic value. Gson-bound. */
public class HullFaces {
    @SerializedName("front")
    public float front = 0;

    @SerializedName("side")
    public float side = 0;

    @SerializedName("rear")
    public float rear = 0;

    @SerializedName("top")
    public float top = 0;

    @SerializedName("bottom")
    public float bottom = 0;

    @SerializedName("ce")
    @Nullable
    public HullFaces ce;

    public boolean isDefined() {
        return front > 0 || side > 0 || rear > 0 || top > 0 || bottom > 0;
    }

    public float nominal(HullFace face) {
        return switch (face) {
            case FRONT -> front;
            case SIDE -> side;
            case REAR -> rear;
            case TOP -> top;
            case BOTTOM -> bottom;
        };
    }

    public float nominal(HullFace face, boolean shapedCharge) {
        float ke = nominal(face);
        if (!shapedCharge || ce == null) {
            return ke;
        }
        float c = ce.nominal(face);
        return c > 0 ? c : ke;
    }
}
