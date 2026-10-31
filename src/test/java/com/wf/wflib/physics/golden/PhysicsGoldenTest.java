package com.wf.wflib.physics.golden;

import com.wf.wflib.geom.api.OBB;
import com.wf.wflib.physics.api.BoxBuffer;
import com.wf.wflib.physics.api.ConvexHull;
import com.wf.wflib.physics.api.SurfaceBake;
import com.wf.wflib.physics.api.SurfaceSection;
import com.wf.wflib.physics.api.SweptHull;
import com.wf.wflib.physics.rigid.ContactBuffer;
import com.wf.wflib.physics.rigid.ConvexContacts;
import com.wf.wflib.physics.rigid.NarrowNative;
import com.wf.wflib.physics.rigid.Polytope;
import com.wf.wflib.physics.rigid.RigidSolver;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Leaf engine pinned bit-equal to its source copy: hashes recorded from this source run against that copy's classes. */
class PhysicsGoldenTest {

    private static final long CONTACTS = -4910368719769859567L;
    private static final long NATIVE = -937110315176602667L;
    private static final long SOLVER = 7702308298427107883L;
    private static final long BAKE = 704241629330918024L;
    private static final long SWEEP = -5109809745535839280L;

    @Test
    void contactsMatchTheSource() {
        assertEquals(CONTACTS, contacts(), "contacts");
    }

    @Test
    void nativeContactsMatchTheSource() {
        Assumptions.assumeTrue(NarrowNative.available(), "natives not built");
        assertEquals(NATIVE, nativeContacts(), "native");
    }

    @Test
    void solverMatchesTheSource() {
        assertEquals(SOLVER, solver(), "solver");
    }

    @Test
    void bakeMatchesTheSource() {
        assertEquals(BAKE, bake(), "bake");
    }

    @Test
    void sweepMatchesTheSource() {
        assertEquals(SWEEP, sweep(), "sweep");
    }

    public static void main(String[] args) {
        System.out.println("CONTACTS " + contacts());
        System.out.println("NATIVE " + (NarrowNative.available() ? nativeContacts() : "unavailable"));
        System.out.println("SOLVER " + solver());
        System.out.println("BAKE " + bake());
        System.out.println("SWEEP " + sweep());
    }

    private static long mix(long h, long v) {
        h ^= v;
        h *= 0x100000001B3L;
        return h ^ (h >>> 29);
    }

    private static long mix(long h, float v) {
        return mix(h, Float.floatToRawIntBits(v));
    }

    private static long mix(long h, double v) {
        return mix(h, Double.doubleToRawLongBits(v));
    }

    private static long buffer(long h, ContactBuffer b) {
        h = mix(h, b.size());
        for (int k = 0; k < b.size(); k++) {
            h = mix(h, b.x(k));
            h = mix(h, b.y(k));
            h = mix(h, b.z(k));
            h = mix(h, b.normalX(k));
            h = mix(h, b.normalY(k));
            h = mix(h, b.normalZ(k));
            h = mix(h, b.separation(k));
            h = mix(h, b.tagA(k));
            h = mix(h, b.tagB(k));
        }
        return h;
    }

    static long contacts() {
        Random random = new Random(11);
        ConvexContacts narrow = new ConvexContacts();
        ContactBuffer out = new ContactBuffer();
        Polytope a = new Polytope(), b = new Polytope();
        long h = 1;
        for (int scene = 0; scene < 2000; scene++) {
            Quaternionf qa = rotation(random), qb = rotation(random);
            if (random.nextBoolean()) {
                a.hull(hull(random), random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, qa);
            } else {
                a.box(0, 0, 0, qa, 0.3f + random.nextFloat(), 0.3f + random.nextFloat(), 0.3f + random.nextFloat());
            }
            float x = random.nextFloat() * 2 - 1, y = random.nextFloat() * 2 - 0.5f, z = random.nextFloat() * 2 - 1;
            if (random.nextBoolean()) {
                b.hull(hull(random), x, y, z, qb);
            } else {
                b.box(x, y, z, qb, 0.3f + random.nextFloat(), 0.3f + random.nextFloat(), 0.3f + random.nextFloat());
            }
            a.tag = scene;
            b.tag = -scene;
            out.clear();
            narrow.collide(a, b, 0.1f, out);
            h = buffer(h, out);
        }
        return h;
    }

