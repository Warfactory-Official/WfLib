package com.wf.wflib.armor.hull;

import com.wf.wflib.armor.api.hull.HullBody;
import com.wf.wflib.armor.api.hull.HullMesh;
import com.wf.wflib.armor.api.hull.HullProfile;
import com.wf.wflib.armor.api.hull.HullWear;
import com.wf.wflib.geom.api.OBB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Main box + yawed turret box far from the origin; node 0 = hull, 1 = turret ({@link #TURRET}); turret roof = 25 mm plate. */
public final class FakeHull implements HullBody, HullMesh {
    public static final String TURRET = "turret";
    public static final double PX = 1000.3, PY = 64.2, PZ = -2000.7;
    static final Vector3f TURRET_PIVOT = new Vector3f(0, 2.4F, 0.3F);

    public final Quaternionf rotation = new Quaternionf().rotateYXZ(0.6F, 0.1F, 0.05F);
    final Quaternionf turretLocal = new Quaternionf().rotateY(0.4F);
    final Quaternionf turretWorld = new Quaternionf(rotation).mul(turretLocal);
    public final OBB main = box(new Vector3f(0, 1, 0), new Vector3f(1.6F, 1, 3), rotation);
    public final OBB turret = box(TURRET_PIVOT, new Vector3f(1, 0.4F, 1.2F), turretWorld);
    public final HullWear wear = new HullWear();
    @Nullable
    public HullProfile profile;
    public boolean mesh = true;

    private OBB box(Vector3f rest, Vector3f extents, Quaternionf rot) {
        Vector3f c = rotation.transform(new Vector3f(rest));
        OBB b = new OBB(new Vector3f(), extents, new Quaternionf(rot));
        b.setCenter(PX + c.x, PY + c.y, PZ + c.z);
        return b;
    }

    /** One surface crossing, shaped like the mesh strike. */
    public record Hit(int node, Vec3 point, Vector3f normal, float armor) implements HullMesh.Strike {
    }

    public Vec3 world(float x, float y, float z) {
        Vector3f v = rotation.transform(new Vector3f(x, y, z));
        return new Vec3(PX + v.x, PY + v.y, PZ + v.z);
    }

    @Override
    public Quaternionf rotYXZ() {
        return new Quaternionf(rotation);
    }

    @Override
    public OBB mainBox() {
        return main;
    }

    @Override
    @Nullable
    public HullMesh hits() {
        return mesh ? this : null;
    }

    @Override
    public List<OBB> getOBBs() {
        return List.of(main, turret);
    }

    @Override
    public HullWear hullWear() {
        return wear;
    }

    @Override
    @Nullable
    public HullProfile hullProfile() {
        return profile;
    }

    @Override
    @Nullable
    public Hit surface(Vec3 from, Vec3 to) {
        OBB.SegmentHit h = OBB.first(getOBBs(), from, to);
        if (h == null) {
            return null;
        }
        int node = h.obb() == main ? 0 : 1;
        Vector3f n = h.obb().faceNormal(h.point());
        float armor = node == 1 && n.dot(turretWorld.transform(new Vector3f(0, 1, 0))) > 0.9F ? 25 : 0;
        return new Hit(node, h.point(), n, armor);
    }

    @Override
    @Nullable
    public String partOf(int node) {
        return node == 1 ? TURRET : null;
    }

    @Override
    public Vector3f restPoint(int node, Vec3 world) {
        Vector3f v = new Vector3f((float) (world.x - PX), (float) (world.y - PY), (float) (world.z - PZ));
        new Quaternionf(rotation).conjugate().transform(v);
        if (node == 1) {
            new Quaternionf(turretLocal).conjugate().transform(v.sub(TURRET_PIVOT)).add(TURRET_PIVOT);
        }
        return v;
    }

    @Override
    public Vector3f restDirection(int node, Vector3f world) {
        return new Quaternionf(node == 1 ? turretWorld : rotation).conjugate().transform(new Vector3f(world));
    }

    @Override
    public Vector3f worldDirection(int node, Vector3f rest) {
        return (node == 1 ? turretWorld : rotation).transform(new Vector3f(rest));
    }
}
