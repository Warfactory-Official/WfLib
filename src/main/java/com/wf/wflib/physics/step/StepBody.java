package com.wf.wflib.physics.step;

import com.wf.wflib.physics.api.BodyState;
import com.wf.wflib.physics.api.BoxBuffer;
import com.wf.wflib.physics.api.CellTops;
import com.wf.wflib.physics.api.ChunkCollisionCache;
import com.wf.wflib.physics.api.CollisionProvider;
import com.wf.wflib.physics.api.CompoundBody;
import com.wf.wflib.physics.api.ConvexHull;
import com.wf.wflib.physics.api.Coupling;
import com.wf.wflib.physics.api.DeckView;
import com.wf.wflib.physics.api.HullTag;
import com.wf.wflib.physics.api.OrientedBoxes;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.SectionSource;
import com.wf.wflib.physics.api.SpringRows;
import com.wf.wflib.physics.api.StepResult;
import com.wf.wflib.physics.api.DriveSurface;
import com.wf.wflib.physics.api.SurfaceBake;
import com.wf.wflib.physics.api.SurfaceSection;
import com.wf.wflib.physics.rigid.ContactBuffer;
import com.wf.wflib.physics.rigid.ConvexContacts;
import com.wf.wflib.physics.rigid.NarrowNative;
import com.wf.wflib.physics.rigid.Polytope;
import com.wf.wflib.physics.rigid.RigidSolver;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One body's share of {@link RigidStep}: inputs captured on the tick thread ({@link PhysicsBody#capture}), contacts
 * and integration in its island task, result handed back at flush.
 */
public final class StepBody {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final float HULL_RESTITUTION = 0.05f;
    private static final float FRICTION = BodyState.FRICTION;
    private static final float MATCH_DISTANCE_SQ = 0.15f * 0.15f;
    private static final float MATCH_NORMAL = 0.95f;
    /** Contact point moved this far into the box names its cell. */
    private static final float CELL_PROBE = 0.01f;
    /** {@link ContactBuffer#flags}: world support => {@link RigidSolver.Contact#lift}. */
    private static final int LIFT = 1, OFF_FACE = 2;
    private static final float BELLY_SPIN_UP = BodyState.BELLY_SPIN_UP;
    private static final float FOOT = BodyState.FOOT;

    final PhysicsBody owner;
    final BodyState state;
    final StepResult result = new StepResult();
    private final SpringRows rows = new SpringRows();
    boolean active;

    // BodyState scalars, copied at capture; reference fields alias it.
    boolean live;
    boolean predicting;
    double comX, comY, comZ;
    double hullX, hullY, hullZ;
    final Quaternionf hullRotation;
    final Vector3f hullExtents;
    float inverseMass;
    final Matrix3f inverseInertia;
    final Vector3f velocity;
    final Vector3f spin;
    final Vector3f acceleration;
    float spinRetention;
    float maxSpin;
    float margin;
    boolean sprung;
    AABB gather;
    private float[] pieceFrames;
    private int pieceCount;
    private float[] wheelFrames;
    private int wheelCount;
    private float[] feet;
    private int footCount;
    @Nullable
    Coupling coupling;
    boolean carried;
    boolean gear;
    private float restitution;
    private SectionSource sections;

    int body;
    /** World shape frames ({@link NarrowNative#FRAME} each), hull (null = box), native hull handle. */
    private float[] worldFrames = new float[0];
    private ConvexHull[] worldHulls = new ConvexHull[0];
    private long[] worldHandles = new long[0];
    private int worldFrameCount;
    final List<Polytope> worldShapes = new ArrayList<>();
    final List<Polytope> hullShapes = new ArrayList<>();
    int worldShapeCount, hullShapeCount;
    int contactFrom, contactTo, springFrom, springTo;
    final BoxBuffer boxes = new BoxBuffer();
    /** Live islands only (providers read the world). Contact tag = -1 - deck boxes - i. */
    final OrientedBoxes providerBoxes = new OrientedBoxes();
    final List<CollisionProvider.Session> sessions = new ArrayList<>();
    /** Session s owns provider boxes [sessionBase[s], sessionBase[s + 1]). */
    final IntArrayList sessionBase = new IntArrayList();
    /** World contact k's tag, k from contactFrom. */
    private int[] contactTags = new int[0];
    /** Island task: this tick's joint index, -1 none. */
    int couplingJoint = -1;
    /** Captured on the tick thread (bakes); read by the island task. */
    DriveSurface.View surface;
    /** Prism depth behind a fill triangle, blocks. 0.25: hull at 0.47 b/t on a 1:2 ramp went 0.23 deep, past the prism. */
    private static final float FILL_DEPTH = 1.0f;
    /** Hull point this far past a box's xz footprint still reads as over its top, blocks. */
    private static final double TOP_SLACK = 0.02;
    final Polytope box = new Polytope();
    /**
     * Fill steeper and met faster than CRAWL (b/t, horizontal into the facet) => plain contact: lift on a 45 degree
     * facet = dv_y v_h (energy x2), threw a rolled truck 0.39 b/t up; plain at crawl stopped tanks on 1:1 stairs.
     */
    private static final float LIFT_NORMAL = 0.8f, CRAWL = 0.15f;
    /** Overlap past which opposed world contacts read as a wedge (light hull in a 3-block arena: launched 2.4 b/t). */
    private static final float WEDGE_DEPTH = 0.05f;
    /** Probe past a box face for a neighbour, blocks. */
    private static final double FACE_STEP = 1.0e-3;
    /** Normal component below this is no face's. */
    private static final float FACE_COMPONENT = 0.1f;
    /** One hill triangle's vertices, section-local. */
    private final float[] triangle = new float[9];
    /** Union of the world shapes' bounds: one triangle walk per tick, not per shape. */
    private final Polytope reach = new Polytope();
    /** Fill prisms meeting {@link #reach}, walk order; per-prism section and section-local vertices. */
    private final List<Polytope> fills = new ArrayList<>();
    private final List<SurfaceSection> fillSections = new ArrayList<>();
    private float[] fillVertices = new float[9 * 16];
    /** Candidate boxes [6] origin-relative; triangles [13]: section-local vertices, origin - section corner, tag bits. */
    private float[] candidateBoxes = new float[6 * 64];
    private float[] candidateVertices = new float[13 * 64];
    /** Origin-relative bounds (min xyz, max xyz) of every candidate box and fill prism; pieces wholly outside are never posed. */
    private final float[] envelope = new float[6];
    private final Matrix3f rotationMatrix = new Matrix3f();
    private int fillCount;
    /** Hill triangles over the gather columns, walk order. */
    private final List<SurfaceSection> candidateSections = new ArrayList<>();
    private final IntArrayList candidateTriangles = new IntArrayList();

