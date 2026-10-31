package com.wf.wflib.physics.rigid;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline: static ground box (top y = 0) + dynamic boxes, ticks and blocks, no warm start. */
class RigidSolverTest {

    private static final float G = 9.8f / 400;
    private static final float MARGIN = 0.1f;

    private static final class Box {
        final float ex, ey, ez, mass;
        final Vector3f position = new Vector3f();
        final Vector3f velocity = new Vector3f();
        final Vector3f spin = new Vector3f();
        final Quaternionf rotation = new Quaternionf();
        float friction = 0.5f;

        Box(float ex, float ey, float ez, float mass, float x, float y, float z) {
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
            this.mass = mass;
            position.set(x, y, z);
        }
    }

    private static final class Scene {
        final RigidSolver solver = new RigidSolver(RigidSolver.SUBSTEPS, 1);
        final ConvexContacts narrow = new ConvexContacts();
        final ContactBuffer buffer = new ContactBuffer();
        final Polytope ground = new Polytope().box(-50, -1, -50, 50, 0, 50);
        final Polytope shape = new Polytope();
        final Polytope other = new Polytope();
        final java.util.List<Box> boxes = new java.util.ArrayList<>();
        float deepest;

        Box add(Box box) {
            boxes.add(box);
            return box;
        }

        void tick(boolean springs) {
            RigidSolver s = solver;
            s.reset(0, 0, 0);
            s.addBody();
            for (Box b : boxes) {
                RigidSolver.Body body = s.addBody();
                body.inverseMass = 1 / b.mass;
                inverseInertia(b, body.inverseInertia);
                body.com.set(b.position);
                body.velocity.set(b.velocity);
                body.spin.set(b.spin);
                body.acceleration.set(0, -G, 0);
            }
            for (int i = 0; i < boxes.size(); i++) {
                Box b = boxes.get(i);
                shape.box(b.position.x, b.position.y, b.position.z, b.rotation, b.ex, b.ey, b.ez);
                buffer.clear();
                narrow.collide(ground, shape, MARGIN, buffer);
                for (int k = 0; k < buffer.size(); k++) {
                    RigidSolver.Contact c = s.addContact(0, i + 1, buffer.x(k), buffer.y(k), buffer.z(k),
                            buffer.normalX(k), buffer.normalY(k), buffer.normalZ(k), buffer.separation(k));
                    c.friction = b.friction;
                }
                for (int j = i + 1; j < boxes.size(); j++) {
                    Box o = boxes.get(j);
                    other.box(o.position.x, o.position.y, o.position.z, o.rotation, o.ex, o.ey, o.ez);
                    buffer.clear();
                    // Lower body is `a`: normal a -> b points up.
                    boolean iLower = b.position.y < o.position.y;
                    narrow.collide(iLower ? shape : other, iLower ? other : shape, MARGIN, buffer);
                    for (int k = 0; k < buffer.size(); k++) {
                        RigidSolver.Contact c = s.addContact(iLower ? i + 1 : j + 1, iLower ? j + 1 : i + 1,
                                buffer.x(k), buffer.y(k), buffer.z(k),
                                buffer.normalX(k), buffer.normalY(k), buffer.normalZ(k), buffer.separation(k));
                        c.friction = Math.min(b.friction, o.friction);
                    }
                }
            }
            if (springs) {
                addSprings(s);
            }
            s.solve();
            deepest = 0;
            for (int k = 0; k < s.contactCount(); k++) {
                deepest = Math.min(deepest, s.contact(k).debugSeparation());
            }
            for (int i = 0; i < boxes.size(); i++) {
                Box b = boxes.get(i);
                RigidSolver.Body body = s.body(i + 1);
                b.position.add(body.dp);
                body.dq.mul(b.rotation, b.rotation).normalize();
                b.velocity.set(body.velocity);
                b.spin.set(body.spin);
            }
        }

