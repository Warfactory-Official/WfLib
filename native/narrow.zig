//! World narrowphase of `StepBody.worldContacts`: port of `ConvexContacts` + `Polytope` (physics.rigid).
//! Same operation order as the Java (no FMA: strict float mode), Java Math.min/max zero rules => contacts bitwise
//! equal up to the sign of a zero. Layouts: `NarrowNative`.

const std = @import("std");

const V = @Vector(8, f32);
const W = 8;
const MAX = std.math.floatMax(f32);
const EDGE_PREFERENCE: f32 = 0.01;
const PARALLEL: f32 = 1.0e-4;
const NEAR = 4;
const NAN_BITS: u32 = 0x7fc00000;

fn javaMax(a: f32, b: f32) f32 {
    if (a != a) return a;
    if (a == 0 and b == 0 and @as(u32, @bitCast(a)) == 0x80000000) return b;
    return if (a >= b) a else b;
}

fn javaMin(a: f32, b: f32) f32 {
    if (a != a) return a;
    if (a == 0 and b == 0 and @as(u32, @bitCast(b)) == 0x80000000) return b;
    return if (a <= b) a else b;
}

// ---------------------------------------------------------------------------------------------- scratch memory

/// Bump arena, reset per call; grows by whole blocks (old blocks kept until destroy).
const Arena = struct {
    blocks: [64][]align(64) u8 = undefined,
    count: usize = 0,
    used: usize = 0,

    fn reset(self: *Arena) void {
        if (self.count > 1) {
            // Fold into one block big enough for the last call.
            var total: usize = 0;
            for (self.blocks[0..self.count]) |b| total += b.len;
            for (self.blocks[0..self.count]) |b| std.heap.page_allocator.free(b);
            self.count = 0;
            self.addBlock(total);
        }
        self.used = 0;
    }

    fn addBlock(self: *Arena, size: usize) void {
        const block = std.heap.page_allocator.alignedAlloc(u8, .@"64", @max(size, 1 << 20)) catch @panic("narrow: out of memory");
        self.blocks[self.count] = block;
        self.count += 1;
        self.used = 0;
    }

    fn alloc(self: *Arena, comptime T: type, n: usize) []T {
        const bytes = (n * @sizeOf(T) + 63) & ~@as(usize, 63);
        if (self.count == 0 or self.used + bytes > self.blocks[self.count - 1].len) self.addBlock(bytes);
        const block = self.blocks[self.count - 1];
        const out: [*]T = @ptrCast(@alignCast(block.ptr + self.used));
        self.used += bytes;
        return out[0..n];
    }

    pub fn deinit(self: *Arena) void {
        for (self.blocks[0..self.count]) |b| std.heap.page_allocator.free(b);
        self.count = 0;
    }
};

// ---------------------------------------------------------------------------------------------- polytope

const BOX_START = [_]i32{ 0, 4, 8, 12, 16, 20, 24 };
const BOX_LOOPS = [_]i32{ 0, 4, 6, 2, 1, 3, 7, 5, 0, 1, 5, 4, 2, 6, 7, 3, 0, 2, 3, 1, 4, 5, 7, 6 };
const BOX_NORMALS = [_]f32{ -1, 0, 0, 1, 0, 0, 0, -1, 0, 0, 1, 0, 0, 0, -1, 0, 0, 1 };
const UNIT_AXES = [_]f32{ 1, 0, 0, 0, 1, 0, 0, 0, 1 };
/// Polytope.BOX_EDGE_FACES (ConvexHull.edgeFaces on the unit cube), passed in by `init`.
var box_edge_faces: [36]i32 = undefined;
var box_planes: [24]f32 = undefined;

const PRISM_FACES = [_][]const i32{ &.{ 0, 1, 2 }, &.{ 3, 5, 4 }, &.{ 0, 3, 4, 1 }, &.{ 1, 4, 5, 2 }, &.{ 2, 5, 3, 0 } };
/// Polytope.PRISM_EDGES.
const PRISM_EDGES = blk: {
    var out: [27]i32 = undefined;
    var n = 0;
    for (PRISM_FACES, 0..) |face, f| {
        for (0..face.len) |k| {
            const a = face[k];
            const b = face[(k + 1) % face.len];
            for (f + 1..PRISM_FACES.len) |g| {
                const other = PRISM_FACES[g];
                for (0..other.len) |m| {
                    const c = other[m];
                    const d = other[(m + 1) % other.len];
                    if (c == a and d == b or c == b and d == a) {
                        out[n * 3] = f;
                        out[n * 3 + 1] = k;
                        out[n * 3 + 2] = g;
                        n += 1;
                    }
                }
            }
        }
    }
    break :blk out;
};

const Poly = struct {
    pts: []f32 = &.{},
    n: usize = 0,
    /// SoA copies padded to a multiple of W with vertex 0.
    sx: []f32 = &.{},
    sy: []f32 = &.{},
    sz: []f32 = &.{},
    planes: []f32 = &.{},
    face_count: usize = 0,
    face_start: []const i32 = &.{},
    loops: []const i32 = &.{},
    axes: []f32 = &.{},
    axis_count: usize = 0,
    edges: []f32 = &.{},
    edge_count: usize = 0,
    edge_faces: []const i32 = &.{},
    edge_face_count: usize = 0,
    min_x: f32 = 0,
    min_y: f32 = 0,
    min_z: f32 = 0,
    max_x: f32 = 0,
    max_y: f32 = 0,
    max_z: f32 = 0,
    tag: i32 = 0,
    aligned: bool = false,
    faces_projected: bool = false,
    arcs_posed: bool = false,
    face_ranges: []f32 = &.{},
    arcs: []f32 = &.{},
    arc_stride: usize = 0,
    es_x: []f32 = &.{},
    es_y: []f32 = &.{},
    es_z: []f32 = &.{},
    aligned_minkowski: []bool = &.{},
    cross_x: []f32 = &.{},
    cross_y: []f32 = &.{},
    cross_z: []f32 = &.{},
    cross_lo: []f32 = &.{},
    cross_hi: []f32 = &.{},
    cross_have: []bool = &.{},
    /// Prism whose loops / edges `prismEdges` has yet to build.
    pending: ?*PrismStore = null,

    fn soa(self: *Poly, arena: *Arena) void {
        const padded = (self.n + W - 1) / W * W;
        self.sx = arena.alloc(f32, padded);
        self.sy = arena.alloc(f32, padded);
        self.sz = arena.alloc(f32, padded);
        for (0..padded) |i| {
            const v = if (i < self.n) i else 0;
            self.sx[i] = self.pts[v * 3];
            self.sy[i] = self.pts[v * 3 + 1];
            self.sz[i] = self.pts[v * 3 + 2];
        }
    }

    /// min, max of vertices along (x, y, z).
    fn project(self: *const Poly, x: f32, y: f32, z: f32, out: *[2]f32) void {
        if (self.n <= W) {
            var min: f32 = MAX;
            var max: f32 = -MAX;
            for (0..self.n) |i| {
                const d = self.pts[i * 3] * x + self.pts[i * 3 + 1] * y + self.pts[i * 3 + 2] * z;
                if (d < min) min = d;
                if (d > max) max = d;
            }
            out[0] = min;
            out[1] = max;
            return;
        }
        const vx: V = @splat(x);
        const vy: V = @splat(y);
        const vz: V = @splat(z);
        var vmin: V = @splat(MAX);
        var vmax: V = @splat(-MAX);
        var i: usize = 0;
        while (i < self.sx.len) : (i += W) {
            const px: V = self.sx[i..][0..W].*;
            const py: V = self.sy[i..][0..W].*;
            const pz: V = self.sz[i..][0..W].*;
            const d = px * vx + py * vy + pz * vz;
            vmin = @select(f32, d < vmin, d, vmin);
            vmax = @select(f32, d > vmax, d, vmax);
        }
        var min: f32 = MAX;
        var max: f32 = -MAX;
        inline for (0..W) |l| {
            if (vmin[l] < min) min = vmin[l];
            if (vmax[l] > max) max = vmax[l];
        }
        out[0] = min;
        out[1] = max;
    }

    fn bound(self: *Poly) void {
        self.min_x = MAX;
        self.min_y = MAX;
        self.min_z = MAX;
        self.max_x = -MAX;
        self.max_y = -MAX;
        self.max_z = -MAX;
        for (0..self.n) |i| {
            const x = self.pts[i * 3];
            const y = self.pts[i * 3 + 1];
            const z = self.pts[i * 3 + 2];
            self.min_x = javaMin(self.min_x, x);
            self.max_x = javaMax(self.max_x, x);
            self.min_y = javaMin(self.min_y, y);
            self.max_y = javaMax(self.max_y, y);
            self.min_z = javaMin(self.min_z, z);
            self.max_z = javaMax(self.max_z, z);
        }
    }

    fn boundsMeet(self: *const Poly, other: *const Poly, margin: f32) bool {
        return self.min_x <= other.max_x + margin and other.min_x <= self.max_x + margin and
            self.min_y <= other.max_y + margin and other.min_y <= self.max_y + margin and
            self.min_z <= other.max_z + margin and other.min_z <= self.max_z + margin;
    }

    fn projectFaces(self: *Poly, arena: *Arena) void {
        if (self.faces_projected) return;
        self.faces_projected = true;
        if (self.n > W) self.soa(arena);
        self.face_ranges = arena.alloc(f32, self.axis_count * 2);
        var range: [2]f32 = undefined;
        for (0..self.axis_count) |k| {
            self.project(self.axes[k * 3], self.axes[k * 3 + 1], self.axes[k * 3 + 2], &range);
            self.face_ranges[k * 2] = range[0];
            self.face_ranges[k * 2 + 1] = range[1];
        }
    }

    /// arcs: 9 component rows of `arc_stride` (padded to W, zeros): -c, -d, d x c per edge_faces edge.
    fn poseArcs(self: *Poly, arena: *Arena) void {
        if (self.arcs_posed) return;
        self.arcs_posed = true;
        const stride = (self.edge_face_count + W - 1) / W * W;
        self.arc_stride = stride;
        self.arcs = arena.alloc(f32, stride * 9);
        @memset(self.arcs, 0);
        for (0..self.edge_face_count) |e| {
            const g0: usize = @intCast(self.edge_faces[e * 3] * 4);
            const g1: usize = @intCast(self.edge_faces[e * 3 + 1] * 4);
            const cx = -self.planes[g0];
            const cy = -self.planes[g0 + 1];
            const cz = -self.planes[g0 + 2];
            const dx = -self.planes[g1];
            const dy = -self.planes[g1 + 1];
            const dz = -self.planes[g1 + 2];
            self.arcs[e] = cx;
            self.arcs[stride + e] = cy;
            self.arcs[2 * stride + e] = cz;
            self.arcs[3 * stride + e] = dx;
            self.arcs[4 * stride + e] = dy;
            self.arcs[5 * stride + e] = dz;
            self.arcs[6 * stride + e] = dy * cz - dz * cy;
            self.arcs[7 * stride + e] = dz * cx - dx * cz;
            self.arcs[8 * stride + e] = dx * cy - dy * cx;
        }
    }

    /// es_x/y/z: edge directions SoA, padded to W with zeros.
    fn edgeSoa(self: *Poly, arena: *Arena) void {
        if (self.es_x.len != 0) return;
        const padded = (self.edge_count + W - 1) / W * W;
        self.es_x = arena.alloc(f32, padded);
        self.es_y = arena.alloc(f32, padded);
        self.es_z = arena.alloc(f32, padded);
        for (0..padded) |j| {
            self.es_x[j] = if (j < self.edge_count) self.edges[j * 3] else 0;
            self.es_y[j] = if (j < self.edge_count) self.edges[j * 3 + 1] else 0;
            self.es_z[j] = if (j < self.edge_count) self.edges[j * 3 + 2] else 0;
        }
    }

    /// Box-axis x edge crosses, cell c = axis * edge_count + edge, SoA padded to W (NaN x: parallel or pad).
    fn crossAxes(self: *Poly, arena: *Arena) void {
        if (self.cross_x.len != 0) return;
        const cells = 3 * self.edge_count;
        const padded = (cells + W - 1) / W * W;
        self.cross_x = arena.alloc(f32, padded);
        self.cross_y = arena.alloc(f32, padded);
        self.cross_z = arena.alloc(f32, padded);
        self.cross_lo = arena.alloc(f32, padded);
        self.cross_hi = arena.alloc(f32, padded);
        self.cross_have = arena.alloc(bool, padded);
        @memset(self.cross_have, false);
        for (cells..padded) |c| self.cross_x[c] = @bitCast(NAN_BITS);
        for (0..3) |i| {
            const ax = UNIT_AXES[i * 3];
            const ay = UNIT_AXES[i * 3 + 1];
            const az = UNIT_AXES[i * 3 + 2];
            for (0..self.edge_count) |j| {
                const bx = self.edges[j * 3];
                const by = self.edges[j * 3 + 1];
                const bz = self.edges[j * 3 + 2];
                const x = ay * bz - az * by;
                const y = az * bx - ax * bz;
                const z = ax * by - ay * bx;
                const length = @sqrt(x * x + y * y + z * z);
                const c = i * self.edge_count + j;
                if (length < PARALLEL) {
                    self.cross_x[c] = @bitCast(NAN_BITS);
                    continue;
                }
                self.cross_x[c] = x / length;
                self.cross_y[c] = y / length;
                self.cross_z[c] = z / length;
            }
        }
    }

    fn markAligned(self: *Poly, arena: *Arena) void {
        if (self.aligned_minkowski.len != 0) return;
        self.aligned_minkowski = arena.alloc(bool, 3 * self.edge_count);
        markMinkowski(&box_planes, &box_edge_faces, 12, 3, self, self.aligned_minkowski, arena);
    }

    fn crossRange(self: *Poly, c: usize) void {
        if (self.cross_have[c]) return;
        self.cross_have[c] = true;
        var range: [2]f32 = undefined;
        self.project(self.cross_x[c], self.cross_y[c], self.cross_z[c], &range);
        self.cross_lo[c] = range[0];
        self.cross_hi[c] = range[1];
    }
};

