package com.wf.wflib.soldier.client;

import org.joml.Vector3f;

/**
 * A lamp lens held in its wearer's head frame (right, up, forward from the eye along the view), so it can be applied to the
 * wearer's position and rotation of <em>this</em> frame: the drawn lens is only known as of the last entity render, one frame
 * old, which is a visible lag whenever the wearer flies or turns fast. What the render gives is the small, slowly changing
 * part (where the lamp sits on the head, as the body is posed); where the head is comes from the entity right now.
 */
final class LampFrame {
    /** Eye position and unit forward / up of the head. */
    record Basis(double ex, double ey, double ez, Vector3f forward, Vector3f up) {
        Vector3f right() {
            return new Vector3f(forward).cross(up).normalize();
        }
    }

    /** Lens position and its forward / up directions as (right, up, forward) components. */
    record Local(double px, double py, double pz, Vector3f forward, Vector3f up) {}

    /** World position and directions. */
    record World(double x, double y, double z, Vector3f forward, Vector3f up) {}

    private LampFrame() {}

    private static Vector3f toLocal(Vector3f right, Vector3f up, Vector3f forward, Vector3f v) {
        return new Vector3f(v.dot(right), v.dot(up), v.dot(forward));
    }

    private static Vector3f toWorld(Vector3f right, Vector3f up, Vector3f forward, Vector3f c) {
        return new Vector3f(right).mul(c.x).fma(c.y, up).fma(c.z, forward);
    }

    static Local encode(Basis b, double x, double y, double z, Vector3f lensForward, Vector3f lensUp) {
        Vector3f r = b.right();
        double dx = x - b.ex, dy = y - b.ey, dz = z - b.ez;
        return new Local(dx * r.x + dy * r.y + dz * r.z,
                dx * b.up.x + dy * b.up.y + dz * b.up.z, dx * b.forward.x + dy * b.forward.y + dz * b.forward.z,
                toLocal(r, b.up, b.forward, lensForward), toLocal(r, b.up, b.forward, lensUp));
    }

    static World decode(Basis b, Local l) {
        Vector3f r = b.right();
        double x = b.ex + r.x * l.px + b.up.x * l.py + b.forward.x * l.pz;
        double y = b.ey + r.y * l.px + b.up.y * l.py + b.forward.y * l.pz;
        double z = b.ez + r.z * l.px + b.up.z * l.py + b.forward.z * l.pz;
        return new World(x, y, z, toWorld(r, b.up, b.forward, l.forward), toWorld(r, b.up, b.forward, l.up));
    }
}