        private void addSprings(RigidSolver s) {
            Box b = boxes.get(0);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float x = b.position.x + sx * b.ex, y = b.position.y - b.ey, z = b.position.z + sz * b.ez;
                    RigidSolver.Spring spring = s.addSpring(1, x, y, z, 0, 1, 0);
                    spring.ratio = 1;
                    spring.compression = Math.max(0, REST - y);
                    spring.min = 0;
                    spring.max = Float.MAX_VALUE;
                    spring.stiffness = STIFFNESS;
                    spring.damping = DAMPING;
                }
            }
        }
    }

    private static final float REST = 0.5f;
    private static final float STIFFNESS = 0.1f;
    private static final float DAMPING = 0.2f;

    private static void inverseInertia(Box b, Matrix3f dest) {
        float k = b.mass / 3;
        Matrix3f r = new Matrix3f().set(b.rotation);
        Matrix3f local = new Matrix3f().scaling(1 / (k * (b.ey * b.ey + b.ez * b.ez)), 1 / (k * (b.ex * b.ex + b.ez * b.ez)),
                1 / (k * (b.ex * b.ex + b.ey * b.ey)));
        r.mul(local, dest).mul(r.transpose(new Matrix3f()));
    }

    @Test
    void aDroppedBoxRestsWithinAMillimetre() {
        Scene scene = new Scene();
        Box box = scene.add(new Box(0.5f, 0.5f, 0.5f, 1, 0, 3, 0));
        for (int t = 0; t < 400; t++) {
            scene.tick(false);
        }
        assertEquals(0.5, box.position.y, 1.0e-3);
        assertTrue(box.velocity.length() < 1.0e-4, "speed " + box.velocity.length());
        assertTrue(box.spin.length() < 1.0e-4, "spin " + box.spin.length());
        assertTrue(Math.abs(box.position.x) < 0.15 && Math.abs(box.position.z) < 0.15, "drift " + box.position);
    }

    @Test
    void aSlidingBoxStopsWhereCoulombSaysItShould() {
        Scene scene = new Scene();
        Box box = scene.add(new Box(0.5f, 0.5f, 0.5f, 1, 0, 0.5f, 0));
        box.velocity.set(0.3f, 0, 0);
        for (int t = 0; t < 400; t++) {
            scene.tick(false);
        }
        double analytic = 0.3 * 0.3 / (2 * 0.5 * G);
        assertEquals(analytic, box.position.x, 0.05, "analytic " + analytic);
        assertEquals(0.5, box.position.y, 2.0e-3);
        assertTrue(box.velocity.length() < 1.0e-4);
    }

    @Test
    void aSprungBoxSettlesAtTheSpringEquilibriumWithoutRinging() {
        // 4 x k x x = g, anchors at the underside.
        double equilibrium = 0.5 + REST - G / (4 * STIFFNESS);
        Scene scene = new Scene();
        Box box = scene.add(new Box(0.5f, 0.5f, 0.5f, 1, 0, (float) equilibrium + 0.05f, 0));
        int reversals = 0;
        float lastVelocity = 0;
        for (int t = 0; t < 600; t++) {
            scene.tick(true);
            if (t > 300 && lastVelocity * box.velocity.y < 0) {
                reversals++;
            }
            lastVelocity = box.velocity.y;
        }
        assertEquals(equilibrium, box.position.y, 1.0e-3, "equilibrium " + equilibrium);
        assertTrue(box.velocity.length() < 1.0e-5, "speed " + box.velocity.length());
        assertEquals(0, reversals, "ringing");
    }

    @Test
    void aLightBottomTowerStaysUnderOnePointThreeCentimetres() {
        Scene scene = new Scene();
        scene.add(new Box(0.5f, 0.5f, 0.5f, 1.5f, -0.65f, 0.5f, 0));
        scene.add(new Box(0.55f, 0.5f, 0.5f, 1.5f, 0.7f, 0.5f, 0));
        Box top = scene.add(new Box(1.3f, 0.5f, 0.7f, 65, 0, 1.5f, 0));
        float worst = 0;
        for (int t = 0; t < 400; t++) {
            scene.tick(false);
            if (t > 100) {
                worst = Math.min(worst, scene.deepest);
            }
        }
        assertTrue(-worst < 0.013f, "overlap " + -worst);
        assertTrue(top.spin.length() < 1.0e-3, "rocking " + top.spin.length());
        assertEquals(1.5, top.position.y, 0.02);
    }
}