    /** Warm start: hull-frame anchor (from COM), world normal, impulses (n, t1, t2). */
    private float[] cache = new float[0];
    private int cacheCount;
    private float[] springCache = new float[0];

    private StepBody(PhysicsBody owner, BodyState state) {
        this.owner = owner;
        this.state = state;
        hullRotation = state.hullRotation;
        hullExtents = state.hullExtents;
        inverseInertia = state.inverseInertia;
        velocity = state.velocity;
        spin = state.spin;
        acceleration = state.acceleration;
    }

    /** Tick or client thread: created on first use. */
    static StepBody of(PhysicsBody body) {
        BodyState state = body.bodyState();
        StepBody step = (StepBody) state.engine;
        if (step == null) {
            step = new StepBody(body, state);
            state.engine = step;
        }
        return step;
    }

    /** Stepping this tick; island tasks never create. */
    static boolean active(PhysicsBody body) {
        StepBody step = (StepBody) body.bodyState().engine;
        return step != null && step.active;
    }

    static StepBody peek(PhysicsBody body) {
        return (StepBody) body.bodyState().engine;
    }

    /** Tick thread. */
    void capture(boolean async) {
        active = true;
        result.failed = false;
        result.grounded = false;
        result.touched.clear();
        BodyState s = state;
        s.predicting = predicting;
        owner.capture(s, async);
        live = s.live;
        comX = s.comX;
        comY = s.comY;
        comZ = s.comZ;
        hullX = s.hullX;
        hullY = s.hullY;
        hullZ = s.hullZ;
        inverseMass = s.inverseMass;
        spinRetention = s.spinRetention;
        maxSpin = s.maxSpin;
        margin = s.margin;
        sprung = s.sprung;
        gather = s.gather;
        pieceFrames = s.pieceFrames;
        pieceCount = s.pieceCount;
        wheelFrames = s.wheelFrames;
        wheelCount = s.wheelCount;
        feet = s.feet;
        footCount = s.footCount;
        coupling = s.coupling;
        carried = s.carried;
        gear = s.gear;
        restitution = s.restitution;
        sections = s.sections;
        surface = DriveSurface.of(owner.level()).view(owner.level(), gather, CellTops.snapshot(s.cursor));
    }

    /** Island task: coupling rows to the tractor's body. Tick-thread world points from {@link PhysicsBody#capture}. */
    void couplingJoint(RigidSolver solver, StepBody tractor) {
        BodyState s = state;
        double[] o = solver.origin, p = s.couplingPoints;
        RigidSolver.Joint joint = solver.addJoint(tractor.body, body,
                (float) (p[3] - o[0]), (float) (p[4] - o[1]), (float) (p[5] - o[2]),
                (float) (p[0] - o[0]), (float) (p[1] - o[1]), (float) (p[2] - o[2]), s.couplingLength);
        joint.yawLimit = s.couplingYawLimit;
        joint.yaw = s.couplingYaw;
        joint.roll = s.couplingRoll;
        joint.rollAxis.set(s.couplingRollAxis);
        if (coupling.axle()) {
            joint.grip = s.couplingGrip;
            joint.axleAnchor.set((float) (p[6] - o[0]), (float) (p[7] - o[1]), (float) (p[8] - o[2]))
                    .sub(solver.body(body).com);
            joint.axleAxis.set(s.couplingLateral);
        }
        joint.impulse.set(coupling.impulse());
        joint.yawImpulse = coupling.yawImpulse();
        joint.axleImpulse = coupling.axleImpulse();
        joint.rollImpulse = coupling.rollImpulse();
    }

    void storeCoupling(RigidSolver.Joint joint) {
        coupling.impulse().set(joint.impulse);
        coupling.impulses(joint.yawImpulse, joint.axleImpulse, joint.rollImpulse);
    }


    /** Island task. */
    void addBody(RigidSolver solver) {
        body = solver.bodyCount();
        RigidSolver.Body b = solver.addBody();
        b.inverseMass = inverseMass;
        b.inverseInertia.set(inverseInertia);
        b.com.set((float) (comX - solver.origin[0]), (float) (comY - solver.origin[1]), (float) (comZ - solver.origin[2]));
        b.velocity.set(velocity);
        b.spin.set(spin);
        b.acceleration.set(acceleration);
        b.spinRetention = spinRetention;
        b.maxSpin = maxSpin;
    }

    /** Island task: vehicle-vehicle (active OBBs) shapes, origin-relative. */
    void poseShapes(double[] origin) {
        float hx = (float) (hullX - origin[0]), hy = (float) (hullY - origin[1]), hz = (float) (hullZ - origin[2]);
        Vector3f offset = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        hullShapeCount = 0;
        for (int i = 0; i < pieceCount; i++) {
            int o = i * 10;
            hullRotation.transform(offset.set(pieceFrames[o], pieceFrames[o + 1], pieceFrames[o + 2]));
            hullRotation.mul(rotation.set(pieceFrames[o + 3], pieceFrames[o + 4], pieceFrames[o + 5], pieceFrames[o + 6]),
                    rotation);
            Polytope piece = shape(hullShapes, hullShapeCount++);
            piece.box(hx + offset.x, hy + offset.y, hz + offset.z, rotation, pieceFrames[o + 7], pieceFrames[o + 8],
                    pieceFrames[o + 9]);
            piece.tag = i;
        }
    }

    /**
     * World shapes (main, compound pieces, wheels; frame index = tag), origin-relative: {@link #worldFrames},
     * {@link #worldHulls}; {@code handles} => {@link #worldHandles} current.
     */
    private void worldFrames(double[] origin, boolean handles) {
        float hx = (float) (hullX - origin[0]), hy = (float) (hullY - origin[1]), hz = (float) (hullZ - origin[2]);
        CompoundBody compound = owner.compound();
        int count = 1 + compound.pieceCount() + wheelCount + footCount;
        if (worldHulls.length < count) {
            worldHulls = Arrays.copyOf(worldHulls, count);
            worldHandles = Arrays.copyOf(worldHandles, count);
            worldFrames = new float[count * NarrowNative.FRAME];
        }
        worldFrameCount = count;
        ConvexHull mainShape = owner.mainPiece().hull();
        frame(0, mainShape, hx, hy, hz, hullRotation, hullExtents.x, hullExtents.y, hullExtents.z,
                mainShape == null ? hullExtents.length() : Float.NaN, handles);
        Vector3f offset = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        for (int i = 0, pieces = compound.pieceCount(); i < pieces; i++) {
            hullRotation.transform(offset.set(compound.pieceOffset(i)));
            hullRotation.mul(compound.pieceRotation(i), rotation);
            frame(1 + i, compound.pieceShape(i), hx + offset.x, hy + offset.y, hz + offset.z, rotation, 0, 0, 0, Float.NaN,
                    handles);
        }
        for (int i = 0, base = 1 + compound.pieceCount(); i < wheelCount; i++) {
            int o = i * 10;
            hullRotation.transform(offset.set(wheelFrames[o], wheelFrames[o + 1], wheelFrames[o + 2]));
            float px = hx + offset.x, py = hy + offset.y, pz = hz + offset.z;
            hullRotation.mul(rotation.set(wheelFrames[o + 3], wheelFrames[o + 4], wheelFrames[o + 5], wheelFrames[o + 6]),
                    rotation);
            float ex = wheelFrames[o + 7], ey = wheelFrames[o + 8], ez = wheelFrames[o + 9];
            frame(base + i, null, px, py, pz, rotation, ex, ey, ez, offset.set(ex, ey, ez).length(), handles);
        }
        for (int i = 0, base = 1 + compound.pieceCount() + wheelCount; i < footCount; i++) {
            hullRotation.transform(offset.set(feet[i * 3], feet[i * 3 + 1], feet[i * 3 + 2]));
            frame(base + i, null, hx + offset.x, hy + offset.y, hz + offset.z, hullRotation, FOOT, FOOT, FOOT,
                    FOOT * 1.7321f, handles);
        }
    }

