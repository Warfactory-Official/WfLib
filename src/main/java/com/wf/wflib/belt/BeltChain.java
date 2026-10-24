package com.wf.wflib.belt;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.List;

/**
 * Ammo belt as a hinge chain, caller's frame (m): links hinge about the cartridge axis only, {@code <= BEND} per joint
 * (metal links), the axis twisting {@code <= TWIST} per link. Guides: links pinned on a Catmull-Rom path feed ->
 * guides (no collisions => guides route the belt around the gun); the chain proper starts at the last pinned link.
 * Rest shape: free => bent toward gravity at {@code BEND} per joint; anchored (box mouth) => Hermite -> mouth, tangents
 * {@code out} / {@code into}, handle fitted so its length = whole pitches (~{@link #HANDLE} chord). First joint
 * {@code <= TRAY} (feed tray) when unguided. Verlet in the caller's inertial frame ({@code frame}: links carried by
 * frame^-1 * last frame => world-space lag while the gun moves), pulled toward the rest shape ({@link #RESTORE}) =>
 * no coils a free verlet tail keeps under the bend limit; then follow-the-leader (anchored: backward pass from the
 * mouth first, last link along {@code into}).
 * Link {@code k} frame: origin on its cartridge axis, X = belt direction away from the feed, Z = cartridge axis, Y = Z x
 * X. Renderer-agnostic (MineAssault viewmodel, CombinedAssault mounts).
 */
public final class BeltChain {
    public static final float BEND = (float) Math.toRadians(35);
    public static final float TWIST = (float) Math.toRadians(4);
    public static final float TRAY = (float) Math.toRadians(5);
    /** Velocity kept per 1/60 s. */
    static final float DAMPING = 0.97F;
    static final float MAX_DT = 1 / 20F;
    /** 1/s: verlet positions pulled toward the rest shape; free hang / box (tensioned, little sway). */
    static final float RESTORE = 3, RESTORE_BOX = 14;
    /** DFTL velocity correction share (1 = all: jitters; less => tail whip). */
    static final float FTL_DAMPING = 0.6F;
    /** m: frame moved further in one update => restart (teleport / respawn), no carry. */
    static final float TELEPORT = 1;
    private static final int GUIDE_STEPS = 12;
    /** Anchored rest curve tangent length / chord: link count from this, then fitted in [{@code HANDLE_MIN}, x2]. */
    static final float HANDLE = 1.0F, HANDLE_MIN = 0.2F;
    private static final int FIT_STEPS = 14;
    private static final int CURVE_STEPS = 48;
    /** |g in hinge plane| / |g|: hang bend 0 at {@code HANG_MIN}, full {@code BEND} at {@code HANG_FULL}. */
    static final float HANG_MIN = 0.15F, HANG_FULL = 0.5F;
    /** sin(joint dir, g) under this with dir against g => bend side held from earlier frames (no flip at antiparallel). */
    static final float SIDE_LOCK = 0.87F;
    /** Silent 60 Hz steps on start: settled sag, no first-frames wobble. */
    static final int SETTLE = 30;
    /** Twist per pass = gain * ((back x axis).d / pitch)^2 * error: straight link => 0 (twist undetermined, else +-TWIST flip-flop). */
    static final float TWIST_GAIN = 0.44F;
    /** Anchored backward + forward passes per step. */
    static final int PASSES = 4;

    private final float pitch;
    Vector3f[] x = new Vector3f[0], prev = new Vector3f[0], dir = new Vector3f[0], axis = new Vector3f[0],
            rest = new Vector3f[0], moved = new Vector3f[0];
    /** Per joint: last unclamped bend (follow / reach), for {@link #unwrap}. */
    private float[] bent = new float[0], reached = new float[0];
    private int links;
    /** Last pinned link = chain proper's feed. */
    private int base;
    private boolean started;
    private final Matrix4f last = new Matrix4f(), carry = new Matrix4f();
    private Vector3f[] path = new Vector3f[0], pathAxis = new Vector3f[0];
    private float[] pathArc = new float[0], station = new float[0];
    private int pathEnd;
    private final Vector3f feed1 = new Vector3f(), out1 = new Vector3f(), axis1 = new Vector3f(), p0 = new Vector3f(),
            p1 = new Vector3f(), m0 = new Vector3f(), m1 = new Vector3f();
    /** Free hang bend sign, held through the antiparallel ambiguity. */
    private float side = 1;
    @Nullable
    private BeltProbe probe;
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

