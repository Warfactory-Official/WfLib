package com.wf.wflib.physics.api;

import com.wf.wflib.physics.rigid.ContactBuffer;
import com.wf.wflib.physics.rigid.ConvexContacts;
import com.wf.wflib.physics.rigid.Polytope;
import org.joml.Quaternionf;

/** Narrowphase of one posed box against world boxes or another box; normal points at the posed box. One thread per instance. */
public final class BoxContacts {

    private final ConvexContacts narrow = new ConvexContacts();
    private final ContactBuffer out = new ContactBuffer();
    private final Polytope posed = new Polytope(), other = new Polytope();
    private final Quaternionf rotation = new Quaternionf();
    private double originX, originY, originZ;

    /** Coordinates relative to this box's centre from here on. */
    public void pose(double cx, double cy, double cz, double qx, double qy, double qz, double qw, double ex, double ey, double ez) {
        originX = cx;
        originY = cy;
        originZ = cz;
        posed.box(0, 0, 0, rotation.set((float) qx, (float) qy, (float) qz, (float) qw), (float) ex, (float) ey, (float) ez);
    }

    /** @return points; read with the accessors until the next call */
    public int againstWorld(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double margin) {
        out.clear();
        other.box((float) (minX - originX), (float) (minY - originY), (float) (minZ - originZ),
                (float) (maxX - originX), (float) (maxY - originY), (float) (maxZ - originZ));
        return narrow.collide(other, posed, (float) margin, out);
    }

    /** @return points; read with the accessors until the next call */
    public int pair(double cx, double cy, double cz, double qx, double qy, double qz, double qw, double ex, double ey, double ez,
                    double margin) {
        out.clear();
        other.box((float) (cx - originX), (float) (cy - originY), (float) (cz - originZ),
                rotation.set((float) qx, (float) qy, (float) qz, (float) qw), (float) ex, (float) ey, (float) ez);
        return narrow.collide(other, posed, (float) margin, out);
    }

    public double x(int i) {
        return out.x(i) + originX;
    }

    public double y(int i) {
        return out.y(i) + originY;
    }

    public double z(int i) {
        return out.z(i) + originZ;
    }

    public double normalX(int i) {
        return out.normalX(i);
    }

    public double normalY(int i) {
        return out.normalY(i);
    }

    public double normalZ(int i) {
        return out.normalZ(i);
    }

    public double separation(int i) {
        return out.separation(i);
    }
}
