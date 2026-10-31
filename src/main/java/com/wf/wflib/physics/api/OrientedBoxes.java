package com.wf.wflib.physics.api;

import org.joml.Quaternionfc;

import java.util.Arrays;

/** World-frame oriented boxes, SoA: centres double (|x| ~1e7), rotation + half extents float. */
public final class OrientedBoxes {

    private double[] centres = new double[3 * 16];
    private float[] frames = new float[7 * 16];
    private int count;

    public void clear() {
        count = 0;
    }

    public int size() {
        return count;
    }

    public void add(double cx, double cy, double cz, Quaternionfc rotation, float ex, float ey, float ez) {
        if (count * 3 == centres.length) {
            centres = Arrays.copyOf(centres, centres.length * 2);
            frames = Arrays.copyOf(frames, frames.length * 2);
        }
        int c = count * 3, f = count * 7;
        centres[c] = cx;
        centres[c + 1] = cy;
        centres[c + 2] = cz;
        frames[f] = rotation.x();
        frames[f + 1] = rotation.y();
        frames[f + 2] = rotation.z();
        frames[f + 3] = rotation.w();
        frames[f + 4] = ex;
        frames[f + 5] = ey;
        frames[f + 6] = ez;
        count++;
    }

    public double centreX(int i) {
        return centres[i * 3];
    }

    public double centreY(int i) {
        return centres[i * 3 + 1];
    }

    public double centreZ(int i) {
        return centres[i * 3 + 2];
    }

    /** Rotation x, y, z, w = component 0..3; half extents x, y, z = 4..6. */
    public float frame(int i, int component) {
        return frames[i * 7 + component];
    }
}