    /** Links pinned on the guide path, excluding the chain proper's feed. */
    public int pinned() {
        return base;
    }

    public void reset() {
        started = false;
        if (probe != null) probe.restart();
    }

    /** Null detaches. */
    public void probe(@Nullable BeltProbe probe) {
        this.probe = probe;
        if (probe != null) probe.restart();
    }

    /**
     * {@code feed}: first link's cartridge centre; {@code out}: belt direction leaving the feed (unit); {@code cartridge}:
     * cartridge axis there (unit, normal to {@code out}); {@code guides}: path points after the feed, in order;
     * {@code end}: far anchor (box mouth) or null (free hang); {@code into}: belt direction entering it (unit);
     * {@code count}: links available (free: drawn; anchored: cap, fewer than the curve needs => free); {@code gravity}:
     * acceleration, m/s^2, this frame; {@code frame}: caller -> inertial (world-oriented, m; rigid); {@code dt}: s since
     * the last update.
     */
    public void update(Vector3fc feed, Vector3fc out, Vector3fc cartridge, List<? extends Vector3fc> guides,
                       @Nullable Vector3fc end, @Nullable Vector3fc into, int count, Vector3fc gravity, Matrix4fc frame,
                       float dt) {
        BeltProbe.Input in = probe == null ? null
                : BeltProbe.Input.copy(feed, out, cartridge, guides, end, into, count, gravity, frame, dt);
        if (started) {
            frame.invertAffine(carry).mul(last);
            if (carry.getTranslation(t).length() > TELEPORT) reset();
            else for (int k = 0; k < links; k++) {
                carry.transformPosition(x[k]);
                carry.transformPosition(prev[k]);
            }
        }
        last.set(frame);
        int on = guide(feed, out, cartridge, guides, count);
        base = Math.max(on - 1, 0);
        int need = end == null || on == count ? Integer.MAX_VALUE : base + curve(feed1, out1, end, into);
        BeltProbe.Mode mode = end == null ? BeltProbe.Mode.FREE
                : need > count ? BeltProbe.Mode.ANCHOR_DROPPED : BeltProbe.Mode.ANCHORED;
        if (need > count) end = null;
        resize(Math.min(count, need), feed, out);
        if (links == 0) {
            if (in != null) probe.record(this, in, BeltProbe.Mode.EMPTY, -1, false);
            return;
        }
        pin();
        if (end == null) restShape(gravity);
        else anchoredShape();
        if (!started) {
            started = true;
            Arrays.fill(bent, 0);
            Arrays.fill(reached, 0);
            for (int k = base + 1; k < links; k++) {
                x[k].set(rest[k]);
                prev[k].set(x[k]);
            }
            for (int i = 0; i < SETTLE; i++) step(end, into, gravity, 1 / 60F);
            for (int k = 0; k < links; k++) prev[k].set(x[k]);
        }
        step(end, into, gravity, Math.min(dt, MAX_DT));
        if (in != null) probe.record(this, in, mode, need == Integer.MAX_VALUE ? -1 : need, dt > MAX_DT);
    }