    private void frame(int s, @Nullable ConvexHull hull, float x, float y, float z, Quaternionf rotation, float ex, float ey,
                       float ez, float radius, boolean handles) {
        float[] f = worldFrames;
        int o = s * NarrowNative.FRAME;
        f[o] = x;
        f[o + 1] = y;
        f[o + 2] = z;
        f[o + 3] = rotation.x;
        f[o + 4] = rotation.y;
        f[o + 5] = rotation.z;
        f[o + 6] = rotation.w;
        f[o + 7] = ex;
        f[o + 8] = ey;
        f[o + 9] = ez;
        f[o + 10] = radius;
        if (worldHulls[s] != hull) {
            worldHulls[s] = hull;
            worldHandles[s] = 0;
        }
        if (handles && hull != null && worldHandles[s] == 0) {
            worldHandles[s] = NarrowNative.hull(hull);
        }
    }

    /** {@link #worldFrames} posed; {@code cull} => shapes whose posed local bounds miss the envelope (+ margin) skipped. */
    private void poseWorldShapes(boolean cull) {
        worldShapeCount = 0;
        Quaternionf rotation = new Quaternionf();
        Vector3f scratch = new Vector3f();
        float[] f = worldFrames;
        for (int s = 0; s < worldFrameCount; s++) {
            int o = s * NarrowNative.FRAME;
            ConvexHull hull = worldHulls[s];
            rotation.set(f[o + 3], f[o + 4], f[o + 5], f[o + 6]);
            if (cull && !(hull == null ? reaches(f[o], f[o + 1], f[o + 2], f[o + 10])
                    : reaches(hull, f[o], f[o + 1], f[o + 2], rotation, scratch))) {
                continue;
            }
            Polytope shape = shape(worldShapes, worldShapeCount++);
            if (hull != null) {
                shape.hull(hull, f[o], f[o + 1], f[o + 2], rotation);
            } else {
                shape.box(f[o], f[o + 1], f[o + 2], rotation, f[o + 7], f[o + 8], f[o + 9]);
            }
            shape.tag = s;
        }
    }

    /** Hull posed at (x, y, z) by rotation: its local bounds box, posed, meets the envelope. */
    private boolean reaches(ConvexHull hull, float x, float y, float z, Quaternionf rotation, Vector3f scratch) {
        rotation.transform(scratch.set((hull.minX() + hull.maxX()) * 0.5f, (hull.minY() + hull.maxY()) * 0.5f,
                (hull.minZ() + hull.maxZ()) * 0.5f));
        float cx = x + scratch.x, cy = y + scratch.y, cz = z + scratch.z;
        float hx = (hull.maxX() - hull.minX()) * 0.5f, hy = (hull.maxY() - hull.minY()) * 0.5f, hz = (hull.maxZ() - hull.minZ()) * 0.5f;
        Matrix3f r = rotation.get(this.rotationMatrix);
        return reaches(cx, cy, cz, Math.abs(r.m00) * hx + Math.abs(r.m10) * hy + Math.abs(r.m20) * hz,
                Math.abs(r.m01) * hx + Math.abs(r.m11) * hy + Math.abs(r.m21) * hz,
                Math.abs(r.m02) * hx + Math.abs(r.m12) * hy + Math.abs(r.m22) * hz);
    }

    private boolean reaches(float x, float y, float z, float radius) {
        return reaches(x, y, z, radius, radius, radius);
    }

    /** Half extents (rx, ry, rz) about (x, y, z); slack (posing rounding) => a skipped shape's bounds miss for sure. */
    private boolean reaches(float x, float y, float z, float rx, float ry, float rz) {
        float slack = 1.0e-3f + margin;
        rx = rx * 1.001f + slack;
        ry = ry * 1.001f + slack;
        rz = rz * 1.001f + slack;
        float[] e = envelope;
        return x - rx <= e[3] && x + rx >= e[0] && y - ry <= e[4] && y + ry >= e[1] && z - rz <= e[5] && z + rz >= e[2];
    }

    private static Polytope shape(List<Polytope> pool, int index) {
        if (index == pool.size()) {
            pool.add(new Polytope());
        }
        return pool.get(index);
    }