/// Polytope.box(min, max) as `a` of the aligned path: bounds only.
fn alignedBox(p: *Poly, min_x: f32, min_y: f32, min_z: f32, max_x: f32, max_y: f32, max_z: f32, tag: i32) void {
    p.* = .{ .aligned = true, .tag = tag, .min_x = min_x, .min_y = min_y, .min_z = min_z, .max_x = max_x, .max_y = max_y, .max_z = max_z };
}

/// Fixed-size storage for one prism.
const PrismStore = struct {
    pts: [18]f32,
    planes: [20]f32,
    face_start: [6]i32,
    loops: [18]i32,
    axes: [15]f32,
    edges: [27]f32,
    edge_faces: [27]i32,
    slot_direction: [20]i32,
    inward: [5]bool,
};

fn uniqueIndex(into: []f32, count: usize, x: f32, y: f32, z: f32) usize {
    for (0..count) |k| {
        if (@abs(into[k * 3] * x + into[k * 3 + 1] * y + into[k * 3 + 2] * z) > 0.9999) return k;
    }
    into[count * 3] = x;
    into[count * 3 + 1] = y;
    into[count * 3 + 2] = z;
    return count;
}

fn unique(into: []f32, count: usize, x: f32, y: f32, z: f32) usize {
    for (0..count) |k| {
        if (@abs(into[k * 3] * x + into[k * 3 + 1] * y + into[k * 3 + 2] * z) > 0.9999) return count;
    }
    into[count * 3] = x;
    into[count * 3 + 1] = y;
    into[count * 3 + 2] = z;
    return count + 1;
}

fn setPlane(p: *Poly, f: usize, nx: f32, ny: f32, nz: f32, vertex: usize) void {
    p.planes[f * 4] = nx;
    p.planes[f * 4 + 1] = ny;
    p.planes[f * 4 + 2] = nz;
    p.planes[f * 4 + 3] = nx * p.pts[vertex * 3] + ny * p.pts[vertex * 3 + 1] + nz * p.pts[vertex * 3 + 2];
}

/// Polytope.prismMeets for 8 triangles (lanes) at once, same per-lane floats; bit l: lane meets `other`.
fn prismMeets(tri: []const f32, count: usize, depth: f32, other: *const Poly, margin: f32, bounds: *[6][W]f32) u8 {
    var t: [12][W]f32 = undefined;
    for (0..W) |l| {
        const r = tri[@min(l, count - 1) * 13 ..][0..12];
        inline for (0..12) |k| t[k][l] = r[k];
    }
    const ox: V = t[9];
    const oy: V = t[10];
    const oz: V = t[11];
    const x0 = @as(V, t[0]) - ox;
    const y0 = @as(V, t[1]) - oy;
    const z0 = @as(V, t[2]) - oz;
    const x1 = @as(V, t[3]) - ox;
    const y1 = @as(V, t[4]) - oy;
    const z1 = @as(V, t[5]) - oz;
    const x2 = @as(V, t[6]) - ox;
    const y2 = @as(V, t[7]) - oy;
    const z2 = @as(V, t[8]) - oz;
    const ax = x1 - x0;
    const ay = y1 - y0;
    const az = z1 - z0;
    const bx = x2 - x0;
    const by = y2 - y0;
    const bz = z2 - z0;
    const nx = ay * bz - az * by;
    const ny = az * bx - ax * bz;
    const nz = ax * by - ay * bx;
    const scale = @as(V, @splat(depth)) / @sqrt(nx * nx + ny * ny + nz * nz);
    const dx = nx * scale;
    const dy = ny * scale;
    const dz = nz * scale;
    const lx = vJavaMin(vJavaMin(x0, x1), x2);
    const hx = vJavaMax(vJavaMax(x0, x1), x2);
    const ly = vJavaMin(vJavaMin(y0, y1), y2);
    const hy = vJavaMax(vJavaMax(y0, y1), y2);
    const lz = vJavaMin(vJavaMin(z0, z1), z2);
    const hz = vJavaMax(vJavaMax(z0, z1), z2);
    const min_x = vJavaMin(lx, vJavaMin(vJavaMin(x0 - dx, x1 - dx), x2 - dx));
    const max_x = vJavaMax(hx, vJavaMax(vJavaMax(x0 - dx, x1 - dx), x2 - dx));
    const min_y = vJavaMin(ly, vJavaMin(vJavaMin(y0 - dy, y1 - dy), y2 - dy));
    const max_y = vJavaMax(hy, vJavaMax(vJavaMax(y0 - dy, y1 - dy), y2 - dy));
    const min_z = vJavaMin(lz, vJavaMin(vJavaMin(z0 - dz, z1 - dz), z2 - dz));
    const max_z = vJavaMax(hz, vJavaMax(vJavaMax(z0 - dz, z1 - dz), z2 - dz));
    const m: V = @splat(margin);
    const meet = @intFromBool(min_x <= @as(V, @splat(other.max_x + margin))) & @intFromBool(@as(V, @splat(other.min_x)) <= max_x + m) &
        @intFromBool(min_y <= @as(V, @splat(other.max_y + margin))) & @intFromBool(@as(V, @splat(other.min_y)) <= max_y + m) &
        @intFromBool(min_z <= @as(V, @splat(other.max_z + margin))) & @intFromBool(@as(V, @splat(other.min_z)) <= max_z + m);
    bounds.* = .{ min_x, min_y, min_z, max_x, max_y, max_z };
    var bits: u8 = @bitCast(meet);
    if (count < W) bits &= (@as(u8, 1) << @intCast(count)) - 1;
    return bits;
}