    /**
     * Path feed -> guides (Catmull-Rom, feed tangent {@code out}) sampled into {@link #path}, cartridge axis parallel
     * transported; chain proper's feed ({@link #feed1} / {@link #out1} / {@link #axis1}) = last whole pitch on it. ->
     * links on the path (>= 1).
     */
    private int guide(Vector3fc feed, Vector3fc out, Vector3fc cartridge, List<? extends Vector3fc> guides, int count) {
        int n = guides.size();
        feed1.set(feed);
        out1.set(out);
        axis1.set(cartridge);
        if (n == 0 || count == 0) return Math.min(count, 1);
        int samples = n * GUIDE_STEPS + 1;
        if (path.length < samples) {
            path = grow(path, samples);
            pathAxis = grow(pathAxis, samples);
            pathArc = new float[path.length];
        }
        path[0].set(feed);
        pathAxis[0].set(cartridge);
        pathArc[0] = 0;
        for (int s = 0; s < n; s++) {
            p0.set(s == 0 ? feed : guides.get(s - 1));
            p1.set(guides.get(s));
            float chord = p0.distance(p1);
            if (s == 0) m0.set(out).mul(chord);
            else m0.set(p1).sub(s == 1 ? feed : guides.get(s - 2)).mul(0.5F);
            if (s == n - 1) m1.set(p1).sub(p0);
            else m1.set(guides.get(s + 1)).sub(p0).mul(0.5F);
            for (int i = 1; i <= GUIDE_STEPS; i++) {
                float u = i / (float) GUIDE_STEPS, u2 = u * u, u3 = u2 * u;
                int at = s * GUIDE_STEPS + i;
                path[at].set(p0).mul(2 * u3 - 3 * u2 + 1).fma(u3 - 2 * u2 + u, m0)
                        .fma(-2 * u3 + 3 * u2, p1).fma(u3 - u2, m1);
                pathArc[at] = pathArc[at - 1] + path[at].distance(path[at - 1]);
                d.set(path[at]).sub(path[at - 1]).normalize();
                pathAxis[at].set(pathAxis[at - 1]).fma(-pathAxis[at - 1].dot(d), d).normalize();
            }
        }
        pathEnd = samples - 1;
        // stations: links a chord of one pitch apart along the path (arc steps => short chords on curves)
        if (station.length < path.length) station = new float[path.length];
        int on = 1, i = 1;
        station[0] = 0;
        p0.set(feed);
        while (on < count) {
            while (i <= pathEnd && path[i].distance(p0) < pitch) i++;
            if (i > pathEnd) break;
            // |a + f (b - a) - p0| = pitch on segment i-1 -> i, a inside the sphere
            m0.set(path[i - 1]).sub(p0);
            m1.set(path[i]).sub(path[i - 1]);
            float qa = m1.lengthSquared(), qb = 2 * m0.dot(m1), qc = m0.lengthSquared() - pitch * pitch;
            float f = Math.clamp((-qb + (float) Math.sqrt(Math.max(qb * qb - 4 * qa * qc, 0))) / (2 * qa), 0, 1);
            station[on++] = pathArc[i - 1] + f * (pathArc[i] - pathArc[i - 1]);
            p0.set(path[i - 1]).lerp(path[i], f);
        }
        onPath(station[on - 1], feed1, out1, axis1);
        if (on > 1) {
            onPath(station[on - 2], p1, t, r);
            out1.set(feed1).sub(p1).normalize();
            axis1.fma(-axis1.dot(out1), out1).normalize();
        }
        return on;
    }

    /** Path point at arc {@code s}: position, unit tangent, cartridge axis normal to it. */
    private void onPath(float s, Vector3f pos, Vector3f tangent, Vector3f ax) {
        int i = 1;
        while (i < pathEnd && pathArc[i] < s) i++;
        float span = pathArc[i] - pathArc[i - 1];
        float f = span > 0 ? Math.clamp((s - pathArc[i - 1]) / span, 0, 1) : 0;
        pos.set(path[i - 1]).lerp(path[i], f);
        tangent.set(path[i]).sub(path[i - 1]).normalize();
        ax.set(pathAxis[i - 1]).lerp(pathAxis[i], f);
        ax.fma(-ax.dot(tangent), tangent).normalize();
    }

    /** Links {@code [0, base]} onto the guide path stations (no path => link 0 = feed); X = chord from the last. */
    private void pin() {
        for (int k = 0; k <= base; k++) {
            if (k == base) {
                x[k].set(feed1);
                dir[k].set(out1);
                axis[k].set(axis1);
            } else {
                onPath(station[k], x[k], dir[k], axis[k]);
                if (k > 0) {
                    dir[k].set(x[k]).sub(x[k - 1]).normalize();
                    axis[k].fma(-axis[k].dot(dir[k]), dir[k]).normalize();
                }
            }
            prev[k].set(x[k]);
            rest[k].set(x[k]);
        }
    }