    /** Island task: block, drive-surface wedge and deck contacts (static body 0), wheel springs. */
    /** {@code native_} null => Java narrowphase. */
    void worldContacts(RigidSolver solver, ConvexContacts narrow, @Nullable NarrowNative native_, ContactBuffer buffer) {
        double[] origin = solver.origin;
        buffer.clear();
        boxes.clear();
        ChunkCollisionCache.collectBoxes(sections, gather, boxes);
        sessions.clear();
        sessionBase.clear();
        providerBoxes.clear();
        if (live && !predicting) {
            owner.providerBoxes(gather, sessions, sessionBase, providerBoxes);
        }
        sessionBase.add(providerBoxes.size());
        DriveSurface.View view = surface;
        DeckView link = owner.deck();
        boolean deck = link.active();
        int deckCount = deck ? link.deckCount() : 0;
        candidateSections.clear();
        candidateTriangles.clear();
        view.triangles(gather, (section, t) -> {
            candidateSections.add(section);
            candidateTriangles.add(t);
        });
        // Nothing solid in reach (open air, open water): no shape to pose, no contact to find.
        boolean reachable = !boxes.isEmpty() || !candidateTriangles.isEmpty() || deckCount > 0 || providerBoxes.size() > 0;
        worldShapeCount = 0;
        worldFrameCount = 0;
        // Deck / provider boxes are collided by the Java narrowphase per posed shape: no envelope.
        boolean cull = deckCount == 0 && providerBoxes.size() == 0;
        boolean javaPosed = native_ == null || !cull;
        if (reachable) {
            candidates(origin);
            worldFrames(origin, native_ != null);
            if (javaPosed) {
                poseWorldShapes(cull);
            }
        }
        int nativeCount = 0;
        if (native_ != null && worldFrameCount > 0) {
            nativeCount = native_.worldContacts(worldFrameCount, worldFrames, worldHandles, cull, envelope, candidateBoxes,
                    boxes.size(), candidateVertices, candidateTriangles.size(), margin, FILL_DEPTH);
        } else {
            reach.enclose(worldShapes, worldShapeCount);
        }
        fillCount = 0;
        fillSections.clear();
        for (int c = 0, size = worldShapeCount > 0 && native_ == null ? candidateTriangles.size() : 0; c < size; c++) {
            SurfaceSection section = candidateSections.get(c);
            int t = candidateTriangles.getInt(c);
            float ox = candidateVertices[c * 13 + 9], oy = candidateVertices[c * 13 + 10], oz = candidateVertices[c * 13 + 11];
            System.arraycopy(candidateVertices, c * 13, triangle, 0, 9);
            Polytope fill = shape(fills, fillCount);
            if (fill.prismMeets(triangle, 0, ox, oy, oz, FILL_DEPTH, reach, margin)) {
                fill.tag = t;
                fillSections.add(section);
                if (fillVertices.length < (fillCount + 1) * 9) {
                    fillVertices = Arrays.copyOf(fillVertices, fillVertices.length * 2);
                }
                System.arraycopy(triangle, 0, fillVertices, fillCount * 9, 9);
                fillCount++;
            }
        }
        // Reduced per shape: across shapes the kept deepest point flips between pieces (see RigidStep#hullContacts).
        // Native: shape = frame index (culled frames meet nothing); Java posed all when deck / provider boxes.
        for (int s = 0, next = 0, shapes = native_ != null ? worldFrameCount : worldShapeCount; s < shapes; s++) {
            Polytope shape = javaPosed ? worldShapes.get(s) : null;
            int from = buffer.size();
            int fillFrom;
            if (native_ != null) {
                for (; next < nativeCount && native_.shape(next) == s && native_.source(next) >= 0; next++) {
                    copy(native_, next, buffer);
                }
                fillFrom = buffer.size();
                while (next < nativeCount && native_.shape(next) == s) {
                    int source = native_.source(next), before = buffer.size(), c = -1 - source;
                    for (; next < nativeCount && native_.shape(next) == s && native_.source(next) == source; next++) {
                        copy(native_, next, buffer);
                    }
                    System.arraycopy(candidateVertices, c * 13, triangle, 0, 9);
                    faceNormal(buffer, before, candidateSections.get(c), candidateTriangles.getInt(c), triangle, origin);
                }
            } else {
                for (int i = 0, size = boxes.size(); i < size; i++) {
                    float minX = (float) (boxes.minX(i) - origin[0]), minY = (float) (boxes.minY(i) - origin[1]),
                            minZ = (float) (boxes.minZ(i) - origin[2]), maxX = (float) (boxes.maxX(i) - origin[0]),
                            maxY = (float) (boxes.maxY(i) - origin[1]), maxZ = (float) (boxes.maxZ(i) - origin[2]);
                    if (Polytope.boundsMeet(minX, minY, minZ, maxX, maxY, maxZ, shape, margin)) {
                        box.box(minX, minY, minZ, maxX, maxY, maxZ);
                        box.tag = i;
                        narrow.collide(box, shape, margin, buffer);
                    }
                }
                fillFrom = buffer.size();
                for (int f = 0; f < fillCount; f++) {
                    Polytope fill = fills.get(f);
                    if (fill.boundsMeet(shape, margin)) {
                        int before = buffer.size();
                        narrow.collide(fill, shape, margin, buffer);
                        System.arraycopy(fillVertices, f * 9, triangle, 0, 9);
                        faceNormal(buffer, before, fillSections.get(f), fill.tag, triangle, origin);
                    }
                }
            }
            boolean world = worldShape(s);
            if (!world) {
                buffer.retain(from, k -> false);
            }
            int fillTo = buffer.size(), fillStart = world ? fillFrom : from;
            if (!cull) {
                if (deck) {
                    deckContacts(narrow, buffer, shape, link, deckCount, origin);
                }
                if (world) {
                    providerContacts(narrow, buffer, shape, deckCount, origin);
                }
            }
            // Fill => face-normal lift; off-triangle points and steep sides dropped. Blocks inside the hill's cover band
            // are the hill's (step faces, cut crests); other block support => top-face lift.
            buffer.retain(from, k -> {
                if (k >= fillTo) {
                    return true;
                }
                if (k >= fillStart) {
                    if (buffer.flags(k) == OFF_FACE || buffer.normalY(k) <= SurfaceBake.SUPPORT_NORMAL) {
                        return false;
                    }
                    if (buffer.normalY(k) > LIFT_NORMAL
                            || -(velocity.x * buffer.normalX(k) + velocity.z * buffer.normalZ(k)) < CRAWL) {
                        buffer.flag(k, LIFT);
                    }
                    return true;
                }
                if (!exposed(buffer, k, boxes, origin)) {
                    return false;
                }
                // Block-side point = contact clamped into its box: edge-edge midpoints float up to ~0.6 off both edges.
                // Downward normal => ceiling underside (slab-thick cave roof under a hill), never the hill's.
                int i = buffer.tagA(k);
                if (buffer.normalY(k) >= -SurfaceBake.SUPPORT_NORMAL && view.covers(Mth.clamp(buffer.x(k) + origin[0], boxes.minX(i), boxes.maxX(i)),
                        Mth.clamp(buffer.y(k) + origin[1], boxes.minY(i), boxes.maxY(i)),
                        Mth.clamp(buffer.z(k) + origin[2], boxes.minZ(i), boxes.maxZ(i)),
                        buffer.y(k) + buffer.normalY(k) * buffer.separation(k) * 0.5 + origin[1])) {
                    return false;
                }
                if (buffer.normalY(k) > SurfaceBake.SUPPORT_NORMAL) {
                    buffer.flag(k, LIFT);
                    return topFace(buffer, k, boxes, origin);
                }
                return true;
            });
            buffer.reduce(from);
        }
        contactFrom = solver.contactCount();
        // Upright sprung belly frictionless, wheels down or not: high-centred on stair edges (tracks hanging) a 0.5
        // belly braked a T-90 to a stop on a 1:4 hill. Lift contacts => no slide down slopes.
        // Towed on wheels: rolls; the axle row grips sideways (unsprung MIM-104 dragged its belly at 0.5, train stalled).
        // Carried (afloat): heave spring presses a grounded bow into the bed => 0.5 grip >> screw thrust, never backed off.
        // Unsprung gear down: Coulomb 0.5 held a full-thrust A-10 parked (0.0095 b/t per tick zeroed).
        boolean rolling = gear || coupling != null && coupling.axle();
        float friction = rolling || carried || sprung && hullRotation.transform(new Vector3f(0, 1, 0)).y > BELLY_SPIN_UP ? 0 : FRICTION;
        int deckSupports = 0;
        if (contactTags.length < buffer.size()) {
            contactTags = new int[Math.max(buffer.size(), contactTags.length * 2)];
        }
        for (int k = 0, size = buffer.size(); k < size; k++) {
            contactTags[k] = buffer.tagA(k);
            boolean wedged = false;
            if (buffer.separation(k) < -WEDGE_DEPTH) {
                for (int j = 0; j < size && !wedged; j++) {
                    wedged = buffer.separation(j) < -WEDGE_DEPTH && buffer.normalX(k) * buffer.normalX(j)
                            + buffer.normalY(k) * buffer.normalY(j) + buffer.normalZ(k) * buffer.normalZ(j) < -0.5f;
                }
            }
            RigidSolver.Contact c = solver.addContact(0, body, buffer.x(k), buffer.y(k), buffer.z(k),
                    buffer.normalX(k), buffer.normalY(k), buffer.normalZ(k), buffer.separation(k));
            // Any carrier piece: riverine hull top = deck top => reduction kept hull points, rider never parked.
            boolean carrier = buffer.tagA(k) < 0 && -1 - buffer.tagA(k) < deckCount;
            // Carrier support = world support rule: a nose meeting the lowered ramp rides up it.
            c.lift = buffer.flags(k) == LIFT || carrier && buffer.normalY(k) > SurfaceBake.SUPPORT_NORMAL;
            // Deck walls, providers: Coulomb. Deck lift = world belly rule (wheels probe decks): 0.5 on the lip held a tank.
            c.friction = buffer.tagA(k) < 0 && !(carrier && c.lift) ? FRICTION : friction;
            c.restitution = restitution;
            c.wedged = wedged;
            warm(c, c.anchorB);
            if (carrier && buffer.normalY(k) > SurfaceBake.SUPPORT_NORMAL) {
                deckSupports++;
            }
        }
        contactTo = solver.contactCount();
        link.supportContacts(deckSupports + springs(solver));
    }

