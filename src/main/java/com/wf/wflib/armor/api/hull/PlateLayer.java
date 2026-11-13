package com.wf.wflib.armor.api.hull;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * One layer along the plate normal: solid ({@code mm} + {@code mat}), an air {@code gap} in metres, or an integral
 * explosive-reactive array ({@code era} type id + {@code cells}).
 */
public record PlateLayer(@SerializedName("mm") float mm, @SerializedName("mat") @Nullable String mat,
                         @SerializedName("gap") float gap, @SerializedName("era") @Nullable String era,
                         @SerializedName("cells") int cells) {

    public PlateLayer(float mm, @Nullable String mat, float gap) {
        this(mm, mat, gap, null, 0);
    }

    public boolean isGap() {
        return gap > 0;
    }

    public boolean isEra() {
        return era != null;
    }

    /** Unknown {@code mat} => {@link IllegalArgumentException}. */
    public PlateMaterial material() {
        return PlateMaterials.get(mat);
    }
}