/// Polytope.prism + faces(PRISM_FACES): points, planes, axes. Loops and edges on first edge stage (`prismEdges`).
fn prism(p: *Poly, s: *PrismStore, tri: []const f32, ox: f32, oy: f32, oz: f32, depth: f32) void {
    const tag = p.tag;
    p.* = .{ .tag = tag, .pending = s };
    p.pts = &s.pts;
    p.n = 6;
    for (0..3) |c| {
        s.pts[c * 3] = tri[c * 3] - ox;
        s.pts[c * 3 + 1] = tri[c * 3 + 1] - oy;
        s.pts[c * 3 + 2] = tri[c * 3 + 2] - oz;
    }
    const pts = &s.pts;
    const ax = pts[3] - pts[0];
    const ay = pts[4] - pts[1];
    const az = pts[5] - pts[2];
    const bx = pts[6] - pts[0];
    const by = pts[7] - pts[1];
    const bz = pts[8] - pts[2];
    const nx = ay * bz - az * by;
    const ny = az * bx - ax * bz;
    const nz = ax * by - ay * bx;
    const scale = depth / @sqrt(nx * nx + ny * ny + nz * nz);
    for (0..3) |c| {
        pts[(c + 3) * 3] = pts[c * 3] - nx * scale;
        pts[(c + 3) * 3 + 1] = pts[c * 3 + 1] - ny * scale;
        pts[(c + 3) * 3 + 2] = pts[c * 3 + 2] - nz * scale;
    }
    var mx: f32 = 0;
    var my: f32 = 0;
    var mz: f32 = 0;
    for (0..6) |i| {
        mx += pts[i * 3];
        my += pts[i * 3 + 1];
        mz += pts[i * 3 + 2];
    }
    mx /= 6;
    my /= 6;
    mz /= 6;
    p.planes = &s.planes;
    p.face_count = PRISM_FACES.len;
    p.axes = &s.axes;
    p.edges = &s.edges;
    inline for (PRISM_FACES, 0..) |face, f| {
        var fx: f32 = 0;
        var fy: f32 = 0;
        var fz: f32 = 0;
        inline for (0..face.len) |k| {
            const a: usize = @intCast(face[k] * 3);
            const b: usize = @intCast(face[(k + 1) % face.len] * 3);
            fx += (pts[a + 1] - pts[b + 1]) * (pts[a + 2] + pts[b + 2]);
            fy += (pts[a + 2] - pts[b + 2]) * (pts[a] + pts[b]);
            fz += (pts[a] - pts[b]) * (pts[a + 1] + pts[b + 1]);
        }
        const length = @sqrt(fx * fx + fy * fy + fz * fz);
        fx /= length;
        fy /= length;
        fz /= length;
        const first: usize = @intCast(face[0] * 3);
        const inward = fx * (mx - pts[first]) + fy * (my - pts[first + 1]) + fz * (mz - pts[first + 2]) > 0;
        if (inward) {
            fx = -fx;
            fy = -fy;
            fz = -fz;
        }
        s.inward[f] = inward;
        setPlane(p, f, fx, fy, fz, @intCast(face[0]));
        p.axis_count = unique(&s.axes, p.axis_count, fx, fy, fz);
    }
    p.bound();
}

/// Rest of `prism`: loops, edge directions (first-seen order), edge faces.
fn prismEdges(p: *Poly) void {
    const s = p.pending orelse return;
    p.pending = null;
    const pts = &s.pts;
    var cursor: usize = 0;
    // a -> b and b -> a: bitwise negations => same `uniqueIndex`; one normalisation per prism edge.
    var known = [_]i8{-1} ** 36;
    inline for (PRISM_FACES, 0..) |face, f| {
        s.face_start[f] = @intCast(cursor);
        for (0..face.len) |k| {
            s.loops[cursor] = if (s.inward[f]) face[face.len - 1 - k] else face[k];
            cursor += 1;
        }
        inline for (0..face.len) |k| {
            const va = face[k];
            const vb = face[(k + 1) % face.len];
            const key: usize = @as(usize, @intCast(@min(va, vb))) * 6 + @as(usize, @intCast(@max(va, vb)));
            var direction: usize = undefined;
            if (known[key] >= 0) {
                direction = @intCast(known[key]);
            } else {
                const a: usize = @intCast(va * 3);
                const b: usize = @intCast(vb * 3);
                const ex = pts[b] - pts[a];
                const ey = pts[b + 1] - pts[a + 1];
                const ez = pts[b + 2] - pts[a + 2];
                const el = @sqrt(ex * ex + ey * ey + ez * ez);
                direction = uniqueIndex(&s.edges, p.edge_count, ex / el, ey / el, ez / el);
                known[key] = @intCast(direction);
            }
            s.slot_direction[f * 4 + k] = @intCast(direction);
            p.edge_count = @max(p.edge_count, direction + 1);
        }
    }
    s.face_start[PRISM_FACES.len] = @intCast(cursor);
    p.face_start = &s.face_start;
    p.loops = &s.loops;
    for (0..9) |e| {
        s.edge_faces[e * 3] = PRISM_EDGES[e * 3];
        s.edge_faces[e * 3 + 1] = PRISM_EDGES[e * 3 + 2];
        s.edge_faces[e * 3 + 2] = s.slot_direction[@intCast(PRISM_EDGES[e * 3] * 4 + PRISM_EDGES[e * 3 + 1])];
    }
    p.edge_faces = &s.edge_faces;
    p.edge_face_count = 9;
}

// ---------------------------------------------------------------------------------------------- posing

/// JOML `Runtime.HAS_Math_fma` (`-Djoml.useMathFma`): its `Math.fma` fused, else a * b + c.
var use_fma = false;

fn fma(a: f32, b: f32, c: f32) f32 {
    return if (use_fma) @mulAdd(f32, a, b, c) else a * b + c;
}

/// JOML 1.10.5 `Quaternionf.transform(x, y, z, dest)` coefficients (unnormalised q => k = 1 / |q|^2).
const Rotation = struct {
    c: [9]f32,

    fn of(x: f32, y: f32, z: f32, w: f32) Rotation {
        const xx = x * x;
        const yy = y * y;
        const zz = z * z;
        const ww = w * w;
        const xy = x * y;
        const xz = x * z;
        const yz = y * z;
        const xw = x * w;
        const zw = z * w;
        const yw = y * w;
        const k = 1 / (xx + yy + zz + ww);
        return .{ .c = .{
            (xx - yy - zz + ww) * k, 2 * (xy - zw) * k,       2 * (xz + yw) * k,
            2 * (xy + zw) * k,       (yy - xx - zz + ww) * k, 2 * (yz - xw) * k,
            2 * (xz - yw) * k,       2 * (yz + xw) * k,       (zz - xx - yy + ww) * k,
        } };
    }

    fn apply(r: *const Rotation, x: f32, y: f32, z: f32) [3]f32 {
        const c = &r.c;
        return .{
            fma(c[0], x, fma(c[1], y, c[2] * z)),
            fma(c[3], x, fma(c[4], y, c[5] * z)),
            fma(c[6], x, fma(c[7], y, c[8] * z)),
        };
    }
};

/// `ConvexHull`, local frame; immutable, shared by threads. Layout: `NarrowNative.hull`.
pub const Hull = struct {
    words: []u32,
    n: usize,
    face_count: usize,
    axis_count: usize,
    edge_count: usize,
    edge_face_count: usize,
    lo: [3]f32,
    hi: [3]f32,
    pts: []const f32,
    planes: []const f32,
    axes: []const f32,
    edges: []const f32,
    face_start: []const i32,
    loops: []const i32,
    edge_faces: []const i32,
};

pub fn createHull(input: [*]const u32) ?*Hull {
    const counts: [6]usize = .{ input[0], input[1], input[2], input[3], input[4], input[5] };
    const n = counts[0];
    const faces = counts[1];
    const total = 12 + n * 3 + faces * 4 + counts[2] * 3 + counts[3] * 3 + faces + 1 + counts[5] + counts[4] * 3;
    const words = std.heap.page_allocator.dupe(u32, input[0..total]) catch return null;
    const h = std.heap.page_allocator.create(Hull) catch {
        std.heap.page_allocator.free(words);
        return null;
    };
    var in = In{ .words = words.ptr, .at = 6 };
    h.* = .{
        .words = words,
        .n = n,
        .face_count = faces,
        .axis_count = counts[2],
        .edge_count = counts[3],
        .edge_face_count = counts[4],
        .lo = .{ in.float(), in.float(), in.float() },
        .hi = .{ in.float(), in.float(), in.float() },
        .pts = in.floats(n * 3),
        .planes = in.floats(faces * 4),
        .axes = in.floats(counts[2] * 3),
        .edges = in.floats(counts[3] * 3),
        .face_start = in.ints(faces + 1),
        .loops = in.ints(counts[5]),
        .edge_faces = in.ints(counts[4] * 3),
    };
    return h;
}

