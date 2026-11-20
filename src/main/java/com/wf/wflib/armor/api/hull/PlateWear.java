package com.wf.wflib.armor.api.hull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wear + chip marks per {@link PlateMaterial#brittle} plate, keyed by plate id. Thickness scale
 * {@code lerp(1 - wear, FLOOR, 1)}; every hit adds at least {@link #MIN_HIT}. Chips: owning part's rest frame, mm
 * shorts: {@code a = x<<16 | y}, {@code b = z<<16 | node<<8 | variant<<4 | size}; the oldest drops past {@link #MAX_CHIPS}.
 */
public final class PlateWear {

    public static final int MAX_CHIPS = 8;
    public static final float FLOOR = 0.2f;
    public static final float MIN_HIT = 1.0f / 6;
    /** Durability points per mm of nominal plate. */
    public static final float POINTS_PER_MM = 4.0f;
    public static final int VARIANTS = 4;
    private static final String TAG = "PlateWear";

    public static final class State {
        private float wear;
        private int count;
        private final int[] words = new int[MAX_CHIPS * 2];

        public float wear() {
            return wear;
        }

        public int count() {
            return count;
        }

        /** Rest-frame metres into {@code out}. */
        public Vector3f point(int chip, Vector3f out) {
            return out.set((short) (words[chip * 2] >> 16) / 1000.0f, (short) words[chip * 2] / 1000.0f,
                    (short) (words[chip * 2 + 1] >> 16) / 1000.0f);
        }

        /** {@link HullMesh} node struck. */
        public int node(int chip) {
            return words[chip * 2 + 1] >> 8 & 0xFF;
        }

        public int variant(int chip) {
            return words[chip * 2 + 1] >> 4 & 0xF;
        }

        /** 0..15. */
        public int size(int chip) {
            return words[chip * 2 + 1] & 0xF;
        }

        private void add(int node, Vector3f rest, int variant, int size) {
            if (count == MAX_CHIPS) {
                System.arraycopy(words, 2, words, 0, (MAX_CHIPS - 1) * 2);
                count--;
            }
            words[count * 2] = (quantise(rest.x) << 16) | quantise(rest.y) & 0xFFFF;
            words[count * 2 + 1] = (quantise(rest.z) << 16) | node << 8 | variant << 4 | size;
            count++;
        }

        private static int quantise(float metres) {
            return Mth.clamp(Math.round(metres * 1000.0f), Short.MIN_VALUE, Short.MAX_VALUE) & 0xFFFF;
        }
    }

    private final Map<String, State> plates = new HashMap<>();
    private boolean dirty;

    @Nullable
    public State state(String plate) {
        return plates.get(plate);
    }

    public float wear(String plate) {
        State s = plates.get(plate);
        return s == null ? 0 : s.wear;
    }

    public float thicknessScale(String plate) {
        return Mth.lerp(1 - wear(plate), FLOOR, 1);
    }

    /**
     * Chips {@code plate} at {@code rest} on mesh {@code node} (0..255): wear {@code max(MIN_HIT, damage / (nominal x POINTS_PER_MM))}.
     *
     * @return wear added
     */
    public float hit(String plate, int node, Vector3f rest, float damage, float nominal) {
        if (node < 0 || node > 255) {
            throw new IllegalArgumentException("node " + node + " outside 0..255");
        }
        State s = plates.computeIfAbsent(plate, k -> new State());
        float fraction = Math.max(MIN_HIT, nominal > 0 ? Math.max(damage, 0) / (nominal * POINTS_PER_MM) : 1.0f);
        int hash = Math.round(rest.x * 1000) * 31 + Math.round(rest.y * 1000) * 17 + Math.round(rest.z * 1000) * 7;
        s.add(node, rest, Math.floorMod(hash, VARIANTS), Math.min(15, 4 + Math.round(fraction * 12)));
        s.wear = Math.min(1, s.wear + fraction);
        dirty = true;
        return fraction;
    }

    /** @return anything restored */
    public boolean repair(String plate) {
        boolean any = plates.remove(plate) != null;
        dirty |= any;
        return any;
    }

    /** Plates with state; live view. */
    public Set<String> plates() {
        return Collections.unmodifiableSet(plates.keySet());
    }

    public boolean worn() {
        return !plates.isEmpty();
    }

    public boolean dirty() {
        return dirty;
    }

    /** Clears {@link #dirty}. */
    public CompoundTag toSync() {
        dirty = false;
        return write();
    }

    public void fromSync(CompoundTag tag) {
        read(tag);
    }

    public void save(CompoundTag compound) {
        if (!plates.isEmpty()) {
            compound.put(TAG, write());
        }
    }

    public void load(CompoundTag compound) {
        read(compound.getCompound(TAG));
        dirty = true;
    }

    private CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, State> e : plates.entrySet()) {
            State s = e.getValue();
            int[] out = new int[2 + s.count * 2];
            out[0] = Float.floatToIntBits(s.wear);
            out[1] = s.count;
            System.arraycopy(s.words, 0, out, 2, s.count * 2);
            tag.putIntArray(e.getKey(), out);
        }
        return tag;
    }

    private void read(CompoundTag tag) {
        plates.clear();
        for (String key : tag.getAllKeys()) {
            int[] in = tag.getIntArray(key);
            if (in.length < 2) {
                continue;
            }
            State s = new State();
            s.wear = Float.intBitsToFloat(in[0]);
            s.count = Math.min(Math.min(in[1], MAX_CHIPS), (in.length - 2) / 2);
            Arrays.fill(s.words, 0);
            System.arraycopy(in, 2, s.words, 0, s.count * 2);
            plates.put(key, s);
        }
    }
}
