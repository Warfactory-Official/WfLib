package com.wf.wflib.physics.api;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/** Frame arithmetic shared by a carrier and the objects standing on it. */
public final class DeckFrame {

    private DeckFrame() {}

    /** Pitch where yaw/roll become inseparable; guards against NaN in decomposition. */
    private static final float GIMBAL_LIMIT = 0.99999f;

    // ---------------------------------------------------------------- points

    public static Vector3d toLocal(Quaternionf inverse, double pivotX, double pivotY, double pivotZ,
                                   double worldX, double worldY, double worldZ, Vector3d dest) {
        dest.set(worldX - pivotX, worldY - pivotY, worldZ - pivotZ);
        return inverse.transform(dest);
    }

    public static Vector3d toWorld(Quaternionf rotation, double pivotX, double pivotY, double pivotZ,
                                   double localX, double localY, double localZ, Vector3d dest) {
        dest.set(localX, localY, localZ);
        rotation.transform(dest);
        return dest.add(pivotX, pivotY, pivotZ);
    }

    // ---------------------------------------------------------------- rotations

    public static Quaternionf toLocalRotation(Quaternionf carrierInverse, Quaternionf child,
                                              Quaternionf dest) {
        return carrierInverse.mul(child, dest);
    }

    public static Quaternionf toWorldRotation(Quaternionf carrier, Quaternionf local,
                                              Quaternionf dest) {
        return carrier.mul(local, dest);
    }

    /** Must match the carrier's rotYXZ() convention exactly (yaw negated). */
    public static float[] toEulerYXZ(Quaternionf rotation, Matrix3f scratch, float[] out) {
        Matrix3f m = rotation.get(scratch);
        // JOML is column-major and names its elements m<column><row>, so the mathematical element
        // at (row r, column c) is the field m{c}{r}. Working from R = Ry(a) Rx(b) Rz(c):
        //   (1,2) = -sin b        (1,0) = cos b sin c    (1,1) = cos b cos c
        //   (0,2) =  sin a cos b  (2,2) = cos a cos b
        float sinPitch = -m.m21;
        float pitch;
        float yawRad;
        float rollRad;
        if (sinPitch > GIMBAL_LIMIT || sinPitch < -GIMBAL_LIMIT) {
            // Gimbal lock: cos(pitch)=0 => both quotients 0/0, yaw/roll conflated. All to yaw, roll=0;
            // matches what the rest of the physics does with a vertical hull anyway.
            pitch = sinPitch > 0 ? (float) java.lang.Math.PI * 0.5f : (float) -java.lang.Math.PI * 0.5f;
            yawRad = (float) java.lang.Math.atan2(-m.m02, m.m00);
            rollRad = 0;
        } else {
            pitch = (float) java.lang.Math.asin(sinPitch);
            yawRad = (float) java.lang.Math.atan2(m.m20, m.m22);
            rollRad = (float) java.lang.Math.atan2(m.m01, m.m11);
        }
        out[0] = (float) -java.lang.Math.toDegrees(yawRad);
        out[1] = (float) java.lang.Math.toDegrees(pitch);
        out[2] = (float) java.lang.Math.toDegrees(rollRad);
        return out;
    }

    /** Inverse of toEulerYXZ. */
    public static Quaternionf fromEulerYXZ(float yaw, float pitch, float roll, Quaternionf dest) {
        return dest.identity()
                .rotateY((float) java.lang.Math.toRadians(-yaw))
                .rotateX((float) java.lang.Math.toRadians(pitch))
                .rotateZ((float) java.lang.Math.toRadians(roll));
    }

    // ---------------------------------------------------------------- box selection

    public static int select(float[] boxes, int count,
                             double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ, BoxSink sink) {
        int emitted = 0;
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            if (boxes[o] > maxX || boxes[o + 3] < minX
                    || boxes[o + 1] > maxY || boxes[o + 4] < minY
                    || boxes[o + 2] > maxZ || boxes[o + 5] < minZ) {
                continue;
            }
            sink.box(boxes[o], boxes[o + 1], boxes[o + 2],
                    boxes[o + 3], boxes[o + 4], boxes[o + 5]);
            emitted++;
        }
        return emitted;
    }

    @FunctionalInterface
    public interface BoxSink {

        void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

    }

    // ---------------------------------------------------------------- support

    /** NaN when no surface within reach. */
    public static double supportUnder(float[] boxes, int count,
                                      double x, double y, double z, double reach) {
        double best = Double.NaN;
        double bestGap = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            if (x < boxes[o] || x > boxes[o + 3] || z < boxes[o + 2] || z > boxes[o + 5]) {
                continue;
            }
            double gap = Math.abs(boxes[o + 4] - y);
            if (gap <= reach && gap < bestGap) {
                bestGap = gap;
                best = boxes[o + 4];
            }
        }
        return best;
    }

    /** Wake test for sleeping vehicles; cheap probe. */
    public static boolean supportsAt(float[] boxes, int count,
                                     double x, double z, double top, double tolerance) {
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            if (x < boxes[o] || x > boxes[o + 3] || z < boxes[o + 2] || z > boxes[o + 5]) {
                continue;
            }
            if (Math.abs(boxes[o + 4] - top) <= tolerance) {
                return true;
            }
        }
        return false;
    }

}