pub fn destroyHull(h: *Hull) void {
    std.heap.page_allocator.free(h.words);
    std.heap.page_allocator.destroy(h);
}

/// One world shape's frame (input record past the handle); hull null => box of half extents `e`.
const Pose = struct {
    hull: ?*const Hull,
    rotation: Rotation,
    e: [3]f32,
    done: bool = false,
};

/// `StepBody.reaches(x, y, z, rx, ry, rz)`; env = min xyz, max xyz.
fn reaches(env: *const [6]f32, margin: f32, x: f32, y: f32, z: f32, rx0: f32, ry0: f32, rz0: f32) bool {
    const slack = 1.0e-3 + margin;
    const rx = rx0 * 1.001 + slack;
    const ry = ry0 * 1.001 + slack;
    const rz = rz0 * 1.001 + slack;
    return x - rx <= env[3] and x + rx >= env[0] and y - ry <= env[4] and y + ry >= env[1] and
        z - rz <= env[5] and z + rz >= env[2];
}

/// `StepBody.reaches(hull, ...)`: JOML `Matrix3f.rotation(q)` abs rows over the local bounds box.
fn hullReaches(env: *const [6]f32, margin: f32, h: *const Hull, r: *const Rotation, q: [4]f32, x: f32, y: f32, z: f32) bool {
    const centre = r.apply((h.lo[0] + h.hi[0]) * 0.5, (h.lo[1] + h.hi[1]) * 0.5, (h.lo[2] + h.hi[2]) * 0.5);
    const hx = (h.hi[0] - h.lo[0]) * 0.5;
    const hy = (h.hi[1] - h.lo[1]) * 0.5;
    const hz = (h.hi[2] - h.lo[2]) * 0.5;
    const w2 = q[3] * q[3];
    const x2 = q[0] * q[0];
    const y2 = q[1] * q[1];
    const z2 = q[2] * q[2];
    const zw = q[2] * q[3];
    const dzw = zw + zw;
    const xy = q[0] * q[1];
    const dxy = xy + xy;
    const xz = q[0] * q[2];
    const dxz = xz + xz;
    const yw = q[1] * q[3];
    const dyw = yw + yw;
    const yz = q[1] * q[2];
    const dyz = yz + yz;
    const xw = q[0] * q[3];
    const dxw = xw + xw;
    const m00 = w2 + x2 - z2 - y2;
    const m01 = dxy + dzw;
    const m02 = dxz - dyw;
    const m10 = -dzw + dxy;
    const m11 = y2 - z2 + w2 - x2;
    const m12 = dyz + dxw;
    const m20 = dyw + dxz;
    const m21 = dyz - dxw;
    const m22 = z2 - y2 - x2 + w2;
    return reaches(env, margin, x + centre[0], y + centre[1], z + centre[2], @abs(m00) * hx + @abs(m10) * hy + @abs(m20) * hz,
        @abs(m01) * hx + @abs(m11) * hy + @abs(m21) * hz, @abs(m02) * hx + @abs(m12) * hy + @abs(m22) * hz);
}

/// `Polytope.hull` / `Polytope.box(c, rotation, e)` points + bounds; planes, axes, edges by `poseRest`.
fn posePoints(p: *Poly, pose: *const Pose, cx: f32, cy: f32, cz: f32, arena: *Arena) void {
    const r = &pose.rotation;
    if (pose.hull) |h| {
        p.n = h.n;
        p.pts = arena.alloc(f32, h.n * 3);
        for (0..h.n) |i| {
            const v = r.apply(h.pts[i * 3], h.pts[i * 3 + 1], h.pts[i * 3 + 2]);
            p.pts[i * 3] = cx + v[0];
            p.pts[i * 3 + 1] = cy + v[1];
            p.pts[i * 3 + 2] = cz + v[2];
        }
    } else {
        p.n = 8;
        p.pts = arena.alloc(f32, 24);
        const e = pose.e;
        for (0..8) |i| {
            const v = r.apply(if (i & 1 == 0) -e[0] else e[0], if (i & 2 == 0) -e[1] else e[1], if (i & 4 == 0) -e[2] else e[2]);
            p.pts[i * 3] = cx + v[0];
            p.pts[i * 3 + 1] = cy + v[1];
            p.pts[i * 3 + 2] = cz + v[2];
        }
    }
    p.bound();
}

fn poseRest(p: *Poly, pose: *Pose, arena: *Arena) void {
    if (pose.done) return;
    pose.done = true;
    const r = &pose.rotation;
    if (pose.hull) |h| {
        p.face_count = h.face_count;
        p.face_start = h.face_start;
        p.loops = h.loops;
        p.planes = arena.alloc(f32, h.face_count * 4);
        for (0..h.face_count) |f| {
            const v = r.apply(h.planes[f * 4], h.planes[f * 4 + 1], h.planes[f * 4 + 2]);
            setPlane(p, f, v[0], v[1], v[2], @intCast(h.loops[@intCast(h.face_start[f])]));
        }
        p.axis_count = h.axis_count;
        p.axes = rotateAll(r, h.axes, h.axis_count, arena);
        p.edge_count = h.edge_count;
        p.edges = rotateAll(r, h.edges, h.edge_count, arena);
        p.edge_faces = h.edge_faces;
        p.edge_face_count = h.edge_face_count;
    } else {
        p.face_count = 6;
        p.face_start = &BOX_START;
        p.loops = &BOX_LOOPS;
        p.planes = arena.alloc(f32, 24);
        for (0..6) |f| {
            const v = r.apply(BOX_NORMALS[f * 3], BOX_NORMALS[f * 3 + 1], BOX_NORMALS[f * 3 + 2]);
            setPlane(p, f, v[0], v[1], v[2], @intCast(BOX_LOOPS[@intCast(BOX_START[f])]));
        }
        p.axes = rotateAll(r, &UNIT_AXES, 3, arena);
        p.axis_count = 3;
        p.edges = p.axes;
        p.edge_count = 3;
        p.edge_faces = &box_edge_faces;
        p.edge_face_count = 12;
    }
}

fn rotateAll(r: *const Rotation, source: []const f32, count: usize, arena: *Arena) []f32 {
    const out = arena.alloc(f32, count * 3);
    for (0..count) |k| {
        const v = r.apply(source[k * 3], source[k * 3 + 1], source[k * 3 + 2]);
        out[k * 3] = v[0];
        out[k * 3 + 1] = v[1];
        out[k * 3 + 2] = v[2];
    }
    return out;
}

// ---------------------------------------------------------------------------------------------- contacts

pub const Out = struct {
    buf: [*]u32,
    capacity: usize,
    count: usize = 0,
    shape: i32 = 0,
    source: i32 = 0,

    /// Record: x y z nx ny nz separation (f32), tagA tagB shape source (i32).
    pub const WORDS = 11;

    fn add(self: *Out, x: f32, y: f32, z: f32, nx: f32, ny: f32, nz: f32, sep: f32, a: i32, b: i32) void {
        if (self.count >= self.capacity) {
            self.count += 1;
            return;
        }
        const r = self.buf + self.count * WORDS;
        r[0] = @bitCast(x);
        r[1] = @bitCast(y);
        r[2] = @bitCast(z);
        r[3] = @bitCast(nx);
        r[4] = @bitCast(ny);
        r[5] = @bitCast(nz);
        r[6] = @bitCast(sep);
        r[7] = @bitCast(a);
        r[8] = @bitCast(b);
        r[9] = @bitCast(self.shape);
        r[10] = @bitCast(self.source);
        self.count += 1;
    }
};

fn markMinkowski(pa: []const f32, edges_a: []const i32, count_a: usize, directions_a: usize, b: *Poly, out: []bool, arena: *Arena) void {
    b.poseArcs(arena);
    const stride = b.edge_count;
    @memset(out[0 .. directions_a * stride], false);
    const arcs = b.arcs;
    const p = b.arc_stride;
    const edges_b = b.edge_faces;
    const zero: V = @splat(0);
    for (0..count_a) |ea| {
        const f0: usize = @intCast(edges_a[ea * 3] * 4);
        const f1: usize = @intCast(edges_a[ea * 3 + 1] * 4);
        const row: usize = @as(usize, @intCast(edges_a[ea * 3 + 2])) * stride;
        const ax = pa[f0];
        const ay = pa[f0 + 1];
        const az = pa[f0 + 2];
        const bx = pa[f1];
        const by = pa[f1 + 1];
        const bz = pa[f1 + 2];
        const bxa_x: V = @splat(by * az - bz * ay);
        const bxa_y: V = @splat(bz * ax - bx * az);
        const bxa_z: V = @splat(bx * ay - by * ax);
        const vax: V = @splat(ax);
        const vay: V = @splat(ay);
        const vaz: V = @splat(az);
        const vbx: V = @splat(bx);
        const vby: V = @splat(by);
        const vbz: V = @splat(bz);
        var eb: usize = 0;
        while (eb < p) : (eb += W) {
            const c0: V = arcs[eb..][0..W].*;
            const c1: V = arcs[p + eb ..][0..W].*;
            const c2: V = arcs[2 * p + eb ..][0..W].*;
            const c3: V = arcs[3 * p + eb ..][0..W].*;
            const c4: V = arcs[4 * p + eb ..][0..W].*;
            const c5: V = arcs[5 * p + eb ..][0..W].*;
            const c6: V = arcs[6 * p + eb ..][0..W].*;
            const c7: V = arcs[7 * p + eb ..][0..W].*;
            const c8: V = arcs[8 * p + eb ..][0..W].*;
            const cba = c0 * bxa_x + c1 * bxa_y + c2 * bxa_z;
            const dba = c3 * bxa_x + c4 * bxa_y + c5 * bxa_z;
            const adc = vax * c6 + vay * c7 + vaz * c8;
            const bdc = vbx * c6 + vby * c7 + vbz * c8;
            const keep = @intFromBool(!(cba * dba > zero)) & @intFromBool(adc * bdc <= zero) & @intFromBool(cba * bdc >= zero);
            var bits: u8 = @bitCast(keep);
            while (bits != 0) : (bits &= bits - 1) {
                const e = eb + @ctz(bits);
                if (e < b.edge_face_count) out[row + @as(usize, @intCast(edges_b[e * 3 + 2]))] = true;
            }
        }
    }
}

