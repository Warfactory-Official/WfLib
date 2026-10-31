package com.wf.wflib.physics.gametest;

import com.wf.wflib.geom.api.OBB;
import com.wf.wflib.physics.api.BodyState;
import com.wf.wflib.physics.api.CellTops;
import com.wf.wflib.physics.api.CollisionProvider;
import com.wf.wflib.physics.api.CompoundBody;
import com.wf.wflib.physics.api.ConvexHull;
import com.wf.wflib.physics.api.DeckBody;
import com.wf.wflib.physics.api.DeckCarrier;
import com.wf.wflib.physics.api.DeckFrame;
import com.wf.wflib.physics.api.DeckSnapshot;
import com.wf.wflib.physics.api.DeckView;
import com.wf.wflib.physics.api.HullPiece;
import com.wf.wflib.physics.api.HullTag;
import com.wf.wflib.physics.api.OrientedBoxes;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.SpringRows;
import com.wf.wflib.physics.api.StepResult;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic deck carrier, never added to the level: one hull box + three deck pieces (floor and tilted ramp = landing,
 * side wall = not), posed by the test.
 */
final class DeckHull extends Marker implements DeckBody {

    /** Per piece: centre xyz, half extents xyz, rotation about z (deg), landing. */
    static final float[][] PIECES = {
            {0, -0.5f, 0, 5, 0.5f, 8, 0, 1},
            {4.5f, 1, 0, 0.5f, 1, 8, 0, 0},
            {-2, 0.4f, 4, 1.5f, 0.2f, 1.5f, 12, 1}};

    final DeckSnapshot snapshot = new DeckSnapshot();
    private final List<Entity> riders = new ArrayList<>();
    private final Quaternionf rotation = new Quaternionf();
    private final OBB obb;
    private final HullPiece piece;

    DeckHull(Level level, Vector3f extents) {
        super(EntityType.MARKER, level);
        obb = new OBB(new Vector3f(), new Vector3f(extents), new Quaternionf());
        Vector3f zero = new Vector3f();
        Quaternionf identity = new Quaternionf();
        piece = new HullPiece() {
            @Override public OBB obb() { return obb; }
            @Override public ConvexHull hull() { return null; }
            @Override public HullTag tag() { return HullTag.DEFAULT; }
            @Override public Vector3f localCenter() { return zero; }
            @Override public Quaternionf localRotation() { return identity; }
            @Override public boolean isSolid() { return true; }
        };
    }

    void pose(double x, double y, double z, float yaw, float pitch, float roll) {
        setPos(x, y, z);
        setYRot(yaw);
        DeckFrame.fromEulerYXZ(yaw, pitch, roll, rotation);
        obb.setCenter(x, y, z);
        obb.setRotation(rotation);
        int n = PIECES.length;
        float[] boxes = snapshot.boxBuffer(n);
        float[] deck = snapshot.deckBoxBuffer(n);
        snapshot.shapeBuffers(n);
        float[] frames = snapshot.frames();
        boolean[] landing = snapshot.landing();
        int landings = 0;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < n; i++) {
            float[] p = PIECES[i];
            Quaternionf local = new Quaternionf().rotationZ((float) Math.toRadians(p[6]));
            float bx0 = Float.POSITIVE_INFINITY, by0 = bx0, bz0 = bx0;
            float bx1 = Float.NEGATIVE_INFINITY, by1 = bx1, bz1 = bx1;
            for (int c = 0; c < 8; c++) {
                local.transform(corner.set((c & 1) == 0 ? -p[3] : p[3], (c & 2) == 0 ? -p[4] : p[4],
                        (c & 4) == 0 ? -p[5] : p[5])).add(p[0], p[1], p[2]);
                bx0 = Math.min(bx0, corner.x); by0 = Math.min(by0, corner.y); bz0 = Math.min(bz0, corner.z);
                bx1 = Math.max(bx1, corner.x); by1 = Math.max(by1, corner.y); bz1 = Math.max(bz1, corner.z);
                Vector3f world = rotation.transform(new Vector3f(corner));
                minX = Math.min(minX, x + world.x); minY = Math.min(minY, y + world.y); minZ = Math.min(minZ, z + world.z);
                maxX = Math.max(maxX, x + world.x); maxY = Math.max(maxY, y + world.y); maxZ = Math.max(maxZ, z + world.z);
            }
            float[] box = {bx0, by0, bz0, bx1, by1, bz1};
            System.arraycopy(box, 0, boxes, i * 6, 6);
            int f = i * DeckSnapshot.FRAME;
            frames[f] = p[0]; frames[f + 1] = p[1]; frames[f + 2] = p[2];
            frames[f + 3] = p[3]; frames[f + 4] = p[4]; frames[f + 5] = p[5];
            frames[f + 6] = local.x; frames[f + 7] = local.y; frames[f + 8] = local.z; frames[f + 9] = local.w;
            landing[i] = p[7] != 0;
            if (landing[i]) {
                System.arraycopy(box, 0, deck, landings++ * 6, 6);
            }
        }
        snapshot.set(rotation, yaw, x, y, z, n, landings);
        setBoundingBox(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    OBB hullObb() {
        return obb;
    }

    @Override public DeckSnapshot deckSnapshot() { return snapshot; }
    @Override public List<Entity> deckRiders() { return riders; }
    @Override public boolean collides() { return true; }
    @Override public SoundType deckSoundType() { return SoundType.METAL; }
    @Override public void support(Entity entity) { }
    @Override @Nullable public DeckCarrier mountedOn() { return null; }
    @Override public Vec3 carrierCenterOffset() { return Vec3.ZERO; }
    @Override public double lerpTargetX() { return getX(); }
    @Override public double lerpTargetY() { return getY(); }
    @Override public double lerpTargetZ() { return getZ(); }
    @Override public float lerpTargetXRot() { return getXRot(); }
    @Override public float lerpTargetYRot() { return getYRot(); }
    @Override public float lerpTargetZRot() { return 0; }

    @Override public boolean steppable() { return false; }
    @Override public BodyState bodyState() { throw new UnsupportedOperationException(); }
    @Override public void capture(BodyState s, boolean async) { throw new UnsupportedOperationException(); }
    @Override public HullPiece mainPiece() { return piece; }
    @Override public CompoundBody compound() { throw new UnsupportedOperationException(); }
    @Override public DeckView deck() { throw new UnsupportedOperationException(); }
    @Override public void neighbours(AABB bounds, List<PhysicsBody> out) { }
    @Override public List<? extends PhysicsBody> coupledBehind() { return List.of(); }
    @Override public boolean wakeForStep() { return false; }
    @Override public boolean collidesWith(PhysicsBody other) { return false; }
    @Override public void providerBoxes(AABB bounds, List<CollisionProvider.Session> sessions, IntArrayList base,
                                        OrientedBoxes out) { }
    @Override public void springs(SpringRows rows, CellTops surface) { }
    @Override public void integrate(StepResult r) { }
    @Override public void complete(StepResult r) { }
    @Override public void hold() { }
}