    private void deckContacts(ConvexContacts narrow, ContactBuffer buffer, Polytope shape, DeckView link, int deckCount,
                              double[] origin) {
        Quaternionf rotation = link.deckRotation();
        Quaternionf piece = new Quaternionf();
        Vector3f centre = new Vector3f();
        for (int i = 0; i < deckCount; i++) {
            // Oriented piece, not its carrier-frame AABB: a lowered ramp's AABB = 2-block wall at the lip.
            rotation.transform(centre.set(link.deckFrame(i, 0), link.deckFrame(i, 1), link.deckFrame(i, 2)));
            rotation.mul(piece.set(link.deckFrame(i, 6), link.deckFrame(i, 7), link.deckFrame(i, 8), link.deckFrame(i, 9)), piece);
            float cx = (float) (link.pivotX() + centre.x - origin[0]), cy = (float) (link.pivotY() + centre.y - origin[1]),
                    cz = (float) (link.pivotZ() + centre.z - origin[2]);
            ConvexHull hull = link.deckHull(i);
            if (hull != null) {
                box.hull(hull, cx, cy, cz, piece);
            } else {
                box.box(cx, cy, cz, piece, link.deckFrame(i, 3), link.deckFrame(i, 4), link.deckFrame(i, 5));
            }
            box.tag = -1 - i;
            if (box.boundsMeet(shape, margin)) {
                int from = buffer.size();
                narrow.collide(box, shape, margin, buffer);
                if (link.landing(i)) {
                    for (int k = from, to = buffer.size(); k < to; k++) {
                        deckTop(buffer, k, piece, cx, cy, cz, link.deckFrame(i, 3), link.deckFrame(i, 4), link.deckFrame(i, 5));
                    }
                }
            }
        }
    }

    /** World frame s ({@link #worldFrames} order) meets blocks, fill, providers; {@link HullTag#world()}. */
    private boolean worldShape(int s) {
        CompoundBody compound = owner.compound();
        return s == 0 ? owner.mainPiece().tag().world() : s > compound.pieceCount() || compound.piece(s - 1).tag().world();
    }

    /**
     * Landing deck contact k over the piece's top, <= {@link DriveSurface#CLIMB} under it => top normal, lift ({@link #topFace}
     * for a tilted box). Hull face against a lowered ramp's lip read horizontal: a tank (hull box to the ground) stopped.
     */
    private static void deckTop(ContactBuffer buffer, int k, Quaternionf rotation, float cx, float cy, float cz,
                                float ex, float ey, float ez) {
        float half = buffer.separation(k) * 0.5f;
        Vector3f local = rotation.transformInverse(new Vector3f(buffer.x(k) + buffer.normalX(k) * half - cx,
                buffer.y(k) + buffer.normalY(k) * half - cy, buffer.z(k) + buffer.normalZ(k) * half - cz));
        float gap = local.y - ey;
        if (Math.abs(local.x) > ex + TOP_SLACK || Math.abs(local.z) > ez + TOP_SLACK || gap < -DriveSurface.CLIMB) {
            return;
        }
        Vector3f up = rotation.transform(new Vector3f(0, 1, 0));
        local.y = ey + gap * 0.5f;
        rotation.transform(local).add(cx, cy, cz);
        buffer.set(k, local.x, local.y, local.z, up.x, up.y, up.z, gap, LIFT);
    }

    private void providerContacts(ConvexContacts narrow, ContactBuffer buffer, Polytope shape, int deckCount, double[] origin) {
        OrientedBoxes boxes = providerBoxes;
        Quaternionf rotation = new Quaternionf();
        for (int i = 0, size = boxes.size(); i < size; i++) {
            rotation.set(boxes.frame(i, 0), boxes.frame(i, 1), boxes.frame(i, 2), boxes.frame(i, 3));
            box.box((float) (boxes.centreX(i) - origin[0]), (float) (boxes.centreY(i) - origin[1]), (float) (boxes.centreZ(i) - origin[2]),
                    rotation, boxes.frame(i, 4), boxes.frame(i, 5), boxes.frame(i, 6));
            box.tag = -1 - deckCount - i;
            if (box.boundsMeet(shape, margin)) {
                narrow.collide(box, shape, margin, buffer);
            }
        }
    }

