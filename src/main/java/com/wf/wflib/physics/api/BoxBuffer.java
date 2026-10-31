package com.wf.wflib.physics.api;

import net.minecraft.world.phys.AABB;

import java.util.List;

/** Boxes as flat double[], not AABB objects: avoids alloc churn, improves cache locality. */
public final class BoxBuffer {

    private static final int STRIDE = 6;

    private double[] data;
    private int count;

    private double minX, minY, minZ, maxX, maxY, maxZ;

    public BoxBuffer() {
        this(64);
    }

    public BoxBuffer(int initialCapacity) {
        data = new double[Math.max(1, initialCapacity) * STRIDE];
        clear();
    }

    public void clear() {
        count = 0;
        minX = Double.POSITIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        minZ = Double.POSITIVE_INFINITY;
        maxX = Double.NEGATIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
        maxZ = Double.NEGATIVE_INFINITY;
    }

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void add(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int base = count * STRIDE;
        if (base + STRIDE > data.length) {
            // Amortised growth; vehicle stops resizing after first few ticks.
            double[] grown = new double[Math.max(data.length * 2, base + STRIDE)];
            System.arraycopy(data, 0, grown, 0, data.length);
            data = grown;
        }
        data[base] = minX;
        data[base + 1] = minY;
        data[base + 2] = minZ;
        data[base + 3] = maxX;
        data[base + 4] = maxY;
        data[base + 5] = maxZ;
        count++;
        if (minX < this.minX) this.minX = minX;
        if (minY < this.minY) this.minY = minY;
        if (minZ < this.minZ) this.minZ = minZ;
        if (maxX > this.maxX) this.maxX = maxX;
        if (maxY > this.maxY) this.maxY = maxY;
        if (maxZ > this.maxZ) this.maxZ = maxZ;
    }

    /** Own aggregate bound vs given bound; true may have no actual box overlap. */
    public boolean anyWithin(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ) {
        return count > 0 && this.maxX > minX && this.minX < maxX && this.maxY > minY
                && this.minY < maxY && this.maxZ > minZ && this.minZ < maxZ;
    }

    public void add(AABB box) {
        add(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public void addAll(List<AABB> boxes) {
        for (int i = 0, size = boxes.size(); i < size; i++) {
            add(boxes.get(i));
        }
    }

    public double minX(int i) {
        return data[i * STRIDE];
    }

    public double minY(int i) {
        return data[i * STRIDE + 1];
    }

    public double minZ(int i) {
        return data[i * STRIDE + 2];
    }

    public double maxX(int i) {
        return data[i * STRIDE + 3];
    }

    public double maxY(int i) {
        return data[i * STRIDE + 4];
    }

    public double maxZ(int i) {
        return data[i * STRIDE + 5];
    }

    /** Debug/diagnostics only, not the physics hot path. */
    public AABB get(int i) {
        int base = i * STRIDE;
        return new AABB(data[base], data[base + 1], data[base + 2],
                data[base + 3], data[base + 4], data[base + 5]);
    }

}