    static long nativeContacts() {
        Random random = new Random(12);
        NarrowNative nat = new NarrowNative();
        long h = 1;
        float[] envelope = {-100, -100, -100, 100, 100, 100};
        for (int scene = 0; scene < 500; scene++) {
            int shapeCount = 1 + random.nextInt(3);
            float[] frames = new float[shapeCount * NarrowNative.FRAME];
            long[] handles = new long[shapeCount];
            for (int s = 0; s < shapeCount; s++) {
                int o = s * NarrowNative.FRAME;
                Quaternionf q = rotation(random);
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
                    handles[s] = NarrowNative.hull(hull(random));
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
                float ox = random.nextInt(32) - 16, oy = random.nextInt(32) - 16, oz = random.nextInt(32) - 16;
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
            int count = nat.worldContacts(shapeCount, frames, handles, false, envelope, boxes, boxCount, triangles,
                    triangleCount, 0.1f, 1.0f);
            h = mix(h, count);
            for (int k = 0; k < count; k++) {
                h = mix(h, nat.x(k));
                h = mix(h, nat.y(k));
                h = mix(h, nat.z(k));
                h = mix(h, nat.normalX(k));
                h = mix(h, nat.normalY(k));
                h = mix(h, nat.normalZ(k));
                h = mix(h, nat.separation(k));
                h = mix(h, nat.tagA(k));
                h = mix(h, nat.tagB(k));
                h = mix(h, nat.shape(k));
            }
        }
        return h;
    }

    /** Random stacks on a ground box, hulls and boxes, springs on every third scene; per-tick pose bits. */
    static long solver() {
        Random random = new Random(13);
        RigidSolver s = new RigidSolver(RigidSolver.SUBSTEPS, 1);
        ConvexContacts narrow = new ConvexContacts();
        ContactBuffer buffer = new ContactBuffer();
        Polytope ground = new Polytope().box(-50, -1, -50, 50, 0, 50);
        Polytope shape = new Polytope(), other = new Polytope();
        long h = 1;
        for (int scene = 0; scene < 12; scene++) {
            int n = 1 + random.nextInt(4);
            List<float[]> bodies = new ArrayList<>();
            List<Quaternionf> rotations = new ArrayList<>();
            List<ConvexHull> hulls = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                float ex = 0.3f + random.nextFloat() * 0.7f, ey = 0.3f + random.nextFloat() * 0.5f, ez = 0.3f + random.nextFloat() * 0.7f;
                // ex ey ez mass | pos(3) vel(3) spin(3)
                bodies.add(new float[]{ex, ey, ez, 0.5f + random.nextFloat() * 4,
                        random.nextFloat() * 1.5f - 0.75f, 0.6f + i * 1.3f + random.nextFloat() * 0.5f, random.nextFloat() * 1.5f - 0.75f,
                        random.nextFloat() * 0.2f - 0.1f, 0, random.nextFloat() * 0.2f - 0.1f, 0, 0, 0});
                rotations.add(rotation(random));
                hulls.add(random.nextInt(3) == 0 ? hull(random) : null);
            }
            boolean springs = scene % 3 == 0;
            for (int t = 0; t < 120; t++) {
                s.reset(0, 0, 0);
                s.addBody();
                for (int i = 0; i < n; i++) {
                    float[] b = bodies.get(i);
                    RigidSolver.Body body = s.addBody();
                    body.inverseMass = 1 / b[3];
                    float k = b[3] / 3;
                    Matrix3f r = new Matrix3f().set(rotations.get(i));
                    Matrix3f local = new Matrix3f().scaling(1 / (k * (b[1] * b[1] + b[2] * b[2])),
                            1 / (k * (b[0] * b[0] + b[2] * b[2])), 1 / (k * (b[0] * b[0] + b[1] * b[1])));
                    r.mul(local, body.inverseInertia).mul(r.transpose(new Matrix3f()));
                    body.com.set(b[4], b[5], b[6]);
                    body.velocity.set(b[7], b[8], b[9]);
                    body.spin.set(b[10], b[11], b[12]);
                    body.acceleration.set(0, -9.8f / 400, 0);
                }
                for (int i = 0; i < n; i++) {
                    place(shape, bodies.get(i), rotations.get(i), hulls.get(i));
                    buffer.clear();
                    narrow.collide(ground, shape, 0.1f, buffer);
                    for (int k = 0; k < buffer.size(); k++) {
                        s.addContact(0, i + 1, buffer.x(k), buffer.y(k), buffer.z(k), buffer.normalX(k),
                                buffer.normalY(k), buffer.normalZ(k), buffer.separation(k)).friction = 0.5f;
                    }
                    for (int j = i + 1; j < n; j++) {
                        place(other, bodies.get(j), rotations.get(j), hulls.get(j));
                        buffer.clear();
                        boolean lower = bodies.get(i)[5] < bodies.get(j)[5];
                        narrow.collide(lower ? shape : other, lower ? other : shape, 0.1f, buffer);
                        for (int k = 0; k < buffer.size(); k++) {
                            s.addContact(lower ? i + 1 : j + 1, lower ? j + 1 : i + 1, buffer.x(k), buffer.y(k),
                                    buffer.z(k), buffer.normalX(k), buffer.normalY(k), buffer.normalZ(k),
                                    buffer.separation(k)).friction = 0.4f;
                        }
                    }
                }
                if (springs) {
                    float[] b = bodies.get(0);
                    for (int sx = -1; sx <= 1; sx += 2) {
                        for (int sz = -1; sz <= 1; sz += 2) {
                            float y = b[5] - b[1];
                            RigidSolver.Spring spring = s.addSpring(1, b[4] + sx * b[0], y, b[6] + sz * b[2], 0, 1, 0);
                            spring.ratio = 1;
                            spring.compression = Math.max(0, 0.5f - y);
                            spring.min = 0;
                            spring.max = Float.MAX_VALUE;
                            spring.stiffness = 0.1f;
                            spring.damping = 0.2f;
                        }
                    }
                }
                s.solve();
                for (int i = 0; i < n; i++) {
                    float[] b = bodies.get(i);
                    RigidSolver.Body body = s.body(i + 1);
                    b[4] += body.dp.x;
                    b[5] += body.dp.y;
                    b[6] += body.dp.z;
                    body.dq.mul(rotations.get(i), rotations.get(i)).normalize();
                    b[7] = body.velocity.x;
                    b[8] = body.velocity.y;
                    b[9] = body.velocity.z;
                    b[10] = body.spin.x;
                    b[11] = body.spin.y;
                    b[12] = body.spin.z;
                    for (int f = 4; f < 13; f++) {
                        h = mix(h, b[f]);
                    }
                    Quaternionf q = rotations.get(i);
                    h = mix(mix(mix(mix(h, q.x), q.y), q.z), q.w);
                }
            }
        }
        return h;
    }