    /** Tick thread (live island): loaded provider boxes reported, sessions closed. */
    private void endSessions(RigidSolver solver) {
        if (sessions.isEmpty()) {
            return;
        }
        DeckView deck = owner.deck();
        int deckCount = deck.active() ? deck.deckCount() : 0;
        for (int k = contactFrom; k < contactTo; k++) {
            int i = -1 - deckCount - contactTags[k - contactFrom];
            if (i < 0 || solver.contact(k).totalNormalImpulse <= 0) {
                continue;
            }
            int s = 0;
            while (sessionBase.getInt(s + 1) <= i) {
                s++;
            }
            sessions.get(s).touched(i - sessionBase.getInt(s));
        }
        for (int s = 0, size = sessions.size(); s < size; s++) {
            sessions.get(s).end();
        }
        sessions.clear();
    }

    /** @return rows standing on a carrier deck (deck support, like belly contacts) */
    private int springs(RigidSolver solver) {
        springFrom = solver.springCount();
        if (!sprung) {
            springTo = springFrom;
            return 0;
        }
        rows.clear();
        owner.springs(rows, surface);
        int n = rows.size();
        if (springCache.length < n * 2) {
            springCache = Arrays.copyOf(springCache, Math.max(springCache.length * 2, n * 2));
        }
        int onDeck = 0;
        for (int slot = 0; slot < n; slot++) {
            if (!rows.grounded(slot)) {
                springCache[slot * 2] = 0;
                springCache[slot * 2 + 1] = 0;
                continue;
            }
            if (rows.onDeck(slot)) {
                onDeck++;
            }
            RigidSolver.Spring spring = solver.addSpring(body, (float) (rows.anchor(slot, 0) - solver.origin[0]),
                    (float) (rows.anchor(slot, 1) - solver.origin[1]), (float) (rows.anchor(slot, 2) - solver.origin[2]), 0, 1, 0);
            spring.ratio = rows.ratio(slot);
            spring.compression = rows.compression(slot);
            spring.min = rows.min(slot);
            spring.max = rows.max(slot);
            spring.stiffness = rows.stiffness(slot);
            spring.damping = rows.damping(slot);
            spring.impulse = springCache[slot * 2];
            spring.stopImpulse = springCache[slot * 2 + 1];
        }
        springTo = solver.springCount();
        return onDeck;
    }