fn vJavaMin(a: V, b: V) V {
    const zero: V = @splat(0);
    const negative_zero = @as(@Vector(W, u32), @bitCast(b)) == @as(@Vector(W, u32), @splat(0x80000000));
    const take_b = @intFromBool(a == zero) & @intFromBool(b == zero) & @intFromBool(negative_zero);
    const r = @select(f32, a <= b, a, b);
    const nan = a != a;
    return @select(f32, nan, a, @select(f32, take_b == @as(@Vector(W, u1), @splat(1)), b, r));
}

fn vJavaMax(a: V, b: V) V {
    const zero: V = @splat(0);
    const negative_zero = @as(@Vector(W, u32), @bitCast(a)) == @as(@Vector(W, u32), @splat(0x80000000));
    const take_b = @intFromBool(a == zero) & @intFromBool(b == zero) & @intFromBool(negative_zero);
    const r = @select(f32, a >= b, a, b);
    const nan = a != a;
    return @select(f32, nan, a, @select(f32, take_b == @as(@Vector(W, u1), @splat(1)), b, r));
}

/// ConvexContacts: one per thread.
const Narrow = struct {
    arena: *Arena,
    range_a: [2]f32 = undefined,
    range_b: [2]f32 = undefined,
    minkowski: []bool = &.{},
    polygon: []f32 = &.{},
    clipped: []f32 = &.{},
    best_edge: f32 = 0,
    edge_x: f32 = 0,
    edge_y: f32 = 0,
    edge_z: f32 = 0,
    edge_a: i32 = -1,
    edge_b: i32 = -1,
    near: [NEAR]usize = undefined,
    near_distance: [NEAR]f32 = undefined,
    near_count: usize = 0,

    fn gap(self: *const Narrow) f32 {
        return javaMax(self.range_b[0] - self.range_a[1], self.range_a[0] - self.range_b[1]);
    }

    fn separation(self: *Narrow, a: *const Poly, b: *const Poly, x: f32, y: f32, z: f32) f32 {
        a.project(x, y, z, &self.range_a);
        b.project(x, y, z, &self.range_b);
        return self.gap();
    }

    fn signOf(self: *const Narrow) f32 {
        return if (self.range_b[0] - self.range_a[1] >= self.range_a[0] - self.range_b[1]) 1 else -1;
    }

    fn alignedRange(self: *Narrow, a: *const Poly, x: f32, y: f32, z: f32) void {
        self.range_a[0] = (if (x >= 0) a.min_x else a.max_x) * x + (if (y >= 0) a.min_y else a.max_y) * y + (if (z >= 0) a.min_z else a.max_z) * z;
        self.range_a[1] = (if (x >= 0) a.max_x else a.min_x) * x + (if (y >= 0) a.max_y else a.min_y) * y + (if (z >= 0) a.max_z else a.min_z) * z;
    }

    fn collide(self: *Narrow, a: *Poly, b: *Poly, margin: f32, out: *Out) void {
        if (a.aligned) return self.collideAligned(a, b, margin, out);
        b.projectFaces(self.arena);
        var best_face: f32 = -MAX;
        var face_x: f32 = 0;
        var face_y: f32 = 0;
        var face_z: f32 = 0;
        var face_of_a = true;
        for (0..2) |pass| {
            const owner = if (pass == 0) a else b;
            for (0..owner.axis_count) |k| {
                const x = owner.axes[k * 3];
                const y = owner.axes[k * 3 + 1];
                const z = owner.axes[k * 3 + 2];
                var sep: f32 = undefined;
                if (pass == 0) {
                    sep = self.separation(a, b, x, y, z);
                } else {
                    a.project(x, y, z, &self.range_a);
                    self.range_b[0] = b.face_ranges[k * 2];
                    self.range_b[1] = b.face_ranges[k * 2 + 1];
                    sep = self.gap();
                }
                if (sep > margin) return;
                if (sep > best_face) {
                    best_face = sep;
                    const sign = self.signOf();
                    face_x = x * sign;
                    face_y = y * sign;
                    face_z = z * sign;
                    face_of_a = pass == 0;
                }
            }
        }
        prismEdges(a);
        // Face-separated: pruned pass result would be discarded by `rescan` (resets best) => straight to it.
        if (best_face > 0) {
            if (!self.rescan(a, b, margin, best_face + EDGE_PREFERENCE)) return;
        } else {
            const cells = a.edge_count * b.edge_count;
            if (self.minkowski.len < cells) self.minkowski = self.arena.alloc(bool, cells * 2);
            markMinkowski(a.planes, a.edge_faces, a.edge_face_count, a.edge_count, b, self.minkowski, self.arena);
            if (!self.edges(a, b, margin)) return;
            if (javaMax(best_face, self.best_edge) > 0 and !self.rescan(a, b, margin, javaMax(self.best_edge, best_face + EDGE_PREFERENCE))) return;
        }
        if (self.edge_a >= 0 and self.best_edge > best_face + EDGE_PREFERENCE) {
            self.edge(a, b, @intCast(self.edge_a), @intCast(self.edge_b), self.edge_x, self.edge_y, self.edge_z, self.best_edge, out);
            return;
        }
        self.face(a, b, face_of_a, face_x, face_y, face_z, margin, out);
    }

    fn collideAligned(self: *Narrow, a: *Poly, b: *Poly, margin: f32, out: *Out) void {
        b.projectFaces(self.arena);
        var best_face: f32 = -MAX;
        var face_x: f32 = 0;
        var face_y: f32 = 0;
        var face_z: f32 = 0;
        var face_of_a = true;
        for (0..3) |k| {
            const x: f32 = if (k == 0) 1 else 0;
            const y: f32 = if (k == 1) 1 else 0;
            const z: f32 = if (k == 2) 1 else 0;
            self.alignedRange(a, x, y, z);
            self.range_b[0] = if (k == 0) b.min_x else if (k == 1) b.min_y else b.min_z;
            self.range_b[1] = if (k == 0) b.max_x else if (k == 1) b.max_y else b.max_z;
            const sep = self.gap();
            if (sep > margin) return;
            if (sep > best_face) {
                best_face = sep;
                const sign = self.signOf();
                face_x = x * sign;
                face_y = y * sign;
                face_z = z * sign;
            }
        }
        for (0..b.axis_count) |k| {
            const x = b.axes[k * 3];
            const y = b.axes[k * 3 + 1];
            const z = b.axes[k * 3 + 2];
            self.alignedRange(a, x, y, z);
            self.range_b[0] = b.face_ranges[k * 2];
            self.range_b[1] = b.face_ranges[k * 2 + 1];
            const sep = self.gap();
            if (sep > margin) return;
            if (sep > best_face) {
                best_face = sep;
                const sign = self.signOf();
                face_x = x * sign;
                face_y = y * sign;
                face_z = z * sign;
                face_of_a = false;
            }
        }
        if (best_face > 0) {
            if (!self.alignedEdges(a, b, margin, false, best_face + EDGE_PREFERENCE)) return;
        } else {
            if (!self.alignedEdges(a, b, margin, true, 0)) return;
            if (javaMax(best_face, self.best_edge) > 0 and !self.alignedEdges(a, b, margin, false, javaMax(self.best_edge, best_face + EDGE_PREFERENCE))) return;
        }
        if (self.edge_a >= 0 and self.best_edge > best_face + EDGE_PREFERENCE) {
            self.edge(a, b, @intCast(self.edge_a), @intCast(self.edge_b), self.edge_x, self.edge_y, self.edge_z, self.best_edge, out);
            return;
        }
        self.face(a, b, face_of_a, face_x, face_y, face_z, margin, out);
    }

    fn edgeAxis(self: *Narrow, sep: f32, margin: f32, x: f32, y: f32, z: f32, i: usize, j: usize) bool {
        if (sep > margin) return false;
        if (sep > self.best_edge) {
            self.best_edge = sep;
            const sign = self.signOf();
            self.edge_x = x * sign;
            self.edge_y = y * sign;
            self.edge_z = z * sign;
            self.edge_a = @intCast(i);
            self.edge_b = @intCast(j);
        }
        return true;
    }

    fn edges(self: *Narrow, a: *const Poly, b: *const Poly, margin: f32) bool {
        self.best_edge = -MAX;
        self.edge_a = -1;
        for (0..a.edge_count) |i| {
            const ax = a.edges[i * 3];
            const ay = a.edges[i * 3 + 1];
            const az = a.edges[i * 3 + 2];
            for (0..b.edge_count) |j| {
                if (!self.minkowski[i * b.edge_count + j]) continue;
                const bx = b.edges[j * 3];
                const by = b.edges[j * 3 + 1];
                const bz = b.edges[j * 3 + 2];
                var x = ay * bz - az * by;
                var y = az * bx - ax * bz;
                var z = ax * by - ay * bx;
                const length = @sqrt(x * x + y * y + z * z);
                if (length < PARALLEL) continue;
                x /= length;
                y /= length;
                z /= length;
                const sep = self.separation(a, b, x, y, z);
                if (!self.edgeAxis(sep, margin, x, y, z, i, j)) return false;
            }
        }
        return true;
    }

    /// Bound per lane (8 b directions), same per-lane float sequence as the scalar loop; survivors in j order.
    fn rescan(self: *Narrow, a: *const Poly, b: *Poly, margin: f32, floor: f32) bool {
        self.nearest(a, b);
        b.edgeSoa(self.arena);
        const p = b.pts;
        self.best_edge = -MAX;
        self.edge_a = -1;
        const vmargin: V = @splat(margin);
        const parallel: V = @splat(PARALLEL);
        for (0..a.edge_count) |i| {
            const ax: V = @splat(a.edges[i * 3]);
            const ay: V = @splat(a.edges[i * 3 + 1]);
            const az: V = @splat(a.edges[i * 3 + 2]);
            var j0: usize = 0;
            while (j0 < b.edge_count) : (j0 += W) {
                const bx: V = b.es_x[j0..][0..W].*;
                const by: V = b.es_y[j0..][0..W].*;
                const bz: V = b.es_z[j0..][0..W].*;
                var x = ay * bz - az * by;
                var y = az * bx - ax * bz;
                var z = ax * by - ay * bx;
                const length = @sqrt(x * x + y * y + z * z);
                const flat = length < parallel;
                x /= length;
                y /= length;
                z /= length;
                var min_a: V = @splat(MAX);
                var max_a: V = @splat(-MAX);
                for (0..a.n) |v| {
                    const d = @as(V, @splat(a.pts[v * 3])) * x + @as(V, @splat(a.pts[v * 3 + 1])) * y + @as(V, @splat(a.pts[v * 3 + 2])) * z;
                    min_a = @select(f32, d < min_a, d, min_a);
                    max_a = @select(f32, d > max_a, d, max_a);
                }
                var lo: V = @splat(MAX);
                var hi: V = @splat(-MAX);
                for (self.near[0..self.near_count]) |v| {
                    const d = @as(V, @splat(p[v * 3])) * x + @as(V, @splat(p[v * 3 + 1])) * y + @as(V, @splat(p[v * 3 + 2])) * z;
                    lo = @select(f32, d < lo, d, lo);
                    hi = @select(f32, d > hi, d, hi);
                }
                const bound = vJavaMax(lo - max_a, min_a - hi);
                const vbest: V = @splat(javaMax(floor, self.best_edge));
                const skip = @intFromBool(flat) | (@intFromBool(bound <= vmargin) & @intFromBool(bound < vbest));
                const bounds: [W]f32 = bound;
                var bits: u8 = ~@as(u8, @bitCast(skip));
                if (b.edge_count - j0 < W) bits &= (@as(u8, 1) << @intCast(b.edge_count - j0)) - 1;
                const xs: [W]f32 = x;
                const ys: [W]f32 = y;
                const zs: [W]f32 = z;
                const mins: [W]f32 = min_a;
                const maxs: [W]f32 = max_a;
                while (bits != 0) : (bits &= bits - 1) {
                    const l = @ctz(bits);
                    // Best so far raises the floor: gap <= bound < best can neither win nor early-out.
                    if (bounds[l] <= margin and bounds[l] < self.best_edge) continue;
                    self.range_a = .{ mins[l], maxs[l] };
                    b.project(xs[l], ys[l], zs[l], &self.range_b);
                    if (!self.edgeAxis(self.gap(), margin, xs[l], ys[l], zs[l], i, j0 + l)) return false;
                }
            }
        }
        return true;
    }

    fn nearest(self: *Narrow, a: *const Poly, b: *const Poly) void {
        var cx: f32 = 0;
        var cy: f32 = 0;
        var cz: f32 = 0;
        for (0..a.n) |i| {
            cx += a.pts[i * 3];
            cy += a.pts[i * 3 + 1];
            cz += a.pts[i * 3 + 2];
        }
        const count: f32 = @floatFromInt(a.n);
        cx /= count;
        cy /= count;
        cz /= count;
        self.nearestTo(cx, cy, cz, b);
    }

    fn nearestTo(self: *Narrow, cx: f32, cy: f32, cz: f32, b: *const Poly) void {
        self.near_count = 0;
        const p = b.pts;
        for (0..b.n) |v| {
            const dx = p[v * 3] - cx;
            const dy = p[v * 3 + 1] - cy;
            const dz = p[v * 3 + 2] - cz;
            const d = dx * dx + dy * dy + dz * dz;
            var k: usize = undefined;
            if (self.near_count < NEAR) {
                k = self.near_count;
                self.near_count += 1;
            } else {
                k = NEAR;
            }
            while (k > 0 and self.near_distance[k - 1] > d) : (k -= 1) {
                if (k < NEAR) {
                    self.near[k] = self.near[k - 1];
                    self.near_distance[k] = self.near_distance[k - 1];
                }
            }
            if (k < NEAR) {
                self.near[k] = v;
                self.near_distance[k] = d;
            }
        }
    }

    /// Pruned: Minkowski cells. Else every cell, skipping those whose bound U >= gap (b's points nearest a's centre)
    /// stays <= margin and < floor (as `rescan`).
    fn alignedEdges(self: *Narrow, a: *const Poly, b: *Poly, margin: f32, pruned: bool, floor: f32) bool {
        b.crossAxes(self.arena);
        if (pruned) b.markAligned(self.arena) else self.nearestTo((a.min_x + a.max_x) * 0.5, (a.min_y + a.max_y) * 0.5, (a.min_z + a.max_z) * 0.5, b);
        self.best_edge = -MAX;
        self.edge_a = -1;
        const cells = 3 * b.edge_count;
        const zero: V = @splat(0);
        const vmargin: V = @splat(margin);
        const min_x: V = @splat(a.min_x);
        const min_y: V = @splat(a.min_y);
        const min_z: V = @splat(a.min_z);
        const max_x: V = @splat(a.max_x);
        const max_y: V = @splat(a.max_y);
        const max_z: V = @splat(a.max_z);
        var c0: usize = 0;
        while (c0 < cells) : (c0 += W) {
            const x: V = b.cross_x[c0..][0..W].*;
            const y: V = b.cross_y[c0..][0..W].*;
            const z: V = b.cross_z[c0..][0..W].*;
            const px = x >= zero;
            const py = y >= zero;
            const pz = z >= zero;
            const lo_a = @select(f32, px, min_x, max_x) * x + @select(f32, py, min_y, max_y) * y + @select(f32, pz, min_z, max_z) * z;
            const hi_a = @select(f32, px, max_x, min_x) * x + @select(f32, py, max_y, min_y) * y + @select(f32, pz, max_z, min_z) * z;
            var bits: u8 = ~@as(u8, @bitCast(x != x));
            if (cells - c0 < W) bits &= (@as(u8, 1) << @intCast(cells - c0)) - 1;
            var bounds: [W]f32 = undefined;
            if (pruned) {
                var marked: u8 = 0;
                for (0..@min(W, cells - c0)) |l| {
                    if (b.aligned_minkowski[c0 + l]) marked |= @as(u8, 1) << @intCast(l);
                }
                bits &= marked;
            } else {
                var lo: V = @splat(MAX);
                var hi: V = @splat(-MAX);
                const p = b.pts;
                for (self.near[0..self.near_count]) |v| {
                    const d = @as(V, @splat(p[v * 3])) * x + @as(V, @splat(p[v * 3 + 1])) * y + @as(V, @splat(p[v * 3 + 2])) * z;
                    lo = @select(f32, d < lo, d, lo);
                    hi = @select(f32, d > hi, d, hi);
                }
                const bound = vJavaMax(lo - hi_a, lo_a - hi);
                const vbest: V = @splat(javaMax(floor, self.best_edge));
                bits &= ~@as(u8, @bitCast(@intFromBool(bound <= vmargin) & @intFromBool(bound < vbest)));
                bounds = bound;
            }
            const los: [W]f32 = lo_a;
            const his: [W]f32 = hi_a;
            while (bits != 0) : (bits &= bits - 1) {
                const l = @ctz(bits);
                if (!pruned and bounds[l] <= margin and bounds[l] < self.best_edge) continue;
                const c = c0 + l;
                b.crossRange(c);
                self.range_a = .{ los[l], his[l] };
                self.range_b = .{ b.cross_lo[c], b.cross_hi[c] };
                if (!self.edgeAxis(self.gap(), margin, b.cross_x[c], b.cross_y[c], b.cross_z[c], c / b.edge_count, c % b.edge_count)) return false;
            }
        }
        return true;
    }

    /// Polytope view of an aligned box for face/edge clipping (points, planes, box topology).
    fn materialize(a: *const Poly, store: *[24]f32, planes: *[24]f32) Poly {
        var p = a.*;
        for (0..8) |i| {
            store[i * 3] = if (i & 1 == 0) a.min_x else a.max_x;
            store[i * 3 + 1] = if (i & 2 == 0) a.min_y else a.max_y;
            store[i * 3 + 2] = if (i & 4 == 0) a.min_z else a.max_z;
        }
        const offsets = [_]f32{ -a.min_x, a.max_x, -a.min_y, a.max_y, -a.min_z, a.max_z };
        for (0..6) |f| {
            planes[f * 4] = BOX_NORMALS[f * 3];
            planes[f * 4 + 1] = BOX_NORMALS[f * 3 + 1];
            planes[f * 4 + 2] = BOX_NORMALS[f * 3 + 2];
            planes[f * 4 + 3] = offsets[f];
        }
        p.pts = store;
        p.n = 8;
        p.planes = planes;
        p.face_count = 6;
        p.face_start = &BOX_START;
        p.loops = &BOX_LOOPS;
        p.edges = @constCast(&UNIT_AXES);
        p.edge_count = 3;
        return p;
    }

    fn face(self: *Narrow, a0: *const Poly, b: *const Poly, face_of_a: bool, nx: f32, ny: f32, nz: f32, margin: f32, out: *Out) void {
        var store: [24]f32 = undefined;
        var planes: [24]f32 = undefined;
        const box = if (a0.aligned) materialize(a0, &store, &planes) else a0.*;
        const a = &box;
        const reference = if (face_of_a) a else b;
        const incident = if (face_of_a) b else a;
        const rx = if (face_of_a) nx else -nx;
        const ry = if (face_of_a) ny else -ny;
        const rz = if (face_of_a) nz else -nz;
        const ref_face = extremeFace(reference, rx, ry, rz, 1);
        const inc_face = extremeFace(incident, rx, ry, rz, -1);
        const fx = reference.planes[ref_face * 4];
        const fy = reference.planes[ref_face * 4 + 1];
        const fz = reference.planes[ref_face * 4 + 2];
        const fd = reference.planes[ref_face * 4 + 3];
        var n: usize = 0;
        const start: usize = @intCast(incident.face_start[inc_face]);
        const end: usize = @intCast(incident.face_start[inc_face + 1]);
        const ref_start: usize = @intCast(reference.face_start[ref_face]);
        const ref_end: usize = @intCast(reference.face_start[ref_face + 1]);
        self.ensure(end - start + ref_end - ref_start);
        for (start..end) |k| {
            const v: usize = @intCast(incident.loops[k] * 3);
            self.polygon[n * 3] = incident.pts[v];
            self.polygon[n * 3 + 1] = incident.pts[v + 1];
            self.polygon[n * 3 + 2] = incident.pts[v + 2];
            n += 1;
        }
        var k = ref_start;
        while (k < ref_end and n > 0) : (k += 1) {
            const v0: usize = @intCast(reference.loops[k] * 3);
            const v1: usize = @intCast(reference.loops[if (k + 1 < ref_end) k + 1 else ref_start] * 3);
            const ex = reference.pts[v1] - reference.pts[v0];
            const ey = reference.pts[v1 + 1] - reference.pts[v0 + 1];
            const ez = reference.pts[v1 + 2] - reference.pts[v0 + 2];
            const ix = fy * ez - fz * ey;
            const iy = fz * ex - fx * ez;
            const iz = fx * ey - fy * ex;
            const offset = ix * reference.pts[v0] + iy * reference.pts[v0 + 1] + iz * reference.pts[v0 + 2];
            n = self.clip(n, ix, iy, iz, offset);
        }
        const sign: f32 = if (face_of_a) 1 else -1;
        for (0..n) |q| {
            const qx = self.polygon[q * 3];
            const qy = self.polygon[q * 3 + 1];
            const qz = self.polygon[q * 3 + 2];
            const sep = qx * fx + qy * fy + qz * fz - fd;
            if (sep > margin) continue;
            const half = sep * 0.5;
            out.add(qx - fx * half, qy - fy * half, qz - fz * half, fx * sign, fy * sign, fz * sign, sep, a.tag, b.tag);
        }
    }

    fn extremeFace(p: *const Poly, x: f32, y: f32, z: f32, side: f32) usize {
        var best: usize = 0;
        var best_dot: f32 = -MAX;
        for (0..p.face_count) |f| {
            const dot = side * (p.planes[f * 4] * x + p.planes[f * 4 + 1] * y + p.planes[f * 4 + 2] * z);
            if (dot > best_dot) {
                best_dot = dot;
                best = f;
            }
        }
        return best;
    }

    fn clip(self: *Narrow, n: usize, ix: f32, iy: f32, iz: f32, offset: f32) usize {
        var count: usize = 0;
        for (0..n) |k| {
            const following = if (k + 1 < n) k + 1 else 0;
            const ax = self.polygon[k * 3];
            const ay = self.polygon[k * 3 + 1];
            const az = self.polygon[k * 3 + 2];
            const bx = self.polygon[following * 3];
            const by = self.polygon[following * 3 + 1];
            const bz = self.polygon[following * 3 + 2];
            const da = ax * ix + ay * iy + az * iz - offset;
            const db = bx * ix + by * iy + bz * iz - offset;
            if (da >= 0) {
                self.clipped[count * 3] = ax;
                self.clipped[count * 3 + 1] = ay;
                self.clipped[count * 3 + 2] = az;
                count += 1;
            }
            if ((da < 0) != (db < 0)) {
                const t = da / (da - db);
                self.clipped[count * 3] = ax + (bx - ax) * t;
                self.clipped[count * 3 + 1] = ay + (by - ay) * t;
                self.clipped[count * 3 + 2] = az + (bz - az) * t;
                count += 1;
            }
        }
        const swap = self.polygon;
        self.polygon = self.clipped;
        self.clipped = swap;
        return count;
    }

    fn ensure(self: *Narrow, points: usize) void {
        const needed = (points + 2) * 3;
        if (self.polygon.len < needed) {
            self.polygon = self.arena.alloc(f32, needed * 2);
            self.clipped = self.arena.alloc(f32, needed * 2);
        }
    }

    fn edge(self: *Narrow, a0: *const Poly, b: *const Poly, edge_a: usize, edge_b: usize, nx: f32, ny: f32, nz: f32, sep: f32, out: *Out) void {
        _ = self;
        var store: [24]f32 = undefined;
        var planes: [24]f32 = undefined;
        const box = if (a0.aligned) materialize(a0, &store, &planes) else a0.*;
        const a = &box;
        const segment_a = supportEdge(a, a.edges[edge_a * 3], a.edges[edge_a * 3 + 1], a.edges[edge_a * 3 + 2], nx, ny, nz);
        const segment_b = supportEdge(b, b.edges[edge_b * 3], b.edges[edge_b * 3 + 1], b.edges[edge_b * 3 + 2], -nx, -ny, -nz);
        if (segment_a < 0 or segment_b < 0) return;
        const sa: usize = @intCast(segment_a);
        const sb: usize = @intCast(segment_b);
        const a_0: usize = @intCast(a.loops[sa] * 3);
        const a_1: usize = @intCast(a.loops[next(a, sa)] * 3);
        const b_0: usize = @intCast(b.loops[sb] * 3);
        const b_1: usize = @intCast(b.loops[next(b, sb)] * 3);
        const px = a.pts[a_0];
        const py = a.pts[a_0 + 1];
        const pz = a.pts[a_0 + 2];
        const ux = a.pts[a_1] - px;
        const uy = a.pts[a_1 + 1] - py;
        const uz = a.pts[a_1 + 2] - pz;
        const qx = b.pts[b_0];
        const qy = b.pts[b_0 + 1];
        const qz = b.pts[b_0 + 2];
        const vx = b.pts[b_1] - qx;
        const vy = b.pts[b_1 + 1] - qy;
        const vz = b.pts[b_1 + 2] - qz;
        const wx = px - qx;
        const wy = py - qy;
        const wz = pz - qz;
        const uu = ux * ux + uy * uy + uz * uz;
        const uv = ux * vx + uy * vy + uz * vz;
        const vv = vx * vx + vy * vy + vz * vz;
        const uw = ux * wx + uy * wy + uz * wz;
        const vw = vx * wx + vy * wy + vz * wz;
        const denominator = uu * vv - uv * uv;
        var s: f32 = if (denominator > 1.0e-9) clamp01((uv * vw - vv * uw) / denominator) else 0;
        const t: f32 = if (vv > 1.0e-9) clamp01((uv * s + vw) / vv) else 0;
        s = if (uu > 1.0e-9) clamp01((uv * t - uw) / uu) else 0;
        const cx = (px + ux * s + qx + vx * t) * 0.5;
        const cy = (py + uy * s + qy + vy * t) * 0.5;
        const cz = (pz + uz * s + qz + vz * t) * 0.5;
        out.add(cx, cy, cz, nx, ny, nz, sep, a.tag, b.tag);
    }

    fn supportEdge(p: *const Poly, dx: f32, dy: f32, dz: f32, x: f32, y: f32, z: f32) i32 {
        var best: i32 = -1;
        var best_dot: f32 = -MAX;
        for (0..p.face_count) |f| {
            const fs: usize = @intCast(p.face_start[f]);
            const end: usize = @intCast(p.face_start[f + 1]);
            for (fs..end) |k| {
                const v0: usize = @intCast(p.loops[k] * 3);
                const v1: usize = @intCast(p.loops[if (k + 1 < end) k + 1 else fs] * 3);
                const ex = p.pts[v1] - p.pts[v0];
                const ey = p.pts[v1 + 1] - p.pts[v0 + 1];
                const ez = p.pts[v1 + 2] - p.pts[v0 + 2];
                const length = @sqrt(ex * ex + ey * ey + ez * ez);
                if (length < PARALLEL or @abs(ex * dx + ey * dy + ez * dz) < 0.999 * length) continue;
                const dot = (p.pts[v0] + p.pts[v1]) * x + (p.pts[v0 + 1] + p.pts[v1 + 1]) * y + (p.pts[v0 + 2] + p.pts[v1 + 2]) * z;
                if (dot > best_dot) {
                    best_dot = dot;
                    best = @intCast(k);
                }
            }
        }
        return best;
    }

    fn next(p: *const Poly, k: usize) usize {
        for (0..p.face_count) |f| {
            const fs: usize = @intCast(p.face_start[f]);
            const end: usize = @intCast(p.face_start[f + 1]);
            if (k >= fs and k < end) return if (k + 1 < end) k + 1 else fs;
        }
        @panic("narrow: loop index");
    }

    fn clamp01(v: f32) f32 {
        return if (v < 0) 0 else if (v > 1) 1 else v;
    }
};

