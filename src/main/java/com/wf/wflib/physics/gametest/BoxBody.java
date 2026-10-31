package com.wf.wflib.physics.gametest;

import com.wf.wflib.geom.api.OBB;
import com.wf.wflib.physics.api.BodyState;
import com.wf.wflib.physics.api.CellTops;
import com.wf.wflib.physics.api.ChunkCollisionCache;
import com.wf.wflib.physics.api.CollisionProvider;
import com.wf.wflib.physics.api.CompoundBody;
import com.wf.wflib.physics.api.ConvexHull;
import com.wf.wflib.physics.api.DeckView;
import com.wf.wflib.physics.api.HullPiece;
import com.wf.wflib.physics.api.HullTag;
import com.wf.wflib.physics.api.OrientedBoxes;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.SpringRows;
import com.wf.wflib.physics.api.StepResult;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Synthetic body: one box or hull piece, gravity, no springs, coupling or deck. Pose applied at flush. */
final class BoxBody implements PhysicsBody {

    static final float G = 9.8f / 400;
    private static final float MARGIN = 0.1f;

    private static final DeckView NO_DECK = new DeckView() {
        @Override public boolean active() { return false; }
        @Override public PhysicsBody carrier() { return null; }
        @Override public int deckCount() { return 0; }
        @Override public float deckFrame(int i, int k) { return 0; }
        @Override public ConvexHull deckHull(int i) { return null; }
        @Override public boolean landing(int i) { return false; }
        @Override public Quaternionf deckRotation() { return new Quaternionf(); }
        @Override public double pivotX() { return 0; }
        @Override public double pivotY() { return 0; }
        @Override public double pivotZ() { return 0; }
        @Override public void supportContacts(int count) { }
    };

    /** Body-vs-body shape = the hull box itself, hull frame. */
    private final float[] pieceFrame;
    private final int id;
    private final ServerLevel level;
    private final List<BoxBody> scene;
    final Vector3f extents;
    @Nullable
    private final ConvexHull hull;
    private final float mass;
    double x, y, z;
    final Quaternionf rotation = new Quaternionf();
    final Vector3f velocity = new Vector3f();
    final Vector3f spin = new Vector3f();
    boolean grounded;
    boolean removed;

    private final BodyState state = new BodyState();
    private final CompoundBody compound = new CompoundBody();
    private final ChunkCollisionCache.PinnedSections pin = new ChunkCollisionCache.PinnedSections();
    private final OBB obb = new OBB(new Vector3f(), new Vector3f(), new Quaternionf());
    private final HullPiece piece;
    private final Vector3f dp = new Vector3f();
    private final Quaternionf dq = new Quaternionf();
    private final Vector3f nextVelocity = new Vector3f();
    private final Vector3f nextSpin = new Vector3f();
    private boolean nextGrounded;
    private boolean moved;

    BoxBody(int id, ServerLevel level, List<BoxBody> scene, Vector3f extents, @Nullable ConvexHull hull, float mass,
            double x, double y, double z, Quaternionf rotation) {
        this.id = id;
        this.level = level;
        this.scene = scene;
        this.extents = extents;
        this.hull = hull;
        this.mass = mass;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation.set(rotation);
        this.pieceFrame = new float[]{0, 0, 0, 0, 0, 0, 1, extents.x, extents.y, extents.z};
        Vector3f zero = new Vector3f();
        Quaternionf identity = new Quaternionf();
        this.piece = new HullPiece() {
            @Override public OBB obb() { return obb; }
            @Override public ConvexHull hull() { return BoxBody.this.hull; }
            @Override public HullTag tag() { return HullTag.DEFAULT; }
            @Override public Vector3f localCenter() { return zero; }
            @Override public Quaternionf localRotation() { return identity; }
            @Override public boolean isSolid() { return true; }
        };
        pose();
    }

    private void pose() {
        obb.setCenter(x, y, z);
        obb.setExtents(extents);
        obb.setRotation(rotation);
    }