    /** Previous tick's impulses for the nearest matching point. */
    private void warm(RigidSolver.Contact c, Vector3f anchor) {
        Vector3f local = hullRotation.transformInverse(new Vector3f(anchor));
        int best = -1;
        float bestDistance = MATCH_DISTANCE_SQ;
        for (int i = 0; i < cacheCount; i++) {
            int o = i * 9;
            float dx = cache[o] - local.x, dy = cache[o + 1] - local.y, dz = cache[o + 2] - local.z;
            float distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance && cache[o + 3] * c.normal.x + cache[o + 4] * c.normal.y + cache[o + 5] * c.normal.z > MATCH_NORMAL) {
                bestDistance = distance;
                best = i;
            }
        }
        if (best >= 0) {
            c.normalImpulse = cache[best * 9 + 6];
            c.tangentImpulse1 = cache[best * 9 + 7];
            c.tangentImpulse2 = cache[best * 9 + 8];
        }
    }

    /** Island task, after the solve: results, caches, impacts, then {@link PhysicsBody#integrate}. */
    void finish(RigidSolver solver) {
        RigidSolver.Body b = solver.body(body);
        StepResult r = result;
        storeCache(solver);
        endSessions(solver);
        LongArrayList blockingCells = r.blockingCells;
        blockingCells.clear();
        double ramSpeed = 0;
        double impactX = 0, impactY = 0, impactZ = 0, impactWeight = 0;
        boolean belly = false;
        double[] origin = solver.origin;
        for (int k = contactFrom; k < contactTo; k++) {
            RigidSolver.Contact c = solver.contact(k);
            if (c.totalNormalImpulse <= 0) {
                continue;
            }
            belly = true;
            impactWeight += c.totalNormalImpulse;
            impactX += c.totalNormalImpulse * (c.anchorB.x + b.com.x);
            impactY += c.totalNormalImpulse * (c.anchorB.y + b.com.y);
            impactZ += c.totalNormalImpulse * (c.anchorB.z + b.com.z);
            if (c.normal.y > SurfaceBake.SUPPORT_NORMAL) {
                r.grounded = true;
                continue;
            }
            double px = c.anchorB.x + b.com.x + origin[0] - c.normal.x * CELL_PROBE;
            double py = c.anchorB.y + b.com.y + origin[1] - c.normal.y * CELL_PROBE;
            double pz = c.anchorB.z + b.com.z + origin[2] - c.normal.z * CELL_PROBE;
            long cell = BlockPos.asLong(Mth.floor(px), Mth.floor(py), Mth.floor(pz));
            if (!blockingCells.contains(cell)) {
                blockingCells.add(cell);
            }
            ramSpeed = Math.max(ramSpeed, -c.approach());
        }
        float wheelLoad = 0;
        double groundWeight = 0, groundY = 0;
        for (int k = springFrom; k < springTo; k++) {
            RigidSolver.Spring s = solver.spring(k);
            float load = s.totalImpulse * s.ratio;
            wheelLoad += load;
            groundWeight += load;
            groundY += load * (s.anchor.y + b.com.y + origin[1]);
        }
        float bellyLoad = 0;
        for (int k = contactFrom; k < contactTo; k++) {
            RigidSolver.Contact c = solver.contact(k);
            if (c.normal.y > SurfaceBake.SUPPORT_NORMAL) {
                bellyLoad += c.totalNormalImpulse;
            }
        }
        // |dv| the contacts cost beyond free flight; gravity support alone stays far under the threshold.
        double ex = b.velocity.x - velocity.x - acceleration.x, ey = b.velocity.y - velocity.y - acceleration.y;
        double ez = b.velocity.z - velocity.z - acceleration.z;
        r.velocityDiff = Math.sqrt(ex * ex + ey * ey + ez * ez);
        r.impactWeight = impactWeight;
        if (impactWeight > 0) {
            r.impactX = impactX / impactWeight + origin[0];
            r.impactY = impactY / impactWeight + origin[1];
            r.impactZ = impactZ / impactWeight + origin[2];
        }
        r.belly = belly;
        r.ramSpeed = ramSpeed;
        r.wheelLoad = wheelLoad;
        r.bellyLoad = bellyLoad;
        r.groundY = groundWeight > 0 ? groundY / groundWeight : Double.NaN;
        r.dp = b.dp;
        r.dq = b.dq;
        r.velocity = b.velocity;
        r.spin = b.spin;
        if (RigidStep.DEBUG) {
            double hx = velocity.x, hz = velocity.z, hl = Math.sqrt(hx * hx + hz * hz);
            StringBuilder line = new StringBuilder();
            float along = 0;
            for (int k = contactFrom; k < contactTo; k++) {
                RigidSolver.Contact c = solver.contact(k);
                if (hl > 1e-4) {
                    along += (float) ((c.normal.x * hx + c.normal.z * hz) / hl * c.totalNormalImpulse * inverseMass);
                }
                if (Boolean.getBoolean("wflib.physics.debugAll") || c.totalNormalImpulse > 1e-5 && Math.abs(c.normal.y) < 0.999f) {
                    line.append(String.format(java.util.Locale.ROOT, " [%s n %.2f,%.2f,%.2f s %.3f dv %.4f p %.2f,%.2f,%.2f]", c.lift ? "L" : "", c.normal.x, c.normal.y, c.normal.z,
                            c.debugSeparation(), c.totalNormalImpulse * inverseMass, c.anchorB.x + b.com.x + origin[0], c.anchorB.y + b.com.y + origin[1], c.anchorB.z + b.com.z + origin[2]));
                }
            }
            LOGGER.info(String.format(java.util.Locale.ROOT,
                    "[climbdbg] %d com %.2f,%.2f,%.2f up %.3f w %.4f,%.4f,%.4f vin %.3f,%.3f,%.3f vout %.3f,%.3f,%.3f load %.4f contacts %d along %.4f%s",
                    owner.getId(), b.com.x + origin[0], b.com.y + origin[1], b.com.z + origin[2], hullRotation.transform(new Vector3f(0, 1, 0)).y, b.spin.x, b.spin.y, b.spin.z,
                    velocity.x, velocity.y, velocity.z, b.velocity.x, b.velocity.y, b.velocity.z,
                    wheelLoad * inverseMass, contactTo - contactFrom, along, line));
        }
        owner.integrate(r);
    }

    /**
     * Block contact k: normal components across faces a neighbouring box covers removed, separation scaled by what is
     * left; false => nothing left. Boxes merge per section: SAT edge axis at a seam, n (0, 0.56, -0.83), stopped every
     * wheeled vehicle at z 32 and flipped it.
     */
    private static boolean exposed(ContactBuffer buffer, int k, BoxBuffer boxes, double[] origin) {
        int i = buffer.tagA(k);
        double x = Mth.clamp(buffer.x(k) + origin[0], boxes.minX(i) + FACE_STEP, boxes.maxX(i) - FACE_STEP);
        double y = Mth.clamp(buffer.y(k) + origin[1], boxes.minY(i) + FACE_STEP, boxes.maxY(i) - FACE_STEP);
        double z = Mth.clamp(buffer.z(k) + origin[2], boxes.minZ(i) + FACE_STEP, boxes.maxZ(i) - FACE_STEP);
        float nx = buffer.normalX(k), ny = buffer.normalY(k), nz = buffer.normalZ(k);
        if (nx > FACE_COMPONENT && solid(boxes, boxes.maxX(i) + FACE_STEP, y, z)
                || nx < -FACE_COMPONENT && solid(boxes, boxes.minX(i) - FACE_STEP, y, z)) {
            nx = 0;
        }
        if (ny > FACE_COMPONENT && solid(boxes, x, boxes.maxY(i) + FACE_STEP, z)
                || ny < -FACE_COMPONENT && solid(boxes, x, boxes.minY(i) - FACE_STEP, z)) {
            ny = 0;
        }
        if (nz > FACE_COMPONENT && solid(boxes, x, y, boxes.maxZ(i) + FACE_STEP)
                || nz < -FACE_COMPONENT && solid(boxes, x, y, boxes.minZ(i) - FACE_STEP)) {
            nz = 0;
        }
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nx == buffer.normalX(k) && ny == buffer.normalY(k) && nz == buffer.normalZ(k)) {
            return true;
        }
        if (length < FACE_COMPONENT) {
            return false;
        }
        buffer.set(k, buffer.x(k), buffer.y(k), buffer.z(k), nx / length, ny / length, nz / length,
                buffer.separation(k) * length, buffer.flags(k));
        return true;
    }

    private static boolean solid(BoxBuffer boxes, double x, double y, double z) {
        for (int i = 0, size = boxes.size(); i < size; i++) {
            if (x > boxes.minX(i) && x < boxes.maxX(i) && y > boxes.minY(i) && y < boxes.maxY(i) && z > boxes.minZ(i)
                    && z < boxes.maxZ(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Block support contact k => box top normal, top separation, when the hull point is over the box; else false (the
     * neighbouring box carries it). Boxes merge per section: SAT edge axes at section seams read n (0.57, 0.81) =>
     * measured as a steep ramp, launched one side (light hull rolled weaving along a terrace).
     */
    private static boolean topFace(ContactBuffer buffer, int k, BoxBuffer boxes, double[] origin) {
        int i = buffer.tagA(k);
        float half = buffer.separation(k) * 0.5f;
        float hx = buffer.x(k) + buffer.normalX(k) * half, hy = buffer.y(k) + buffer.normalY(k) * half,
                hz = buffer.z(k) + buffer.normalZ(k) * half;
        double wx = hx + origin[0], wz = hz + origin[2];
        if (wx < boxes.minX(i) - TOP_SLACK || wx > boxes.maxX(i) + TOP_SLACK || wz < boxes.minZ(i) - TOP_SLACK
                || wz > boxes.maxZ(i) + TOP_SLACK) {
            return false;
        }
        float gap = (float) (hy + origin[1] - boxes.maxY(i));
        buffer.set(k, hx, hy - gap * 0.5f, hz, 0, 1, 0, gap, LIFT);
        return true;
    }

    /** {@link #candidateBoxes}, {@link #candidateVertices}, envelope. Degenerate triangle => NaN prism (meets nothing): unbounded. */
    private void candidates(double[] origin) {
        int boxCount = boxes.size(), triangleCount = candidateTriangles.size();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        if (candidateBoxes.length < boxCount * 6) {
            candidateBoxes = new float[Math.max(boxCount * 6, candidateBoxes.length * 2)];
        }
        float[] b = candidateBoxes;
        for (int i = 0; i < boxCount; i++) {
            b[i * 6] = (float) (boxes.minX(i) - origin[0]);
            b[i * 6 + 1] = (float) (boxes.minY(i) - origin[1]);
            b[i * 6 + 2] = (float) (boxes.minZ(i) - origin[2]);
            b[i * 6 + 3] = (float) (boxes.maxX(i) - origin[0]);
            b[i * 6 + 4] = (float) (boxes.maxY(i) - origin[1]);
            b[i * 6 + 5] = (float) (boxes.maxZ(i) - origin[2]);
            minX = Math.min(minX, b[i * 6]);
            minY = Math.min(minY, b[i * 6 + 1]);
            minZ = Math.min(minZ, b[i * 6 + 2]);
            maxX = Math.max(maxX, b[i * 6 + 3]);
            maxY = Math.max(maxY, b[i * 6 + 4]);
            maxZ = Math.max(maxZ, b[i * 6 + 5]);
        }
        if (candidateVertices.length < triangleCount * 13) {
            candidateVertices = new float[Math.max(triangleCount * 13, candidateVertices.length * 2)];
        }
        float[] v = candidateVertices;
        for (int c = 0; c < triangleCount; c++) {
            SurfaceSection section = candidateSections.get(c);
            int t = candidateTriangles.getInt(c), o = c * 13;
            section.vertices(t, triangle);
            System.arraycopy(triangle, 0, v, o, 9);
            float ox = (float) (origin[0] - section.sx * 16), oy = (float) (origin[1] - section.sy * 16),
                    oz = (float) (origin[2] - section.sz * 16);
            v[o + 9] = ox;
            v[o + 10] = oy;
            v[o + 11] = oz;
            v[o + 12] = Float.intBitsToFloat(t);
            float ax = triangle[3] - triangle[0], ay = triangle[4] - triangle[1], az = triangle[5] - triangle[2];
            float bx = triangle[6] - triangle[0], by = triangle[7] - triangle[1], bz = triangle[8] - triangle[2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float scale = FILL_DEPTH / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(scale < Float.POSITIVE_INFINITY)) {
                continue;
            }
            // Prism = triangle + its copy at -n depth.
            float dx = Math.min(0, -nx * scale), dy = Math.min(0, -ny * scale), dz = Math.min(0, -nz * scale);
            float ex = Math.max(0, -nx * scale), ey = Math.max(0, -ny * scale), ez = Math.max(0, -nz * scale);
            for (int k = 0; k < 9; k += 3) {
                float x = triangle[k] - ox, y = triangle[k + 1] - oy, z = triangle[k + 2] - oz;
                minX = Math.min(minX, x + dx);
                minY = Math.min(minY, y + dy);
                minZ = Math.min(minZ, z + dz);
                maxX = Math.max(maxX, x + ex);
                maxY = Math.max(maxY, y + ey);
                maxZ = Math.max(maxZ, z + ez);
            }
        }
        envelope[0] = minX;
        envelope[1] = minY;
        envelope[2] = minZ;
        envelope[3] = maxX;
        envelope[4] = maxY;
        envelope[5] = maxZ;
    }

    private static void copy(NarrowNative native_, int k, ContactBuffer buffer) {
        buffer.add(native_.x(k), native_.y(k), native_.z(k), native_.normalX(k), native_.normalY(k), native_.normalZ(k),
                native_.separation(k), native_.tagA(k), native_.tagB(k));
    }

    /**
     * Fill contacts [from, size) of triangle t: hull point over the triangle => face normal, plane separation; else
     * OFF_FACE. SAT edge axes at triangle seams (section borders) gave n (0.57, 0.81, -0.12) lift
     * contacts: sideways kicks, light hull rolled weaving at 12 m/s.
     */
    private static void faceNormal(ContactBuffer buffer, int from, SurfaceSection section, int t, float[] v, double[] origin) {
        float ax = v[3] - v[0], ay = v[4] - v[1], az = v[5] - v[2];
        float bx = v[6] - v[0], by = v[7] - v[1], bz = v[8] - v[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        // Vertex 0 relative to the solver origin.
        float x0 = (float) (section.sx * 16 - origin[0]) + v[0], y0 = (float) (section.sy * 16 - origin[1]) + v[1],
                z0 = (float) (section.sz * 16 - origin[2]) + v[2];
        for (int k = from, size = buffer.size(); k < size; k++) {
            float half = buffer.separation(k) * 0.5f;
            float hx = buffer.x(k) + buffer.normalX(k) * half, hy = buffer.y(k) + buffer.normalY(k) * half,
                    hz = buffer.z(k) + buffer.normalZ(k) * half;
            if (Double.isNaN(section.height(t, hx + origin[0], hz + origin[2]))) {
                buffer.flag(k, OFF_FACE);
                continue;
            }
            float gap = (hx - x0) * nx + (hy - y0) * ny + (hz - z0) * nz;
            buffer.set(k, hx - nx * gap * 0.5f, hy - ny * gap * 0.5f, hz - nz * gap * 0.5f, nx, ny, nz, gap, 0);
        }
    }

    private void storeCache(RigidSolver solver) {
        int count = contactTo - contactFrom;
        if (cache.length < count * 9) {
            cache = new float[Math.max(count * 9, cache.length * 2)];
        }
        Vector3f local = new Vector3f();
        for (int k = 0; k < count; k++) {
            RigidSolver.Contact c = solver.contact(contactFrom + k);
            hullRotation.transformInverse(local.set(c.anchorB));
            int o = k * 9;
            cache[o] = local.x;
            cache[o + 1] = local.y;
            cache[o + 2] = local.z;
            cache[o + 3] = c.normal.x;
            cache[o + 4] = c.normal.y;
            cache[o + 5] = c.normal.z;
            cache[o + 6] = c.normalImpulse;
            cache[o + 7] = c.tangentImpulse1;
            cache[o + 8] = c.tangentImpulse2;
        }
        cacheCount = count;
        int spring = springFrom;
        for (int slot = 0, n = sprung ? rows.size() : 0; slot < n; slot++) {
            if (rows.grounded(slot) && spring < springTo) {
                RigidSolver.Spring s = solver.spring(spring++);
                springCache[slot * 2] = s.impulse;
                springCache[slot * 2 + 1] = s.stopImpulse;
            }
        }
    }

    /** Warm-start caches (snapshot). */
    void writeWarm(FriendlyByteBuf buf) {
        buf.writeVarInt(cacheCount);
        for (int i = 0, n = cacheCount * 9; i < n; i++) {
            buf.writeFloat(cache[i]);
        }
        buf.writeVarInt(springCache.length);
        for (float impulse : springCache) {
            buf.writeFloat(impulse);
        }
    }

    void readWarm(FriendlyByteBuf buf) {
        cacheCount = buf.readVarInt();
        if (cache.length < cacheCount * 9) {
            cache = new float[cacheCount * 9];
        }
        for (int i = 0, n = cacheCount * 9; i < n; i++) {
            cache[i] = buf.readFloat();
        }
        int springs = buf.readVarInt();
        if (springCache.length != springs) {
            springCache = new float[springs];
        }
        for (int i = 0; i < springs; i++) {
            springCache[i] = buf.readFloat();
        }
    }

    /** Task failed: no motion this tick. */
    void hold() {
        result.failed = true;
        owner.hold();
    }
}