// ---------------------------------------------------------------------------------------------- batch

pub const Context = struct {
    arena: Arena = .{},
};

/// Reader over the input words.
const In = struct {
    words: [*]const u32,
    at: usize = 0,

    fn int(self: *In) i32 {
        const v: i32 = @bitCast(self.words[self.at]);
        self.at += 1;
        return v;
    }

    fn float(self: *In) f32 {
        const v: f32 = @bitCast(self.words[self.at]);
        self.at += 1;
        return v;
    }

    fn floats(self: *In, n: usize) []f32 {
        const out: [*]f32 = @ptrCast(@constCast(self.words + self.at));
        self.at += n;
        return out[0..n];
    }

    fn ints(self: *In, n: usize) []const i32 {
        const out: [*]const i32 = @ptrCast(self.words + self.at);
        self.at += n;
        return out[0..n];
    }
};

/// `NarrowNative.worldContacts`: returns contacts written (> capacity => caller grows out and repeats).
pub fn worldContacts(ctx: *Context, input: [*]const u32, output: [*]u32, capacity: usize) usize {
    const arena = &ctx.arena;
    arena.reset();
    var in = In{ .words = input };
    const shape_count: usize = @intCast(in.int());
    const box_count: usize = @intCast(in.int());
    const tri_count: usize = @intCast(in.int());
    const margin = in.float();
    const depth = in.float();
    const cull = in.int() != 0;
    const env = in.floats(6)[0..6];
    const shapes = arena.alloc(Poly, shape_count);
    const poses = arena.alloc(Pose, shape_count);
    var reach = Poly{ .min_x = MAX, .min_y = MAX, .min_z = MAX, .max_x = -MAX, .max_y = -MAX, .max_z = -MAX };
    for (shapes, poses, 0..) |*s, *pose, i| {
        const handle = @as(u64, in.words[in.at]) | @as(u64, in.words[in.at + 1]) << 32;
        in.at += 2;
        const f = in.floats(11);
        const q = [4]f32{ f[3], f[4], f[5], f[6] };
        const hull: ?*const Hull = if (handle == 0) null else @ptrFromInt(@as(usize, @intCast(handle)));
        pose.* = .{ .hull = hull, .rotation = Rotation.of(q[0], q[1], q[2], q[3]), .e = .{ f[7], f[8], f[9] } };
        s.* = .{ .tag = @intCast(i) };
        if (cull and !(if (hull) |h| hullReaches(env, margin, h, &pose.rotation, q, f[0], f[1], f[2]) else reaches(env, margin, f[0], f[1], f[2], f[10], f[10], f[10]))) continue;
        posePoints(s, pose, f[0], f[1], f[2], arena);
        reach.min_x = javaMin(reach.min_x, s.min_x);
        reach.min_y = javaMin(reach.min_y, s.min_y);
        reach.min_z = javaMin(reach.min_z, s.min_z);
        reach.max_x = javaMax(reach.max_x, s.max_x);
        reach.max_y = javaMax(reach.max_y, s.max_y);
        reach.max_z = javaMax(reach.max_z, s.max_z);
    }
    const boxes = in.floats(box_count * 6);
    const tris = in.floats(tri_count * 13);
    const fills = arena.alloc(Poly, tri_count);
    const stores = arena.alloc(PrismStore, tri_count);
    const fill_source = arena.alloc(i32, tri_count);
    var fill_count: usize = 0;
    var t0: usize = 0;
    while (t0 < tri_count) : (t0 += W) {
        var bounds: [6][W]f32 = undefined;
        var bits = prismMeets(tris[t0 * 13 ..], @min(W, tri_count - t0), depth, &reach, margin, &bounds);
        while (bits != 0) : (bits &= bits - 1) {
            const l = @ctz(bits);
            const c = t0 + l;
            // Built on first pair (`prism`); bounds equal `bound()` (javaMin/javaMax order-free).
            fills[fill_count] = .{ .min_x = bounds[0][l], .min_y = bounds[1][l], .min_z = bounds[2][l], .max_x = bounds[3][l], .max_y = bounds[4][l], .max_z = bounds[5][l], .tag = @bitCast(tris[c * 13 + 12]) };
            fill_source[fill_count] = @intCast(c);
            fill_count += 1;
        }
    }
    var narrow = Narrow{ .arena = arena };
    var out = Out{ .buf = output, .capacity = capacity };
    var box: Poly = undefined;
    const BoxSoa = struct { lx: []f32, ly: []f32, lz: []f32, hx: []f32, hy: []f32, hz: []f32 };
    const soaBounds = struct {
        fn make(a: *Arena, count: usize) BoxSoa {
            const padded = (count + W - 1) / W * W;
            const r = BoxSoa{ .lx = a.alloc(f32, padded), .ly = a.alloc(f32, padded), .lz = a.alloc(f32, padded), .hx = a.alloc(f32, padded), .hy = a.alloc(f32, padded), .hz = a.alloc(f32, padded) };
            for (count..padded) |i| {
                r.lx[i] = MAX;
                r.ly[i] = MAX;
                r.lz[i] = MAX;
                r.hx[i] = -MAX;
                r.hy[i] = -MAX;
                r.hz[i] = -MAX;
            }
            return r;
        }
        /// Bits of 8 entries from `at` meeting shape bounds (same predicate as `Poly.boundsMeet`).
        fn meet(r: BoxSoa, at: usize, shape: *const Poly, slack: f32) u8 {
            const sx: V = @splat(shape.max_x + slack);
            const sy: V = @splat(shape.max_y + slack);
            const sz: V = @splat(shape.max_z + slack);
            const m: V = @splat(slack);
            const lx: V = r.lx[at..][0..W].*;
            const ly: V = r.ly[at..][0..W].*;
            const lz: V = r.lz[at..][0..W].*;
            const hx: V = r.hx[at..][0..W].*;
            const hy: V = r.hy[at..][0..W].*;
            const hz: V = r.hz[at..][0..W].*;
            const k = @intFromBool(lx <= sx) & @intFromBool(@as(V, @splat(shape.min_x)) <= hx + m) &
                @intFromBool(ly <= sy) & @intFromBool(@as(V, @splat(shape.min_y)) <= hy + m) &
                @intFromBool(lz <= sz) & @intFromBool(@as(V, @splat(shape.min_z)) <= hz + m);
            return @bitCast(k);
        }
    };
    const bb = soaBounds.make(arena, box_count);
    for (0..box_count) |i| {
        bb.lx[i] = boxes[i * 6];
        bb.ly[i] = boxes[i * 6 + 1];
        bb.lz[i] = boxes[i * 6 + 2];
        bb.hx[i] = boxes[i * 6 + 3];
        bb.hy[i] = boxes[i * 6 + 4];
        bb.hz[i] = boxes[i * 6 + 5];
    }
    const fb = soaBounds.make(arena, fill_count);
    for (fills[0..fill_count], 0..) |*fill, f| {
        fb.lx[f] = fill.min_x;
        fb.ly[f] = fill.min_y;
        fb.lz[f] = fill.min_z;
        fb.hx[f] = fill.max_x;
        fb.hy[f] = fill.max_y;
        fb.hz[f] = fill.max_z;
    }
    for (shapes, poses, 0..) |*shape, *pose, s| {
        if (shape.n == 0) continue;
        out.shape = @intCast(s);
        var box0: usize = 0;
        while (box0 < box_count) : (box0 += W) {
            var bits = soaBounds.meet(bb, box0, shape, margin);
            while (bits != 0) : (bits &= bits - 1) {
                const i = box0 + @ctz(bits);
                const b = boxes[i * 6 ..][0..6];
                poseRest(shape, pose, arena);
                alignedBox(&box, b[0], b[1], b[2], b[3], b[4], b[5], @intCast(i));
                out.source = @intCast(i);
                narrow.collide(&box, shape, margin, &out);
            }
        }
        var f0: usize = 0;
        while (f0 < fill_count) : (f0 += W) {
            var bits = soaBounds.meet(fb, f0, shape, margin);
            while (bits != 0) : (bits &= bits - 1) {
                const f = f0 + @ctz(bits);
                const fill = &fills[f];
                if (fill.n == 0) {
                    const t = tris[@as(usize, @intCast(fill_source[f])) * 13 ..][0..13];
                    prism(fill, &stores[f], t[0..9], t[9], t[10], t[11], depth);
                }
                poseRest(shape, pose, arena);
                out.source = -1 - fill_source[f];
                narrow.collide(fill, shape, margin, &out);
            }
        }
    }
    return out.count;
}

pub fn initTables(edge_faces: [*]const i32, fused: bool) void {
    use_fma = fused;
    @memcpy(&box_edge_faces, edge_faces[0..36]);
    for (0..6) |f| {
        box_planes[f * 4] = BOX_NORMALS[f * 3];
        box_planes[f * 4 + 1] = BOX_NORMALS[f * 3 + 1];
        box_planes[f * 4 + 2] = BOX_NORMALS[f * 3 + 2];
        box_planes[f * 4 + 3] = 0;
    }
}