    @Override public int getId() { return id; }
    @Override public Level level() { return level; }
    @Override public boolean isRemoved() { return removed; }
    @Override public boolean steppable() { return true; }
    @Override public BodyState bodyState() { return state; }
    @Override public HullPiece mainPiece() { return piece; }
    @Override public CompoundBody compound() { return compound; }
    @Override public DeckView deck() { return NO_DECK; }
    @Override public List<? extends PhysicsBody> coupledBehind() { return List.of(); }
    @Override public boolean wakeForStep() { return false; }
    @Override public boolean collidesWith(PhysicsBody other) { return true; }
    @Override public void providerBoxes(AABB bounds, List<CollisionProvider.Session> sessions, IntArrayList base, OrientedBoxes out) { }
    @Override public void springs(SpringRows rows, CellTops surface) { }
    @Override public void hold() { }

    @Override
    public void neighbours(AABB bounds, List<PhysicsBody> out) {
        for (BoxBody other : scene) {
            if (other != this && other.obb.bounds().intersects(bounds)) {
                out.add(other);
            }
        }
    }

    @Override
    public void capture(BodyState s, boolean async) {
        pose();
        s.pivotX = s.hullX = s.comX = x;
        s.pivotY = s.hullY = s.comY = y;
        s.pivotZ = s.hullZ = s.comZ = z;
        s.entityRotation.set(rotation);
        s.hullRotation.set(rotation);
        s.hullLocalRotation.identity();
        s.hullLocal.zero();
        s.comLocal.zero();
        s.hullExtents.set(extents);
        s.inverseMass = 1 / mass;
        float k = mass / 3, ex = extents.x, ey = extents.y, ez = extents.z;
        Matrix3f r = new Matrix3f().set(rotation);
        r.mul(new Matrix3f().scaling(1 / (k * (ey * ey + ez * ez)), 1 / (k * (ex * ex + ez * ez)),
                1 / (k * (ex * ex + ey * ey))), s.inverseInertia).mul(r.transpose(new Matrix3f()));
        s.velocity.set(velocity);
        s.spin.set(spin);
        s.acceleration.set(0, -G, 0);
        s.spinRetention = 1;
        s.maxSpin = 1;
        s.margin = MARGIN + velocity.length() + spin.length() * extents.length();
        s.restitution = 0;
        s.sprung = false;
        s.gear = false;
        s.carried = false;
        s.pieceCount = 1;
        s.pieceFrames = pieceFrame;
        s.wheelCount = 0;
        s.footCount = 0;
        s.coupling = null;
        s.gather = obb.bounds().expandTowards(velocity.x, velocity.y, velocity.z).inflate(s.margin);
        s.live = s.predicting || !async;
        ChunkCollisionCache cache = ChunkCollisionCache.of(level);
        if (s.live) {
            cache.prepare(level, s.gather);
            s.sections = cache;
        } else {
            cache.prepareAndPin(level, s.gather, pin);
            s.sections = pin;
        }
        s.cursor = ChunkCollisionCache.cursorOver(s.sections);
    }

    @Override
    public void integrate(StepResult r) {
        dp.set(r.dp);
        dq.set(r.dq);
        nextVelocity.set(r.velocity);
        nextSpin.set(r.spin);
        nextGrounded = r.grounded;
        moved = true;
    }

    @Override
    public void complete(StepResult r) {
        if (!moved) {
            return;
        }
        moved = false;
        x += dp.x;
        y += dp.y;
        z += dp.z;
        dq.mul(rotation, rotation).normalize();
        velocity.set(nextVelocity);
        spin.set(nextSpin);
        grounded = nextGrounded;
        pose();
    }

    /** Pose, velocity and spin bits. */
    long hash(long h) {
        double[] d = {x, y, z};
        for (double v : d) {
            h = mix(h, Double.doubleToRawLongBits(v));
        }
        float[] f = {rotation.x, rotation.y, rotation.z, rotation.w, velocity.x, velocity.y, velocity.z, spin.x, spin.y, spin.z};
        for (float v : f) {
            h = mix(h, Float.floatToRawIntBits(v));
        }
        return h;
    }

    static long mix(long h, long v) {
        h ^= v;
        h *= 0x100000001B3L;
        return h ^ (h >>> 29);
    }
}
