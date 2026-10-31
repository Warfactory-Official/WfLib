package com.wf.wflib.physics.rigid;

import com.wf.wflib.physics.api.ConvexHull;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Java {@link ConvexContacts} vs {@code native/narrow.zig}: the per-shape loops of {@code StepBody.worldContacts}, bitwise. */
class NarrowParityTest {

    private static final float MARGIN = 0.1f;
    private static final float DEPTH = 1.0f;
    private static final int SCENES = 300;

    /** FIXME: skips (not passes) when natives are not built for this platform; CI must build them (buildNatives, zig). */
    @Test
    void javaAndNativeNarrowphaseAgreeBitwise() {
        Assumptions.assumeTrue(NarrowNative.available(), "FIXME: natives not built; parity unchecked");
        Random random = new Random(0x5EEDL);
        NarrowNative nat = new NarrowNative();
        ConvexContacts narrow = new ConvexContacts();
        ContactBuffer java = new ContactBuffer();
        Polytope shape = new Polytope(), box = new Polytope(), fill = new Polytope();
        int compared = 0, boxContacts = 0, fillContacts = 0, hullShapes = 0;
        float[] envelope = {-100, -100, -100, 100, 100, 100};

        for (int scene = 0; scene < SCENES; scene++) {
            int shapeCount = 1 + random.nextInt(3);
            float[] frames = new float[shapeCount * NarrowNative.FRAME];
            long[] handles = new long[shapeCount];
            ConvexHull[] hulls = new ConvexHull[shapeCount];
            for (int s = 0; s < shapeCount; s++) {
                int o = s * NarrowNative.FRAME;
                Quaternionf q = new Quaternionf().rotateYXZ(random.nextFloat() * 6.28f, (random.nextFloat() - 0.5f) * 1.2f,
                        (random.nextFloat() - 0.5f) * 1.2f).normalize();
                frames[o] = random.nextFloat() * 4 - 2;
                frames[o + 1] = random.nextFloat() * 2 - 0.5f;
                frames[o + 2] = random.nextFloat() * 4 - 2;
                frames[o + 3] = q.x;
                frames[o + 4] = q.y;
                frames[o + 5] = q.z;
                frames[o + 6] = q.w;
                frames[o + 7] = 0.3f + random.nextFloat();
                frames[o + 8] = 0.3f + random.nextFloat();
                frames[o + 9] = 0.3f + random.nextFloat();
                frames[o + 10] = 3;
                if (random.nextInt(3) != 0) {
                    hulls[s] = hull(random);
                    handles[s] = NarrowNative.hull(hulls[s]);
                    hullShapes++;
                }
            }
            int boxCount = 1 + random.nextInt(8);
            float[] boxes = new float[boxCount * 6];
            for (int i = 0; i < boxCount; i++) {
                float x = random.nextFloat() * 6 - 3, y = random.nextFloat() * 3 - 1, z = random.nextFloat() * 6 - 3;
                boxes[i * 6] = x;
                boxes[i * 6 + 1] = y;
                boxes[i * 6 + 2] = z;
                boxes[i * 6 + 3] = x + 0.2f + random.nextFloat() * 2;
                boxes[i * 6 + 4] = y + 0.2f + random.nextFloat() * 2;
                boxes[i * 6 + 5] = z + 0.2f + random.nextFloat() * 2;
            }
            int triangleCount = random.nextInt(7);
            float[] triangles = new float[triangleCount * 13];
            for (int c = 0; c < triangleCount; c++) {
                float ox = random.nextInt(32) - 16 + random.nextFloat(), oy = random.nextInt(32) - 16, oz = random.nextInt(32) - 16;
                float cx = random.nextFloat() * 5 - 2.5f, cy = random.nextFloat() * 2 - 0.5f, cz = random.nextFloat() * 5 - 2.5f;
                for (int v = 0; v < 3; v++) {
                    triangles[c * 13 + v * 3] = cx + random.nextFloat() * 3 - 1.5f + ox;
                    triangles[c * 13 + v * 3 + 1] = cy + random.nextFloat() * 1.5f - 0.75f + oy;
                    triangles[c * 13 + v * 3 + 2] = cz + random.nextFloat() * 3 - 1.5f + oz;
                }
                triangles[c * 13 + 9] = ox;
                triangles[c * 13 + 10] = oy;
                triangles[c * 13 + 11] = oz;
                triangles[c * 13 + 12] = Float.intBitsToFloat(c);
            }

            int count = nat.worldContacts(shapeCount, frames, handles, false, envelope, boxes, boxCount, triangles, triangleCount,
                    MARGIN, DEPTH);
            int next = 0;
            float[] triangle = new float[9];
            for (int s = 0; s < shapeCount; s++) {
                int o = s * NarrowNative.FRAME;
                Quaternionf q = new Quaternionf(frames[o + 3], frames[o + 4], frames[o + 5], frames[o + 6]);
                if (hulls[s] != null) {
                    shape.hull(hulls[s], frames[o], frames[o + 1], frames[o + 2], q);
                } else {
                    shape.box(frames[o], frames[o + 1], frames[o + 2], q, frames[o + 7], frames[o + 8], frames[o + 9]);
                }
                shape.tag = s;
                java.clear();
                for (int i = 0; i < boxCount; i++) {
                    if (Polytope.boundsMeet(boxes[i * 6], boxes[i * 6 + 1], boxes[i * 6 + 2], boxes[i * 6 + 3], boxes[i * 6 + 4],
                            boxes[i * 6 + 5], shape, MARGIN)) {
                        box.box(boxes[i * 6], boxes[i * 6 + 1], boxes[i * 6 + 2], boxes[i * 6 + 3], boxes[i * 6 + 4], boxes[i * 6 + 5]);
                        box.tag = i;
                        narrow.collide(box, shape, MARGIN, java);
                    }
                }
                boxContacts += java.size();
                for (int c = 0; c < triangleCount; c++) {
                    System.arraycopy(triangles, c * 13, triangle, 0, 9);
                    fill.prism(triangle, 0, triangles[c * 13 + 9], triangles[c * 13 + 10], triangles[c * 13 + 11], DEPTH);
                    fill.tag = c;
                    if (fill.boundsMeet(shape, MARGIN)) {
                        int before = java.size();
                        narrow.collide(fill, shape, MARGIN, java);
                        fillContacts += java.size() - before;
                    }
                }
                for (int k = 0; k < java.size(); k++, next++) {
                    String at = "scene " + scene + " shape " + s + " contact " + k;
                    assertTrue(next < count && nat.shape(next) == s, at + ": native has no record");
                    assertBits(java.x(k), nat.x(next), at + " x");
                    assertBits(java.y(k), nat.y(next), at + " y");
                    assertBits(java.z(k), nat.z(next), at + " z");
                    assertBits(java.normalX(k), nat.normalX(next), at + " nx");
                    assertBits(java.normalY(k), nat.normalY(next), at + " ny");
                    assertBits(java.normalZ(k), nat.normalZ(next), at + " nz");
                    assertBits(java.separation(k), nat.separation(next), at + " separation");
                    assertEquals(java.tagA(k), nat.tagA(next), at + " tagA");
                    assertEquals(java.tagB(k), nat.tagB(next), at + " tagB");
                    compared++;
                }
            }
            assertEquals(next, count, "scene " + scene + ": native has extra records");
        }
        assertTrue(boxContacts > 300 && fillContacts > 100 && hullShapes > 100,
                "scenes too sparse: " + boxContacts + " box, " + fillContacts + " fill, " + hullShapes + " hulls");
        assertEquals(boxContacts + fillContacts, compared);
    }

