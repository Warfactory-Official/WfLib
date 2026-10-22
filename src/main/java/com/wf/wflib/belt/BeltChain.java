package com.wf.wflib.belt;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Ammo belt as a hinge chain, caller's frame (m): links hinge about the cartridge axis only, {@code <= BEND} per joint
 * (metal links), the axis twisting {@code <= TWIST} per link. Rest shape: free => bent toward gravity at {@code BEND}
 * per joint from the feed; anchored (box mouth) => Hermite feed -> mouth, tangents {@code out} / {@code into}, handle
 * fitted so its length = whole pitches (~{@link #HANDLE} chord). First joint {@code <= TRAY} (feed tray). Verlet inertia pulled toward it ({@link #RESTORE}) => sway /
 * recoil lag without the coils a free verlet tail keeps under the bend limit; then follow-the-leader from the feed
 * (anchored: backward pass from the mouth first, last link along {@code into}). Not a physics sim: no collisions.
 * Link {@code k} frame: origin on its cartridge axis, X = belt direction away from the feed, Z = cartridge axis, Y = Z x
 * X. Renderer-agnostic (MineAssault viewmodel, CombinedAssault mounts).
 */
public final class BeltChain {
    public static final float BEND = (float) Math.toRadians(35);
    public static final float TWIST = (float) Math.toRadians(4);
    public static final float TRAY = (float) Math.toRadians(5);
    /** Velocity kept per 1/60 s. */
    static final float DAMPING = 0.88F;
    static final float MAX_DT = 1 / 20F;
    /** 1/s: verlet positions pulled toward the rest shape. */
    static final float RESTORE = 14;
    /** Anchored rest curve tangent length / chord: link count from this, then fitted in [{@code HANDLE_MIN}, x2]. */
    static final float HANDLE = 1.0F, HANDLE_MIN = 0.2F;
    private static final int FIT_STEPS = 14;
    private static final int CURVE_STEPS = 48;
    /** Anchored backward + forward passes per step. */
    static final int PASSES = 4;

    private final float pitch;
    private Vector3f[] x = new Vector3f[0], prev = new Vector3f[0], dir = new Vector3f[0], axis = new Vector3f[0],
            rest = new Vector3f[0];
    private int links;
    private boolean started;
    private final Vector3f d = new Vector3f(), t = new Vector3f(), c = new Vector3f(), g = new Vector3f(),
            r = new Vector3f(), a0 = new Vector3f(), a1 = new Vector3f();
    private final Vector3f[] curve = new Vector3f[CURVE_STEPS + 1];
    private final float[] arc = new float[CURVE_STEPS + 1];

    {
        for (int i = 0; i <= CURVE_STEPS; i++) curve[i] = new Vector3f();
    }

    public BeltChain(float pitch) {
        this.pitch = pitch;
    }

    public float pitch() {
        return pitch;
    }

    public int links() {
        return links;
    }

    public void reset() {
        started = false;
    }

    /**
     * {@code feed}: first link's cartridge centre; {@code out}: belt direction leaving the feed (unit); {@code cartridge}:
     * cartridge axis there (unit, normal to {@code out}); {@code end}: far anchor (box mouth) or null (free hang);
     * {@code into}: belt direction entering it (unit); {@code count}: links available (free: drawn; anchored: cap,
     * fewer than the curve needs => free); {@code gravity}: acceleration, m/s^2, this frame; {@code dt}: s since the
     * last update.
     */
    public void update(Vector3fc feed, Vector3fc out, Vector3fc cartridge, @Nullable Vector3fc end,
                       @Nullable Vector3fc into, int count, Vector3fc gravity, float dt) {
        int need = end == null ? Integer.MAX_VALUE : curve(feed, out, end, into);
        if (need > count) end = null;
        resize(Math.min(count, need), feed, out);
        if (links == 0) return;
        if (end == null) restShape(feed, out, cartridge, gravity);
        else anchoredShape();
        if (!started) {
            started = true;
            for (int k = 0; k < links; k++) {
                x[k].set(rest[k]);
                prev[k].set(x[k]);
            }
        }
        step(feed, out, cartridge, end, into, gravity, Math.min(dt, MAX_DT));
    }

    /** Free hang from the feed: each joint bent toward gravity (projected normal to the cartridge), {@code <= BEND}. */
    private void restShape(Vector3fc feed, Vector3fc out, Vector3fc cartridge, Vector3fc gravity) {
        g.set(gravity).fma(-gravity.dot(cartridge), cartridge);
        boolean hangs = g.lengthSquared() > 1e-6F;
        if (hangs) g.normalize();
        r.set(out);
        rest[0].set(feed);
        for (int k = 1; k < links; k++) {
            if (hangs) {
                float bend = (float) Math.atan2(c.set(r).cross(g).dot(cartridge), r.dot(g));
                r.rotateAxis(Math.clamp(bend, -BEND, BEND), cartridge.x(), cartridge.y(), cartridge.z()).normalize();
            }
            rest[k].set(rest[k - 1]).fma(pitch, r);
        }
    }

    /** Hermite feed -> end into {@link #curve} / {@link #arc}, length fitted to whole pitches; -> links. */
    private int curve(Vector3fc feed, Vector3fc out, Vector3fc end, Vector3fc into) {
        float chord = feed.distance(end);
        int n = Math.max(2, Math.round(hermite(feed, out, end, into, chord * HANDLE) / pitch) + 1);
        float lo = chord * HANDLE_MIN, hi = chord * HANDLE * 2, want = (n - 1) * pitch;
        for (int i = 0; i < FIT_STEPS; i++) {
            float h = (lo + hi) / 2;
            if (hermite(feed, out, end, into, h) < want) lo = h;
            else hi = h;
        }
        hermite(feed, out, end, into, (lo + hi) / 2);
        return n;
    }

    private float hermite(Vector3fc feed, Vector3fc out, Vector3fc end, Vector3fc into, float h) {
        a0.set(out).mul(h);
        a1.set(into).mul(h);
        for (int i = 0; i <= CURVE_STEPS; i++) {
            float u = i / (float) CURVE_STEPS, u2 = u * u, u3 = u2 * u;
            curve[i].set(feed).mul(2 * u3 - 3 * u2 + 1).fma(u3 - 2 * u2 + u, a0)
                    .fma(-2 * u3 + 3 * u2, end).fma(u3 - u2, a1);
            arc[i] = i == 0 ? 0 : arc[i - 1] + curve[i].distance(curve[i - 1]);
        }
        return arc[CURVE_STEPS];
    }

    /** {@link #rest} = {@link #curve} at equal arc steps, link 0 on the feed, last on the end. */
    private void anchoredShape() {
        float len = arc[CURVE_STEPS];
        int i = 1;
        for (int k = 0; k < links; k++) {
            float s = links == 1 ? 0 : len * k / (links - 1);
            while (i < CURVE_STEPS && arc[i] < s) i++;
            float span = arc[i] - arc[i - 1];
            rest[k].set(curve[i - 1]).lerp(curve[i], span > 0 ? Math.clamp((s - arc[i - 1]) / span, 0, 1) : 0);
        }
    }

    private void step(Vector3fc feed, Vector3fc out, Vector3fc cartridge, @Nullable Vector3fc end,
                      @Nullable Vector3fc into, Vector3fc gravity, float dt) {
        float keep = (float) Math.pow(DAMPING, dt * 60);
        float pull = 1 - (float) Math.exp(-RESTORE * dt);
        for (int k = 1; k < links; k++) {
            t.set(x[k]).sub(prev[k]).mul(keep);
            prev[k].set(x[k]);
            x[k].add(t).fma(dt * dt, gravity);
            x[k].lerp(rest[k], pull);
        }
        x[0].set(feed);
        prev[0].set(feed);
        for (int pass = end == null ? PASSES - 1 : 0; pass < PASSES; pass++) {
            if (end != null && links > 1) reach(end, into);
            follow(out, cartridge);
        }
    }

    /** Backward pass from the anchor: last link along {@code into}, bend clamps about the links' axes. */
    private void reach(Vector3fc end, Vector3fc into) {
        x[links - 1].set(end);
        t.set(into);
        for (int k = links - 2; k > 0; k--) {
            Vector3f a = axis[k];
            d.set(x[k + 1]).sub(x[k]);
            d.fma(-d.dot(a), a);
            if (d.lengthSquared() < 1e-12F) d.set(t);
            d.normalize();
            float bend = (float) Math.atan2(c.set(t).cross(d).dot(a), t.dot(d));
            t.rotateAxis(Math.clamp(bend, -BEND, BEND), a.x, a.y, a.z).normalize();
            x[k].set(x[k + 1]).fma(-pitch, t);
        }
    }

    /** Forward pass from link 0: pitch, twist / bend clamps. */
    private void follow(Vector3fc out, Vector3fc cartridge) {
        dir[0].set(out);
        axis[0].set(cartridge);
        for (int k = 1; k < links; k++) {
            Vector3f a = axis[k].set(axis[k - 1]);
            Vector3f back = dir[k - 1];
            d.set(x[k]).sub(x[k - 1]);
            // twist: axis turns toward the plane holding d, <= TWIST
            float along = d.dot(a);
            float across = c.set(a).mul(along).negate().add(d).length();
            float twist = Math.clamp((float) Math.atan2(along, across), -TWIST, TWIST);
            if (twist != 0) a.rotateAxis(-twist, back.x, back.y, back.z).normalize();
            d.fma(-d.dot(a), a);
            if (d.lengthSquared() < 1e-12F) d.set(back);
            d.normalize();
            // bend about the cartridge axis, <= BEND
            float bend = (float) Math.atan2(c.set(back).cross(d).dot(a), back.dot(d));
            float limit = k == 1 ? TRAY : BEND;
            bend = Math.clamp(bend, -limit, limit);
            dir[k].set(back).rotateAxis(bend, a.x, a.y, a.z).normalize();
            x[k].set(x[k - 1]).fma(pitch, dir[k]);
        }
    }

    private void resize(int count, Vector3fc feed, Vector3fc out) {
        if (count > x.length) {
            x = grow(x, count);
            prev = grow(prev, count);
            dir = grow(dir, count);
            axis = grow(axis, count);
            rest = grow(rest, count);
        }
        for (int k = links; k < count; k++) {
            // appended links extend the last one straight
            if (k == 0) x[0].set(feed);
            else x[k].set(dir[k - 1].lengthSquared() > 0 ? dir[k - 1] : out).mul(pitch).add(x[k - 1]);
            prev[k].set(x[k]);
            dir[k].set(k == 0 ? out : dir[k - 1]);
            axis[k].set(k == 0 ? axis[0] : axis[k - 1]);
        }
        links = count;
    }

    private static Vector3f[] grow(Vector3f[] a, int n) {
        Vector3f[] out = new Vector3f[Math.max(n, a.length * 2)];
        System.arraycopy(a, 0, out, 0, a.length);
        for (int i = a.length; i < out.length; i++) out[i] = new Vector3f();
        return out;
    }

    /** Link {@code k} frame (see class doc). */
    public Matrix4f link(int k, Matrix4f dest) {
        Vector3f xa = dir[k], za = axis[k];
        c.set(za).cross(xa);
        return dest.set(xa.x, xa.y, xa.z, 0, c.x, c.y, c.z, 0, za.x, za.y, za.z, 0, x[k].x, x[k].y, x[k].z, 1);
    }

    public Vector3fc position(int k) {
        return x[k];
    }
}