    /**
     * Free hang from the feed: each joint bent toward gravity (projected normal to the cartridge), {@code <= BEND}
     * scaled down as gravity turns onto the cartridge axis.
     */
    private void restShape(Vector3fc gravity) {
        Vector3fc cartridge = axis1;
        g.set(gravity).fma(-gravity.dot(cartridge), cartridge);
        float full = gravity.length();
        float u = full > 0 ? Math.clamp((g.length() / full - HANG_MIN) / (HANG_FULL - HANG_MIN), 0, 1) : 0;
        float limit = BEND * u * u * (3 - 2 * u);
        if (limit > 0) g.normalize();
        r.set(out1);
        for (int k = base + 1; k < links; k++) {
            if (limit > 0) {
                float sin = c.set(r).cross(g).dot(cartridge), cos = r.dot(g);
                float bend;
                if (Math.abs(sin) >= SIDE_LOCK) {
                    side = Math.signum(sin);
                    bend = (float) Math.atan2(sin, cos);
                } else bend = cos < 0 ? side * limit : (float) Math.atan2(sin, cos);
                r.rotateAxis(Math.clamp(bend, -limit, limit), cartridge.x(), cartridge.y(), cartridge.z()).normalize();
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
        for (int k = base + 1; k < links; k++) {
            float s = len * (k - base) / (links - 1 - base);
            while (i < CURVE_STEPS && arc[i] < s) i++;
            float span = arc[i] - arc[i - 1];
            rest[k].set(curve[i - 1]).lerp(curve[i], span > 0 ? Math.clamp((s - arc[i - 1]) / span, 0, 1) : 0);
        }
    }

    private void step(@Nullable Vector3fc end, @Nullable Vector3fc into, Vector3fc gravity, float dt) {
        float keep = (float) Math.pow(DAMPING, dt * 60);
        float pull = 1 - (float) Math.exp(-(end == null ? RESTORE : RESTORE_BOX) * dt);
        // hinges cannot fold along the cartridge axis: that component would only drive twist
        g.set(gravity).fma(-gravity.dot(axis1), axis1);
        for (int k = base + 1; k < links; k++) {
            t.set(x[k]).sub(prev[k]).mul(keep);
            prev[k].set(x[k]);
            x[k].add(t).fma(dt * dt, g);
            x[k].lerp(rest[k], pull);
        }
        for (int k = base + 1; k < links; k++) moved[k].set(x[k]);
        for (int pass = end == null ? PASSES - 1 : 0; pass < PASSES; pass++) {
            if (end != null && links > base + 1) reach(end, into);
            follow();
        }
        // box: tensioned, passes fight when over-constrained => no velocity. Free: DFTL (Mueller 2012),
        // v_k -= S * correction_{k+1}; plain verlet => tail whip, none => gravity piles into v
        if (end != null) for (int k = base + 1; k < links; k++) prev[k].set(x[k]);
        else for (int k = base + 1; k < links - 1; k++) prev[k].fma(FTL_DAMPING, t.set(x[k + 1]).sub(moved[k + 1]));
    }

    /** Backward pass from the anchor: last link along {@code into}, bend clamps about the links' axes. */
    private void reach(Vector3fc end, Vector3fc into) {
        x[links - 1].set(end);
        t.set(into);
        for (int k = links - 2; k > base; k--) {
            Vector3f a = axis[k];
            d.set(x[k + 1]).sub(x[k]);
            d.fma(-d.dot(a), a);
            if (d.lengthSquared() < 1e-12F) d.set(t);
            d.normalize();
            float bend = reached[k] = unwrap((float) Math.atan2(c.set(t).cross(d).dot(a), t.dot(d)), reached[k]);
            t.rotateAxis(Math.clamp(bend, -BEND, BEND), a.x, a.y, a.z).normalize();
            x[k].set(x[k + 1]).fma(-pitch, t);
        }
    }

    /** Forward pass from the chain proper's feed: pitch, twist / bend clamps. */
    private void follow() {
        dir[base].set(out1);
        axis[base].set(axis1);
        for (int k = base + 1; k < links; k++) {
            Vector3f a = axis[k].set(axis[k - 1]);
            Vector3f back = dir[k - 1];
            d.set(x[k]).sub(x[k - 1]);
            // twist about back: a.d -> 0 at rate (back x a).d (sign = bend side)
            float along = d.dot(a), side = c.set(back).cross(a).dot(d);
            if (side != 0) {
                float twist = Math.clamp(TWIST_GAIN * side * side / (pitch * pitch) * (float) Math.atan(-along / side),
                        -TWIST, TWIST);
                a.rotateAxis(twist, back.x, back.y, back.z).normalize();
            }
            d.fma(-d.dot(a), a);
            if (d.lengthSquared() < 1e-12F) d.set(back);
            d.normalize();
            // bend about the cartridge axis, <= BEND
            float bend = bent[k] = unwrap((float) Math.atan2(c.set(back).cross(d).dot(a), back.dot(d)), bent[k]);
            float limit = k == 1 ? TRAY : BEND;
            bend = Math.clamp(bend, -limit, limit);
            dir[k].set(back).rotateAxis(bend, a.x, a.y, a.z).normalize();
            x[k].set(x[k - 1]).fma(pitch, dir[k]);
        }
    }

    /** Folded back (|angle| > pi/2) across +-pi from {@code last} => {@code last}'s side kept (no snap through). */
    private static float unwrap(float angle, float last) {
        return Math.abs(angle) > Math.PI / 2 && angle * last < 0 ? Math.copySign(Math.abs(angle), last) : angle;
    }

    private void resize(int count, Vector3fc feed, Vector3fc out) {
        if (count > x.length) {
            x = grow(x, count);
            prev = grow(prev, count);
            dir = grow(dir, count);
            axis = grow(axis, count);
            rest = grow(rest, count);
            moved = grow(moved, count);
            bent = Arrays.copyOf(bent, x.length);
            reached = Arrays.copyOf(reached, x.length);
        }
        for (int k = links; k < count; k++) {
            // appended links extend the last one straight
            if (k == 0) x[0].set(feed);
            else x[k].set(dir[k - 1].lengthSquared() > 0 ? dir[k - 1] : out).mul(pitch).add(x[k - 1]);
            prev[k].set(x[k]);
            dir[k].set(k == 0 ? out : dir[k - 1]);
            axis[k].set(k == 0 ? axis[0] : axis[k - 1]);
            bent[k] = reached[k] = 0;
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

    /** Shape link {@code k} is pulled toward. */
    public Vector3fc restPosition(int k) {
        return rest[k];
    }

    /**
     * Link {@code k} frame {@code shift} pitches back from the feed (advance in flight: decays to 0 per fired round);
     * {@code k >= -1}: -1 = the link being stripped into the feed. Slots past either end extend along the end links.
     */
    public Matrix4f link(int k, float shift, Matrix4f dest) {
        float at = k + shift;
        int j = (int) Math.floor(at);
        float f = at - j;
        slot(j, a0);
        slot(j + 1, a1);
        a0.lerp(a1, f);
        int i0 = Math.clamp(j, 0, links - 1), i1 = Math.clamp(j + 1, 0, links - 1);
        Vector3f xa = d.set(dir[i0]).lerp(dir[i1], f).normalize();
        Vector3f za = t.set(axis[i0]).lerp(axis[i1], f);
        za.fma(-za.dot(xa), xa).normalize();
        c.set(za).cross(xa);
        return dest.set(xa.x, xa.y, xa.z, 0, c.x, c.y, c.z, 0, za.x, za.y, za.z, 0, a0.x, a0.y, a0.z, 1);
    }

    private void slot(int j, Vector3f dest) {
        if (j < 0) dest.set(dir[0]).mul(j * pitch).add(x[0]);
        else if (j >= links) dest.set(dir[links - 1]).mul((j - links + 1) * pitch).add(x[links - 1]);
        else dest.set(x[j]);
    }
}
