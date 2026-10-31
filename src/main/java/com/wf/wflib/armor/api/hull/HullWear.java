package com.wf.wflib.armor.api.hull;

import com.wf.wflib.geom.api.OBB;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Armour wear per region: 6 hull faces x {@link #CELLS}^2 grid over the main box. Wear 0..1 of a region's durability
 * ({@code nominal mm x} {@link #POINTS_PER_MM}); effective thickness {@code nominal x lerp(1 - wear, wear_floor, 1)}.
 * Every positioned damage wears the region it lands on ({@link Kind} share of the damage), neighbours in the same face
 * by {@code spread}.
 */
public final class HullWear {

    public static final int CELLS = 3;
    public static final int PER_FACE = CELLS * CELLS;
    public static final int COUNT = Face.VALUES.length * PER_FACE;
    /** Durability points per mm of nominal plate. */
    public static final float POINTS_PER_MM = 4.0f;
    /** Below this a face counts as compromised (probe, HUD). */
    public static final float COMPROMISED = 0.5f;
    private static final String TAG = "ArmorWear";

    /** Hull-local face; +X = port = left, +Z forward. */
    public enum Face {
        FRONT(HullFace.FRONT), REAR(HullFace.REAR), LEFT(HullFace.SIDE), RIGHT(HullFace.SIDE),
        TOP(HullFace.TOP), BOTTOM(HullFace.BOTTOM);

        public static final Face[] VALUES = values();
        public final HullFace profile;

        Face(HullFace profile) {
            this.profile = profile;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Damage share that becomes wear points, by cause. */
    public enum Kind {
        KINETIC(0.3f, 0f), BLAST(0.2f, 0.5f), THERMAL(1.2f, 0.35f), COLLISION(0.05f, 0.5f), OTHER(0.2f, 0f);

        public final float share;
        public final float spread;

        Kind(float share, float spread) {
            this.share = share;
            this.spread = spread;
        }
    }

    private final float[] wear = new float[COUNT];
    private boolean dirty;

    public static int index(Face face, int u, int v) {
        return face.ordinal() * PER_FACE + v * CELLS + u;
    }

    public static Face face(int region) {
        return Face.VALUES[region / PER_FACE];
    }

    /**
     * Region at a world point; face by {@code worldNormal} when given, else by the main box side the point is nearest
     * to (normalised by extents).
     *
     * @param box main hull box ({@link HullBody#mainBox})
     * @return -1 = no box
     */
    public static int region(@Nullable OBB box, Vec3 world, @Nullable Vector3f worldNormal) {
        if (box == null) {
            return -1;
        }
        Vector3f[] axes = box.getAxes();
        Vector3f ext = box.extents();
        double rx = world.x - box.centerX();
        double ry = world.y - box.centerY();
        double rz = world.z - box.centerZ();
        // Double until projected: world coords lose the offset in float far out.
        double x = (rx * axes[0].x + ry * axes[0].y + rz * axes[0].z) / Math.max(1.0E-3, ext.x);
        double y = (rx * axes[1].x + ry * axes[1].y + rz * axes[1].z) / Math.max(1.0E-3, ext.y);
        double z = (rx * axes[2].x + ry * axes[2].y + rz * axes[2].z) / Math.max(1.0E-3, ext.z);
        Face face;
        if (worldNormal != null) {
            double nx = worldNormal.dot(axes[0]);
            double ny = worldNormal.dot(axes[1]);
            double nz = worldNormal.dot(axes[2]);
            face = faceOf(nx, ny, nz);
        } else {
            face = faceOf(x, y, z);
        }
        return switch (face) {
            case FRONT, REAR -> index(face, cell(x), cell(y));
            case LEFT, RIGHT -> index(face, cell(z), cell(y));
            case TOP, BOTTOM -> index(face, cell(x), cell(z));
        };
    }

    private static Face faceOf(double x, double y, double z) {
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);
        if (ay >= ax && ay >= az) {
            return y >= 0 ? Face.TOP : Face.BOTTOM;
        }
        if (az >= ax) {
            return z >= 0 ? Face.FRONT : Face.REAR;
        }
        return x >= 0 ? Face.LEFT : Face.RIGHT;
    }

    private static int cell(double normalised) {
        return Mth.clamp((int) Math.floor((normalised + 1) * 0.5 * CELLS), 0, CELLS - 1);
    }

    public float wear(int region) {
        return wear[region];
    }

    public float integrity(int region) {
        return 1 - wear[region];
    }

    /** Lowest integrity over a face's cells. */
    public float integrity(Face face) {
        float min = 1;
        for (int i = 0; i < PER_FACE; i++) {
            min = Math.min(min, 1 - wear[face.ordinal() * PER_FACE + i]);
        }
        return min;
    }

    /** Thickness multiplier at {@code region}; -1 => 1. */
    public float thicknessScale(int region, float wearFloor) {
        return region < 0 ? 1 : Mth.lerp(1 - wear[region], wearFloor, 1);
    }

    /**
     * Wears {@code region} by {@code damage x kind.share} points against {@code nominal mm} of plate; same-face
     * neighbours by {@code kind.spread} of that.
     *
     * @return wear added at the region
     */
    public float apply(int region, float damage, Kind kind, float nominal) {
        if (region < 0 || nominal <= 0 || damage <= 0) {
            return 0;
        }
        float fraction = damage * kind.share / (nominal * POINTS_PER_MM);
        add(region, fraction);
        if (kind.spread > 0) {
            int face = region / PER_FACE;
            int u = region % PER_FACE % CELLS;
            int v = region % PER_FACE / CELLS;
            int[][] around = {{u - 1, v}, {u + 1, v}, {u, v - 1}, {u, v + 1}};
            for (int[] n : around) {
                if (n[0] >= 0 && n[0] < CELLS && n[1] >= 0 && n[1] < CELLS) {
                    add(face * PER_FACE + n[1] * CELLS + n[0], fraction * kind.spread);
                }
            }
        }
        return fraction;
    }

    private void add(int region, float fraction) {
        float before = wear[region];
        wear[region] = Math.min(1, before + fraction);
        dirty |= wear[region] != before;
    }

    /** One repair band off every cell of a face. @return anything restored */
    public boolean repair(Face face, float band) {
        boolean any = false;
        for (int i = 0; i < PER_FACE; i++) {
            int r = face.ordinal() * PER_FACE + i;
            if (wear[r] > 0) {
                wear[r] = Math.max(0, wear[r] - band);
                any = true;
            }
        }
        dirty |= any;
        return any;
    }

    public boolean worn(Face face) {
        return integrity(face) < 1;
    }

    public boolean dirty() {
        return dirty;
    }

    /** Quantised to 1/255; clears {@link #dirty}. */
    public CompoundTag toSync() {
        dirty = false;
        byte[] q = new byte[COUNT];
        for (int i = 0; i < COUNT; i++) {
            q[i] = (byte) Math.round(wear[i] * 255);
        }
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("w", q);
        return tag;
    }

    public void fromSync(CompoundTag tag) {
        byte[] q = tag.getByteArray("w");
        for (int i = 0; i < COUNT && i < q.length; i++) {
            wear[i] = (q[i] & 0xFF) / 255f;
        }
    }

    public void save(CompoundTag compound) {
        int[] bits = new int[COUNT];
        boolean any = false;
        for (int i = 0; i < COUNT; i++) {
            bits[i] = Float.floatToIntBits(wear[i]);
            any |= wear[i] > 0;
        }
        if (any) {
            compound.putIntArray(TAG, bits);
        }
    }

    public void load(CompoundTag compound) {
        int[] bits = compound.getIntArray(TAG);
        for (int i = 0; i < COUNT; i++) {
            wear[i] = i < bits.length ? Float.intBitsToFloat(bits[i]) : 0;
        }
        dirty = true;
    }
}
