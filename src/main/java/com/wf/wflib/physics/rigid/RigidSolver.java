package com.wf.wflib.physics.rigid;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One island, one tick: Box2D v3 soft step (solver.c, contact_solver.c) in 3D, ticks and blocks. Per substep:
 * integrate velocity, warm start, push (soft bias, normals only), integrate dp/dq, relax (speculative bias, friction);
 * restitution once after. Divergences: two tangents + friction cone; implicit wheel springs; inverse inertia fixed for
 * the tick; {@link #STACKING_NORMAL} mass scale. Callers apply dp/dq. Positions relative to {@link #origin} in float.
 * Pooled; one thread per instance.
 */
public final class RigidSolver {

    public static final int SUBSTEPS = 4;
    /** Box2D: damping ratio 10, hertz min(30 Hz = 1.5 /t, substep rate / 8), static contacts 2x hertz. */
    private static final float CONTACT_ZETA = 10;
    /** Overlap push-out speed cap, b/t (Box2D 3 m/s). */
    private static final float CONTACT_SPEED = 0.15f;
    /** Below this approach speed no bounce, b/t (Box2D 1 m/s). */
    private static final float RESTITUTION_THRESHOLD = 0.05f;
    /**
     * |n.y| > this between dynamics: lower body's inverse mass scaled to the upper's (PhysX contact mass scale).
     * Soft contact stiffness = effective mass x omega^2 => overlap ~ load / lower mass: 65 t on 1.5 t sank, rocked,
     * toppled at any substep count; scaled 1.3 cm at every ratio (offline harness, 4 substeps).
     */
    private static final float STACKING_NORMAL = 0.7f;
    /** Joint error closing speed cap, b/t: a coupling found far apart (spawn, unload) eases in, not launched. */
    private static final float JOINT_SPEED = 0.5f;
    /** Box2D jointDampingRatio; contact zeta 10 left a turning fifth wheel 0.24 open. */
    private static final float JOINT_ZETA = 2;

    public static final class Body {
        public float inverseMass;
        /** World frame, held for the tick. */
        public final Matrix3f inverseInertia = new Matrix3f();
        /** Relative to {@link #origin}. */
        public final Vector3f com = new Vector3f();
        public final Vector3f velocity = new Vector3f();
        public final Vector3f spin = new Vector3f();
        /** b/t^2, integrated per substep: gravity + force / mass. */
        public final Vector3f acceleration = new Vector3f();
        /** Spin kept per tick. */
        public float spinRetention = 1;
        public float maxSpin = Float.MAX_VALUE;
        /** Output, from the step's start pose. */
        public final Vector3f dp = new Vector3f();
        public final Quaternionf dq = new Quaternionf();
        boolean dynamic;
    }

    public static final class Contact {
        int a, b;
        /** a -> b. */
        public final Vector3f normal = new Vector3f();
        final Vector3f tangent1 = new Vector3f(), tangent2 = new Vector3f();
        /** Point - com, world, step start. */
        public final Vector3f anchorA = new Vector3f(), anchorB = new Vector3f();
        float baseSeparation;
        float normalMass, tangentMass1, tangentMass2;
        float scaleA, scaleB;
        boolean stiff;
        float relativeVelocity;
        public float friction, restitution;
        /**
         * Static a only: measured along n, impulse along +y (world support: a ramp lifts without pushing back, a
         * pitched face cannot shove sideways).
         */
        public boolean lift;
        /** Overlap opposed by another on the same body: no push-out (cannot succeed; pumped a wedged hull). */
        public boolean wedged;
        /** Per substep; warm start in, stored out. */
        public float normalImpulse, tangentImpulse1, tangentImpulse2;
        /** Whole tick, output. */
        public float totalNormalImpulse;
        float restitutionImpulse;

        float debugSeparation;
        public int debugTagA, debugTagB;

        public float debugMass() {
            return normalMass;
        }

        public float debugSeparation() {
            return debugSeparation;
        }

        /** Normal relative velocity at the step's start, b/t; < 0 closing. */
        public float approach() {
            return relativeVelocity;
        }
    }

    /** Wheel: implicit spring-damper, unilateral, bump stop at max compression. Single body. */
    public static final class Spring {
        int body;
        public final Vector3f anchor = new Vector3f();
        /** Unit ground normal; impulse direction on the body. */
        public final Vector3f axis = new Vector3f();
        /** Compression per unit motion along axis: 1 / (strut down . -axis), strut tilted off the ground normal. */
        public float ratio;
        public float compression, min, max;
        /** Mass/t^2 per block, mass/t. */
        public float stiffness, damping;
        float inverseEffectiveMass;
        /** Per substep; warm start in, stored out. */
        public float impulse, stopImpulse;
        /** Whole tick, output. */
        public float totalImpulse;
    }

    /**
     * Tow coupling a -> b: ball (rigid hitch) or rope ({@link #length} >= 0, max length). Optional yaw stop: articulation
     * (b's heading - a's about +y) held within +-{@link #yawLimit}. Optional towed axle on b: no slip along
     * {@link #axleAxis} at {@link #axleAnchor}, |impulse| <= {@link #grip} x b's support (wheels + ground contacts).
     */
    public static final class Joint {
        int a, b;
        /** Point - com, world, step start. */
        public final Vector3f anchorA = new Vector3f(), anchorB = new Vector3f();
        /** pB - pA, step start. */
        final Vector3f gap = new Vector3f();
        public float length;
        /** Rad; NaN => none. */
        public float yawLimit, yaw;
        /**
         * Fifth-wheel plate: relative roll about {@link #rollAxis} (a's forward, world, step start) driven to 0 from
         * {@link #roll} (rad, step start); NaN => free. Ball alone: MIM-104 rolled over by itself on whole-block noise.
         */
        public float roll;
        public final Vector3f rollAxis = new Vector3f();
        /** grip 0 => no axle. */
        public float grip;
        public final Vector3f axleAnchor = new Vector3f(), axleAxis = new Vector3f();
        /** Accumulated: ball x/y/z (rope: x), yaw stop, axle. Warm start in, stored out. */
        public final Vector3f impulse = new Vector3f();
        public float yawImpulse, axleImpulse, rollImpulse;
        final Matrix3f k = new Matrix3f();
        final Vector3f rA = new Vector3f(), rB = new Vector3f(), u = new Vector3f(), axleR = new Vector3f(), axleU = new Vector3f();
    }

    public final double[] origin = new double[3];
    private final List<Body> bodies = new ArrayList<>();
    private final List<Contact> contacts = new ArrayList<>();
    private final List<Spring> springs = new ArrayList<>();
    private final List<Joint> joints = new ArrayList<>();
    private int bodyCount, contactCount, springCount, jointCount;
    private final Vector3f t0 = new Vector3f(), t1 = new Vector3f(), t2 = new Vector3f(), t3 = new Vector3f();
    private final Vector3f t4 = new Vector3f(), t5 = new Vector3f();
    private final Matrix3f m0 = new Matrix3f(), m1 = new Matrix3f(), m2 = new Matrix3f();

    private final int substeps, iterations;
    private final float h, inverseH;
    private final float biasRate, massScale, impulseScale;
    private final float staticBiasRate, staticMassScale, staticImpulseScale;
    private final float jointBiasRate, jointMassScale, jointImpulseScale;

    /** @param iterations push passes per substep; relax once. */
    public RigidSolver(int substeps, int iterations) {
        this.substeps = substeps;
        this.iterations = iterations;
        h = 1.0f / substeps;
        inverseH = substeps;
        float hertz = Math.min(1.5f, 0.125f * substeps);
        float[] soft = soft(hertz, CONTACT_ZETA, h);
        biasRate = soft[0];
        massScale = soft[1];
        impulseScale = soft[2];
        soft = soft(2 * hertz, CONTACT_ZETA, h);
        staticBiasRate = soft[0];
        staticMassScale = soft[1];
        staticImpulseScale = soft[2];
        soft = soft(2 * hertz, JOINT_ZETA, h);
        jointBiasRate = soft[0];
        jointMassScale = soft[1];
        jointImpulseScale = soft[2];
    }

    /** Box2D b2MakeSoft: {biasRate, massScale, impulseScale}. */
    private static float[] soft(float hertz, float zeta, float h) {
        float omega = 2 * (float) Math.PI * hertz;
        float a1 = 2 * zeta + h * omega;
        float a2 = h * omega * a1;
        float a3 = 1 / (1 + a2);
        return new float[]{omega / a1, a2 * a3, a3};
    }

    public void reset(double x, double y, double z) {
        origin[0] = x;
        origin[1] = y;
        origin[2] = z;
        bodyCount = 0;
        contactCount = 0;
        springCount = 0;
        jointCount = 0;
    }

    /** Zeroed; static until given mass. */
    public Body addBody() {
        if (bodyCount == bodies.size()) {
            bodies.add(new Body());
        }
        Body body = bodies.get(bodyCount++);
        body.inverseMass = 0;
        body.inverseInertia.zero();
        body.com.zero();
        body.velocity.zero();
        body.spin.zero();
        body.acceleration.zero();
        body.spinRetention = 1;
        body.maxSpin = Float.MAX_VALUE;
        return body;
    }

    public Body body(int i) {
        return bodies.get(i);
    }

    public int bodyCount() {
        return bodyCount;
    }

    /** Point relative to {@link #origin}; separation < 0 overlapping, > 0 speculative. Impulses zeroed. */
    public Contact addContact(int a, int b, float x, float y, float z, float nx, float ny, float nz, float separation) {
        if (contactCount == contacts.size()) {
            contacts.add(new Contact());
        }
        Contact contact = contacts.get(contactCount++);
        contact.a = a;
        contact.b = b;
        contact.normal.set(nx, ny, nz);
        contact.anchorA.set(x, y, z).sub(bodies.get(a).com);
        contact.anchorB.set(x, y, z).sub(bodies.get(b).com);
        contact.baseSeparation = separation;
        contact.debugSeparation = separation;
        contact.friction = 0;
        contact.restitution = 0;
        contact.lift = false;
        contact.wedged = false;
        contact.normalImpulse = 0;
        contact.tangentImpulse1 = 0;
        contact.tangentImpulse2 = 0;
        return contact;
    }

    public Contact contact(int i) {
        return contacts.get(i);
    }

    public int contactCount() {
        return contactCount;
    }

    /** Point relative to {@link #origin}. Impulses zeroed. */
    public Spring addSpring(int body, float x, float y, float z, float axisX, float axisY, float axisZ) {
        if (springCount == springs.size()) {
            springs.add(new Spring());
        }
        Spring spring = springs.get(springCount++);
        spring.body = body;
        spring.anchor.set(x, y, z).sub(bodies.get(body).com);
        spring.axis.set(axisX, axisY, axisZ);
        spring.ratio = 1;
        spring.impulse = 0;
        spring.stopImpulse = 0;
        return spring;
    }

    public Spring spring(int i) {
        return springs.get(i);
    }

    public int springCount() {
        return springCount;
    }

    /** Points relative to {@link #origin}; length < 0 ball; no yaw stop, no axle, impulses zeroed. */
    public Joint addJoint(int a, int b, float ax, float ay, float az, float bx, float by, float bz, float length) {
        if (jointCount == joints.size()) {
            joints.add(new Joint());
        }
        Joint joint = joints.get(jointCount++);
        joint.a = a;
        joint.b = b;
        joint.anchorA.set(ax, ay, az).sub(bodies.get(a).com);
        joint.anchorB.set(bx, by, bz).sub(bodies.get(b).com);
        joint.gap.set(bx - ax, by - ay, bz - az);
        joint.length = length;
        joint.yawLimit = Float.NaN;
        joint.roll = Float.NaN;
        joint.grip = 0;
        joint.impulse.zero();
        joint.yawImpulse = 0;
        joint.axleImpulse = 0;
        joint.rollImpulse = 0;
        return joint;
    }

    public Joint joint(int i) {
        return joints.get(i);
    }

    public int jointCount() {
        return jointCount;
    }

    public void solve() {
        prepare();
        for (int substep = 0; substep < substeps; substep++) {
            integrateVelocities();
            warmStart();
            for (int i = 0; i < iterations; i++) {
                push();
            }
            integratePositions();
            relax();
        }
        restitution();
    }

    private void prepare() {
        for (int i = 0; i < bodyCount; i++) {
            Body body = bodies.get(i);
            body.dynamic = body.inverseMass > 0 || !isZero(body.inverseInertia);
            body.dp.zero();
            body.dq.identity();
        }
        for (int k = 0; k < contactCount; k++) {
            Contact c = contacts.get(k);
            Body a = bodies.get(c.a), b = bodies.get(c.b);
            Vector3f n = c.normal;
            c.stiff = !a.dynamic || !b.dynamic;
            c.scaleA = 1;
            c.scaleB = 1;
            if (!c.stiff && Math.abs(n.y) > STACKING_NORMAL) {
                // n a -> b upward => a lower.
                boolean aLower = n.y > 0;
                float ratio = aLower ? a.inverseMass / b.inverseMass : b.inverseMass / a.inverseMass;
                if (ratio > 1) {
                    if (aLower) {
                        c.scaleA = 1 / ratio;
                    } else {
                        c.scaleB = 1 / ratio;
                    }
                }
            }
            // s = s0 + n . (cB - cA) - n . (rB - rA) at the start, rotated anchors after.
            c.baseSeparation -= t0.set(c.anchorB).sub(c.anchorA).dot(n);
            tangents(n, c.tangent1, c.tangent2);
            c.normalMass = c.lift ? liftMass(b, c.anchorB, n) : mass(c, a, b, n);
            c.tangentMass1 = mass(c, a, b, c.tangent1);
            c.tangentMass2 = mass(c, a, b, c.tangent2);
            c.relativeVelocity = relative(a, b, c.anchorA, c.anchorB, t1).dot(n);
            c.totalNormalImpulse = 0;
            c.restitutionImpulse = 0;
        }
        for (int k = 0; k < springCount; k++) {
            Spring s = springs.get(k);
            Body body = bodies.get(s.body);
            s.inverseEffectiveMass = s.ratio * s.ratio * response(body, s.anchor, s.axis);
            s.totalImpulse = 0;
        }
    }

    private void integrateVelocities() {
        for (int i = 0; i < bodyCount; i++) {
            Body body = bodies.get(i);
            if (!body.dynamic) {
                continue;
            }
            body.velocity.fma(h, body.acceleration);
            if (body.spinRetention != 1) {
                body.spin.mul((float) Math.pow(body.spinRetention, h));
            }
            float spin = body.spin.length();
            if (spin > body.maxSpin) {
                body.spin.mul(body.maxSpin / spin);
            }
        }
    }

    private void warmStart() {
        for (int k = 0; k < contactCount; k++) {
            Contact c = contacts.get(k);
            Body a = bodies.get(c.a), b = bodies.get(c.b);
            c.totalNormalImpulse += c.normalImpulse;
            applyNormal(c, a, b, c.normalImpulse);
            apply(c, a, b, t0.set(c.tangent1).mul(c.tangentImpulse1).fma(c.tangentImpulse2, c.tangent2));
        }
        for (int k = 0; k < springCount; k++) {
            Spring s = springs.get(k);
            float impulse = s.impulse + s.stopImpulse;
            s.totalImpulse += impulse;
            applyOne(bodies.get(s.body), s.anchor, t0.set(s.axis).mul(impulse * s.ratio), 1);
        }
        for (int k = 0; k < jointCount; k++) {
            Joint j = joints.get(k);
            Body a = bodies.get(j.a), b = bodies.get(j.b);
            pose(j, a, b);
            if (j.length < 0) {
                applyPair(a, b, j.rA, j.rB, t0.set(j.impulse));
            } else if (j.u.lengthSquared() > 1.0e-12f) {
                applyPair(a, b, j.rA, j.rB, t0.set(j.u).normalize().mul(-j.impulse.x));
            }
            applyYaw(a, b, -Math.signum(j.yaw) * j.yawImpulse);
            if (!Float.isNaN(j.roll)) {
                applyAngular(a, b, a.dq.transform(t3.set(j.rollAxis)), j.rollImpulse);
            }
            if (j.grip > 0) {
                applyOne(b, j.axleR, t0.set(j.axleU).mul(j.axleImpulse), 1);
            }
        }
    }

    /** Normals with soft bias; springs. */
    private void push() {
        for (int k = 0; k < contactCount; k++) {
            Contact c = contacts.get(k);
            Body a = bodies.get(c.a), b = bodies.get(c.b);
            float s = separation(c, a, b);
            float bias, scale, keep;
            if (s > 0) {
                bias = s * inverseH;
                scale = 1;
                keep = 0;
            } else if (c.wedged) {
                bias = 0;
                scale = 1;
                keep = 0;
            } else if (c.stiff) {
                bias = Math.max(staticMassScale * staticBiasRate * s, -CONTACT_SPEED);
                scale = staticMassScale;
                keep = staticImpulseScale;
            } else {
                bias = Math.max(massScale * biasRate * s, -CONTACT_SPEED);
                scale = massScale;
                keep = impulseScale;
            }
            float vn = relative(a, b, c.anchorA, c.anchorB, t1).dot(c.normal);
            float impulse = -c.normalMass * (scale * vn + bias) - keep * c.normalImpulse;
            float next = Math.max(c.normalImpulse + impulse, 0);
            impulse = next - c.normalImpulse;
            c.normalImpulse = next;
            c.totalNormalImpulse += impulse;
            applyNormal(c, a, b, impulse);
        }
        solveSprings(true);
        solveJoints(true);
    }

    /** Normals with speculative bias only, friction cone; springs. */
    private void relax() {
        for (int k = 0; k < contactCount; k++) {
            Contact c = contacts.get(k);
            Body a = bodies.get(c.a), b = bodies.get(c.b);
            float s = separation(c, a, b);
            float bias = s > 0 ? s * inverseH : 0;
            float vn = relative(a, b, c.anchorA, c.anchorB, t1).dot(c.normal);
            float impulse = -c.normalMass * (vn + bias);
            float next = Math.max(c.normalImpulse + impulse, 0);
            impulse = next - c.normalImpulse;
            c.normalImpulse = next;
            c.totalNormalImpulse += impulse;
            applyNormal(c, a, b, impulse);

            if (c.friction == 0) {
                continue;
            }
            Vector3f relative = relative(a, b, c.anchorA, c.anchorB, t1);
            float first = c.tangentImpulse1 - c.tangentMass1 * relative.dot(c.tangent1);
            float second = c.tangentImpulse2 - c.tangentMass2 * relative.dot(c.tangent2);
            float limit = c.friction * c.normalImpulse;
            float length = (float) Math.sqrt(first * first + second * second);
            if (length > limit) {
                float scaleDown = length > 0 ? limit / length : 0;
                first *= scaleDown;
                second *= scaleDown;
            }
            Vector3f p = t0.set(c.tangent1).mul(first - c.tangentImpulse1).fma(second - c.tangentImpulse2, c.tangent2);
            c.tangentImpulse1 = first;
            c.tangentImpulse2 = second;
            apply(c, a, b, p);
        }
        solveSprings(false);
        solveJoints(false);
    }

    /** Rotated anchors, current gap direction. */
    private void pose(Joint j, Body a, Body b) {
        a.dq.transform(j.rA.set(j.anchorA));
        b.dq.transform(j.rB.set(j.anchorB));
        b.dq.transform(j.axleR.set(j.axleAnchor));
        b.dq.transform(j.axleU.set(j.axleAxis));
        // pB - pA now.
        j.u.set(j.gap).add(b.dp).sub(a.dp).add(j.rB).sub(j.anchorB).sub(j.rA).add(j.anchorA);
    }

    /**
     * Box2D v3 joint softness (2 x contact hertz, {@link #JOINT_ZETA}) on position error, bias capped at
     * {@link #JOINT_SPEED}; relax drops the bias. Rope, yaw stop: unilateral, speculative while slack. Axle: friction-like,
     * no bias.
     */
    private void solveJoints(boolean useBias) {
        for (int k = 0; k < jointCount; k++) {
            Joint j = joints.get(k);
            Body a = bodies.get(j.a), b = bodies.get(j.b);
            pose(j, a, b);
            Vector3f relative = relative(a, b, j.rA, j.rB, t1);
            if (j.length < 0) {
                Vector3f error = t2.set(j.u);
                float scale = 1, keep = 0;
                Vector3f bias = t3.zero();
                if (useBias) {
                    bias.set(error).mul(jointMassScale * jointBiasRate);
                    float speed = bias.length();
                    if (speed > JOINT_SPEED) {
                        bias.mul(JOINT_SPEED / speed);
                    }
                    scale = jointMassScale;
                    keep = jointImpulseScale;
                }
                ballMass(j, a, b);
                Vector3f impulse = j.k.transform(t0.set(relative).mul(scale).add(bias)).negate()
                        .sub(t2.set(j.impulse).mul(keep));
                j.impulse.add(impulse);
                applyPair(a, b, j.rA, j.rB, impulse);
            } else {
                float distance = j.u.length();
                if (distance > 1.0e-6f) {
                    Vector3f u = j.u.div(distance);
                    float s = j.length - distance;
                    float bias, scale = 1, keep = 0;
                    if (s > 0) {
                        bias = s * inverseH;
                    } else if (useBias) {
                        bias = Math.max(jointMassScale * jointBiasRate * s, -JOINT_SPEED);
                        scale = jointMassScale;
                        keep = jointImpulseScale;
                    } else {
                        bias = 0;
                    }
                    float kk = response(a, j.rA, u) + response(b, j.rB, u);
                    if (kk > 0) {
                        float vn = -relative.dot(u);
                        float impulse = -(scale * vn + bias) / kk - keep * j.impulse.x;
                        float next = Math.max(j.impulse.x + impulse, 0);
                        applyPair(a, b, j.rA, j.rB, t0.set(u).mul(-(next - j.impulse.x)));
                        j.impulse.x = next;
                    }
                }
            }
            if (!Float.isNaN(j.yawLimit)) {
                float kk = yawResponse(a) + yawResponse(b);
                float sign = Math.signum(j.yaw);
                float now = j.yaw + yawTurn(b) - yawTurn(a);
                float s = j.yawLimit - sign * now;
                if (kk > 0 && sign != 0) {
                    float bias = s > 0 ? s * inverseH : useBias ? Math.max(jointMassScale * jointBiasRate * s, -JOINT_SPEED) : 0;
                    float vn = -sign * (b.spin.y - a.spin.y);
                    float impulse = -(vn + bias) / kk;
                    float next = Math.max(j.yawImpulse + impulse, 0);
                    applyYaw(a, b, -sign * (next - j.yawImpulse));
                    j.yawImpulse = next;
                }
            }
            if (!Float.isNaN(j.roll)) {
                Vector3f axis = a.dq.transform(t3.set(j.rollAxis));
                float kk = angularResponse(a, axis) + angularResponse(b, axis);
                if (kk > 0) {
                    // Small-step twist about the axis: 2 (q.xyz . axis).
                    float now = j.roll + 2 * (b.dq.x * axis.x + b.dq.y * axis.y + b.dq.z * axis.z) * Math.signum(b.dq.w)
                            - 2 * (a.dq.x * axis.x + a.dq.y * axis.y + a.dq.z * axis.z) * Math.signum(a.dq.w);
                    float scale = 1, keep = 0, bias = 0;
                    if (useBias) {
                        bias = Math.clamp(jointMassScale * jointBiasRate * now, -JOINT_SPEED, JOINT_SPEED);
                        scale = jointMassScale;
                        keep = jointImpulseScale;
                    }
                    float rate = t2.set(b.spin).sub(a.spin).dot(axis);
                    float impulse = -(scale * rate + bias) / kk - keep * j.rollImpulse;
                    j.rollImpulse += impulse;
                    applyAngular(a, b, axis, impulse);
                }
            }
            if (j.grip > 0) {
                float load = 0;
                for (int i = 0; i < springCount; i++) {
                    Spring spring = springs.get(i);
                    if (spring.body == j.b) {
                        load += (spring.impulse + spring.stopImpulse) * spring.ratio;
                    }
                }
                for (int i = 0; i < contactCount; i++) {
                    Contact c = contacts.get(i);
                    if (c.b == j.b && !bodies.get(c.a).dynamic && c.normal.y > STACKING_NORMAL) {
                        load += c.lift ? c.normalImpulse : c.normalImpulse * c.normal.y;
                    }
                }
                float kk = response(b, j.axleR, j.axleU);
                if (kk > 0) {
                    float limit = j.grip * load;
                    float next = Math.clamp(j.axleImpulse - pointVelocity(b, j.axleR, t1).dot(j.axleU) / kk, -limit, limit);
                    applyOne(b, j.axleR, t0.set(j.axleU).mul(next - j.axleImpulse), 1);
                    j.axleImpulse = next;
                }
            }
        }
    }

    /** Ball K^-1: (mA^-1 + mB^-1) I - [rA]x IA^-1 [rA]x - [rB]x IB^-1 [rB]x. */
    private void ballMass(Joint j, Body a, Body b) {
        Matrix3f k = j.k.scaling(a.inverseMass + b.inverseMass);
        k.add(skewSandwich(j.rA, a.inverseInertia, m0)).add(skewSandwich(j.rB, b.inverseInertia, m0));
        if (Math.abs(k.determinant()) > 1.0e-12f) {
            k.invert();
        } else {
            k.zero();
        }
    }

    /** -[r]x M [r]x. */
    private Matrix3f skewSandwich(Vector3f r, Matrix3f m, Matrix3f dest) {
        m1.set(0, r.z, -r.y, -r.z, 0, r.x, r.y, -r.x, 0);
        return m1.transpose(m2).mul(m, dest).mul(m1);
    }

    /** P on b at rB, -P on a at rA. */
    private void applyPair(Body a, Body b, Vector3f rA, Vector3f rB, Vector3f p) {
        if (b.dynamic) {
            applyOne(b, rB, p, 1);
        }
        if (a.dynamic) {
            applyOne(a, rA, p, -1);
        }
    }

    /** Angular impulse +y on b, -y on a. */
    private void applyYaw(Body a, Body b, float impulse) {
        if (impulse == 0) {
            return;
        }
        b.spin.add(b.inverseInertia.transform(t4.set(0, impulse, 0)));
        a.spin.sub(a.inverseInertia.transform(t4.set(0, impulse, 0)));
    }

    /** Angular impulse +axis on b, -axis on a. */
    private void applyAngular(Body a, Body b, Vector3f axis, float impulse) {
        if (impulse == 0) {
            return;
        }
        b.spin.add(b.inverseInertia.transform(t4.set(axis).mul(impulse)));
        a.spin.sub(a.inverseInertia.transform(t4.set(axis).mul(impulse)));
    }

    private float angularResponse(Body body, Vector3f axis) {
        return axis.dot(body.inverseInertia.transform(t5.set(axis)));
    }

    private float yawResponse(Body body) {
        return body.inverseInertia.m11;
    }

    /** Heading change this step about +y, rad. */
    private float yawTurn(Body body) {
        Vector3f forward = body.dq.transform(t4.set(0, 0, 1));
        return (float) Math.atan2(forward.x, forward.z);
    }

    /**
     * Spring: J = h (k (x - h v) - c v), x compression, v extension rate along axis; Gauss-Seidel on the implicit
     * equation. Stop: contact at max compression.
     */
    private void solveSprings(boolean useBias) {
        for (int k = 0; k < springCount; k++) {
            Spring s = springs.get(k);
            Body body = bodies.get(s.body);
            float compression = s.compression - s.ratio * t2.set(body.dp).add(body.dq.transform(t3.set(s.anchor)))
                    .sub(s.anchor).dot(s.axis);
            float response = s.inverseEffectiveMass;
            float extension = s.ratio * pointVelocity(body, s.anchor, t1).dot(s.axis);
            float velocityResponse = h * (s.damping + h * s.stiffness);
            float next = compression < s.min ? 0 : Math.max(0, s.impulse
                    + (h * s.stiffness * Math.min(compression, s.max) - velocityResponse * extension - s.impulse)
                    / (1 + velocityResponse * response));
            float delta = next - s.impulse;
            s.impulse = next;
            s.totalImpulse += delta;
            applyOne(body, s.anchor, t0.set(s.axis).mul(delta * s.ratio), 1);

            float gap = s.max - compression;
            float bias = gap > 0 ? gap * inverseH : useBias ? Math.max(staticMassScale * staticBiasRate * gap, -CONTACT_SPEED) : 0;
            float scale = gap <= 0 && useBias ? staticMassScale : 1;
            float keep = gap <= 0 && useBias ? staticImpulseScale : 0;
            extension = s.ratio * pointVelocity(body, s.anchor, t1).dot(s.axis);
            float stop = response > 0 ? -(scale * extension + bias) / response - keep * s.stopImpulse : 0;
            float nextStop = Math.max(s.stopImpulse + stop, 0);
            delta = nextStop - s.stopImpulse;
            s.stopImpulse = nextStop;
            s.totalImpulse += delta;
            applyOne(body, s.anchor, t0.set(s.axis).mul(delta * s.ratio), 1);
        }
    }

    private void integratePositions() {
        for (int i = 0; i < bodyCount; i++) {
            Body body = bodies.get(i);
            if (!body.dynamic) {
                continue;
            }
            body.dp.fma(h, body.velocity);
            Vector3f w = body.spin;
            if (w.lengthSquared() > 0) {
                body.dq.integrate(h, w.x, w.y, w.z).normalize();
            }
        }
    }

    /** Box2D b2ApplyRestitution: Poisson, no energy gain. */
    private void restitution() {
        for (int k = 0; k < contactCount; k++) {
            Contact c = contacts.get(k);
            if (c.restitution == 0) {
                continue;
            }
            Body a = bodies.get(c.a), b = bodies.get(c.b);
            float compression = c.totalNormalImpulse - c.restitutionImpulse;
            boolean armed = c.relativeVelocity < -RESTITUTION_THRESHOLD && compression > 0;
            float bias;
            if (armed) {
                bias = c.restitution * c.relativeVelocity;
            } else {
                float s = separation(c, a, b);
                bias = s > 0 ? s * inverseH : 0;
            }
            float vn = relative(a, b, c.anchorA, c.anchorB, t1).dot(c.normal);
            float impulse = -c.normalMass * (vn + bias);
            float next = Math.max(c.normalImpulse + impulse, 0);
            impulse = next - c.normalImpulse;
            float approach = Math.min(Math.max(-c.normalMass * vn, 0), Math.max(impulse, 0));
            if (armed) {
                float allowance = c.restitution * (compression + approach) - c.restitutionImpulse;
                impulse = Math.min(impulse, approach + Math.max(allowance, 0));
            }
            c.normalImpulse += impulse;
            c.restitutionImpulse += impulse - approach;
            c.totalNormalImpulse += impulse;
            applyNormal(c, a, b, impulse);
        }
    }

    private float separation(Contact c, Body a, Body b) {
        Vector3f ds = t2.set(b.dp).sub(a.dp).add(b.dq.transform(t3.set(c.anchorB))).sub(a.dq.transform(t4.set(c.anchorA)));
        return c.baseSeparation + ds.dot(c.normal);
    }

    /** vB + wB x rB - vA - wA x rA. */
    private Vector3f relative(Body a, Body b, Vector3f anchorA, Vector3f anchorB, Vector3f dest) {
        pointVelocity(b, anchorB, dest);
        return dest.sub(pointVelocity(a, anchorA, t5));
    }

    private static Vector3f pointVelocity(Body body, Vector3f anchor, Vector3f dest) {
        return body.spin.cross(anchor, dest).add(body.velocity);
    }

    /** P on b, -P on a. */
    private void apply(Contact c, Body a, Body b, Vector3f p) {
        if (b.dynamic) {
            applyOne(b, c.anchorB, p, c.scaleB);
        }
        if (a.dynamic) {
            applyOne(a, c.anchorA, p, -c.scaleA);
        }
    }

    private void applyNormal(Contact c, Body a, Body b, float impulse) {
        if (c.lift) {
            applyOne(b, c.anchorB, t0.set(0, impulse, 0), 1);
        } else {
            apply(c, a, b, t0.set(c.normal).mul(impulse));
        }
    }

    /**
     * Impulse along +y, measured along n: n.y / m + (r x n) . I^-1 (r x up). Floor n.y / (2 m): cross term can
     * cancel the linear one on a pitched lever.
     */
    private float liftMass(Body body, Vector3f anchor, Vector3f n) {
        Vector3f rn = anchor.cross(n, t4);
        Vector3f ru = t5.set(-anchor.z, 0, anchor.x);
        float k = Math.max(n.y * body.inverseMass + rn.dot(body.inverseInertia.transform(ru)), 0.5f * n.y * body.inverseMass);
        return k > 0 ? 1 / k : 0;
    }

    private void applyOne(Body body, Vector3f anchor, Vector3f p, float scale) {
        body.velocity.fma(scale * body.inverseMass, p);
        body.spin.add(body.inverseInertia.transform(anchor.cross(p, t4)).mul(scale));
    }

    private float mass(Contact c, Body a, Body b, Vector3f direction) {
        float k = c.scaleA * response(a, c.anchorA, direction) + c.scaleB * response(b, c.anchorB, direction);
        return k > 0 ? 1 / k : 0;
    }

    /** m^-1 + (r x d) . I^-1 (r x d). */
    private float response(Body body, Vector3f anchor, Vector3f direction) {
        Vector3f rn = anchor.cross(direction, t4);
        return body.inverseMass + rn.dot(body.inverseInertia.transform(t5.set(rn)));
    }

    private static void tangents(Vector3f n, Vector3f first, Vector3f second) {
        if (Math.abs(n.x) < 0.57735f) {
            first.set(0, n.z, -n.y);
        } else {
            first.set(n.y, -n.x, 0);
        }
        first.normalize();
        n.cross(first, second);
    }

    private static boolean isZero(Matrix3f m) {
        return m.m00 == 0 && m.m01 == 0 && m.m02 == 0 && m.m10 == 0 && m.m11 == 0 && m.m12 == 0
                && m.m20 == 0 && m.m21 == 0 && m.m22 == 0;
    }
}