    private static void place(Polytope p, float[] b, Quaternionf q, ConvexHull hull) {
        if (hull != null) {
            p.hull(hull, b[4], b[5], b[6], q);
        } else {
            p.box(b[4], b[5], b[6], q, b[0], b[1], b[2]);
        }
    }

    /** Random block terrain per section, every triangle's vertices. */
    static long bake() {
        Random random = new Random(14);
        float[] v = new float[9];
        long h = 1;
        for (int scene = 0; scene < 40; scene++) {
            BoxBuffer boxes = new BoxBuffer();
            boxes.add(-8, 0, -8, 24, 1, 24);
            for (int c = 0; c < 60; c++) {
                int x = random.nextInt(24) - 4, z = random.nextInt(24) - 4, y = 1 + random.nextInt(4);
                double top = random.nextInt(4) == 0 ? y + 0.5 : y + 1;
                boxes.add(x, y, z, x + 1 + random.nextInt(3), top, z + 1 + random.nextInt(3));
            }
            SurfaceSection s = SurfaceBake.bake(0, 0, 0, boxes);
            h = mix(h, s.triangleCount());
            for (int t = 0; t < s.triangleCount(); t++) {
                s.vertices(t, v);
                for (float f : v) {
                    h = mix(h, f);
                }
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    h = mix(h, s.first(x, z));
                    h = mix(h, s.end(x, z));
                }
            }
        }
        return h;
    }

    /** Random OBBs cast through random box fields: time of impact bits. */
    static long sweep() {
        Random random = new Random(15);
        long h = 1;
        for (int scene = 0; scene < 3000; scene++) {
            BoxBuffer boxes = new BoxBuffer();
            int count = 1 + random.nextInt(10);
            for (int i = 0; i < count; i++) {
                double x = random.nextDouble() * 8 - 4, y = random.nextDouble() * 4 - 4, z = random.nextDouble() * 8 - 4;
                boxes.add(x, y, z, x + 0.25 + random.nextDouble() * 2, y + 0.25 + random.nextDouble() * 2,
                        z + 0.25 + random.nextDouble() * 2);
            }
            OBB obb = new OBB(new Vector3f(random.nextFloat() * 2 - 1, 2 + random.nextFloat() * 2, random.nextFloat() * 2 - 1),
                    new Vector3f(0.3f + random.nextFloat(), 0.3f + random.nextFloat(), 0.3f + random.nextFloat()),
                    rotation(random));
            Vec3 move = new Vec3(random.nextDouble() * 4 - 2, -random.nextDouble() * 6, random.nextDouble() * 4 - 2);
            h = mix(h, SweptHull.timeOfImpact(obb, boxes, move));
            h = mix(h, SweptHull.firstOverlappingBox(obb, boxes));
        }
        return h;
    }

    private static Quaternionf rotation(Random random) {
        return new Quaternionf().rotateYXZ(random.nextFloat() * 6.28f, (random.nextFloat() - 0.5f) * 1.2f,
                (random.nextFloat() - 0.5f) * 1.2f).normalize();
    }

    /** Random convex prism or pyramid on an ellipse. */
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