    private static void assertBits(float expected, float actual, String what) {
        assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual), what + ": " + expected + " vs " + actual);
    }

    /** Random convex prism (planar caps, vertical sides) or pyramid on an ellipse. */
    private static ConvexHull hull(Random random) {
        int n = 3 + random.nextInt(6);
        float[] angle = new float[n];
        for (int i = 0; i < n; i++) {
            angle[i] = (i + random.nextFloat() * 0.8f) * 6.2831855f / n;
        }
        float rx = 0.4f + random.nextFloat(), rz = 0.4f + random.nextFloat();
        boolean pyramid = random.nextBoolean();
        float height = 0.4f + random.nextFloat();
        int total = pyramid ? n + 1 : 2 * n;
        float[] points = new float[total * 3];
        for (int i = 0; i < n; i++) {
            float x = (float) Math.cos(angle[i]) * rx, z = (float) Math.sin(angle[i]) * rz;
            points[i * 3] = x;
            points[i * 3 + 1] = -height / 2;
            points[i * 3 + 2] = z;
            if (!pyramid) {
                points[(n + i) * 3] = x;
                points[(n + i) * 3 + 1] = height / 2;
                points[(n + i) * 3 + 2] = z;
            }
        }
        int[][] faces;
        if (pyramid) {
            points[n * 3 + 1] = height / 2;
            faces = new int[n + 1][];
            faces[0] = new int[n];
            for (int i = 0; i < n; i++) {
                faces[0][i] = n - 1 - i;
                faces[1 + i] = new int[]{i, (i + 1) % n, n};
            }
        } else {
            faces = new int[n + 2][];
            faces[0] = new int[n];
            faces[1] = new int[n];
            for (int i = 0; i < n; i++) {
                faces[0][i] = n - 1 - i;
                faces[1][i] = n + i;
                faces[2 + i] = new int[]{i, (i + 1) % n, n + (i + 1) % n, n + i};
            }
        }
        return ConvexHull.of(points, faces);
    }
}
