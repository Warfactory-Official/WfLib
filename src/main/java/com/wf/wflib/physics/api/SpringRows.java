package com.wf.wflib.physics.api;

import java.util.Arrays;

/** Suspension rows of one body, in a stable slot order (warm start by slot). Ungrounded rows hold a slot only. */
public final class SpringRows {

    private int size;
    private boolean[] grounded = new boolean[8];
    private boolean[] onDeck = new boolean[8];
    /** World anchor. */
    private double[] anchors = new double[24];
    /** ratio, compression, min, max, stiffness, damping. */
    private float[] values = new float[48];

    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    public void skip() {
        ensure();
        grounded[size] = false;
        onDeck[size] = false;
        size++;
    }

    public void add(boolean deck, double x, double y, double z, float ratio, float compression, float min, float max,
                    float stiffness, float damping) {
        ensure();
        grounded[size] = true;
        onDeck[size] = deck;
        anchors[size * 3] = x;
        anchors[size * 3 + 1] = y;
        anchors[size * 3 + 2] = z;
        int o = size * 6;
        values[o] = ratio;
        values[o + 1] = compression;
        values[o + 2] = min;
        values[o + 3] = max;
        values[o + 4] = stiffness;
        values[o + 5] = damping;
        size++;
    }

    public boolean grounded(int i) {
        return grounded[i];
    }

    public boolean onDeck(int i) {
        return onDeck[i];
    }

    public double anchor(int i, int axis) {
        return anchors[i * 3 + axis];
    }

    public float ratio(int i) {
        return values[i * 6];
    }

    public float compression(int i) {
        return values[i * 6 + 1];
    }

    public float min(int i) {
        return values[i * 6 + 2];
    }

    public float max(int i) {
        return values[i * 6 + 3];
    }

    public float stiffness(int i) {
        return values[i * 6 + 4];
    }

    public float damping(int i) {
        return values[i * 6 + 5];
    }

    private void ensure() {
        if (size == grounded.length) {
            int n = size * 2;
            grounded = Arrays.copyOf(grounded, n);
            onDeck = Arrays.copyOf(onDeck, n);
            anchors = Arrays.copyOf(anchors, n * 3);
            values = Arrays.copyOf(values, n * 6);
        }
    }

}
