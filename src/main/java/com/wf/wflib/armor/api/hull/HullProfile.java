package com.wf.wflib.armor.api.hull;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hull faces, slope included. All-zero => unarmoured. {@code parts}: a part's own faces in its frame (turret).
 * {@code zones}: boxes overriding both (weak spots). {@code plates}: named plates bound by the mesh
 * ({@link HullMesh.Strike#plate}), overriding zones.
 */
public class HullProfile extends HullFaces {

    /** Thickness fraction a fully worn {@link HullWear} region keeps. */
    @SerializedName("wear_floor")
    public float wearFloor = 0.6f;

    /** Part id -> faces in the part's rest frame. */
    @SerializedName("parts")
    public Map<String, HullFaces> parts = Map.of();

    @Override
    public boolean isDefined() {
        return super.isDefined() || !plates.isEmpty();
    }

    /** Compiles every plate; an invalid one => {@link IllegalStateException} naming it. */
    public void validate() {
        plates.forEach((id, spec) -> spec.compile(id));
    }

    /** Plate id -> plate; call {@link #validate} after loading. */
    @SerializedName("plates")
    public Map<String, PlateSpec> plates = Map.of();

    /** First match wins. */
    @SerializedName("zones")
    public List<Zone> zones = List.of();

    /**
     * Box in model coordinates at rest ({@code part}: that part's rest pose, moves with it). RHAe, slope included,
     * incidence vs the face.
     *
     * @param ce    0 = {@code ke}
     * @param face  null = any
     */
    public record Zone(@SerializedName("name") String name, @SerializedName("part") @Nullable String part,
                       @SerializedName("face") @Nullable HullFace face,
                       @SerializedName("min") float[] min, @SerializedName("max") float[] max,
                       @SerializedName("ke") float ke, @SerializedName("ce") float ce) {

        public boolean matches(@Nullable String part, HullFace face, float x, float y, float z) {
            return Objects.equals(this.part, part) && (this.face == null || this.face == face)
                    && x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2];
        }

        public float nominal(boolean shapedCharge) {
            return shapedCharge && ce > 0 ? ce : ke;
        }
    }
}
