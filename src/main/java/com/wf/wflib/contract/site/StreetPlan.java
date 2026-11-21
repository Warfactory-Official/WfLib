package com.wf.wflib.contract.site;

import com.wf.wflib.contract.site.BasePlan.Piece;
import com.wf.wflib.contract.site.BasePlan.Role;
import com.wf.wflib.contract.site.BasePlan.Slot;
import com.wf.wflib.contract.site.BasePlan.Type;
import com.wf.wflib.contract.site.BasePlan.Wall;
import com.wf.wflib.contract.site.BasePlan.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A walled base laid out like a village: streets first, then buildings along them.
 * <ul>
 * <li>a main road runs gate to gate along the base's axis, a cross road meets it in a plaza at the middle, and both run out
 * through gates in the wall, so the base has an opening on every side; a service lane runs round the inside of the wall;</li>
 * <li>each quarter between the roads is a block with two rows of buildings back to back: the front row faces the main road,
 * the back row the service lane; every building's door is on its street and a path joins it to the road;</li>
 * <li>the command post takes the first lot off the plaza, vehicle bays the lots by the gates, quarters and the mess the front
 * rows, stores, workshops and power the back rows;</li>
 * <li>towers stand on the corners and, on a fortress, either side of the main gates; all of one design.</li>
 * </ul>
 * Coordinates: {@code u} runs along the main road, {@code v} across it; {@code axis} says which of x and z {@code u} is.
 */
final class StreetPlan {
    /** Half-widths: the main road and the cross road are five wide, the lane three. */
    private static final int ROAD = 2, CROSS = 2;
    /** Between a road's edge and a building's face: a path cell and the apron. */
    private static final int SETBACK = 2;
    /** Between two buildings in a row. */
    private static final int GAP = 2;

    private final BaseKind kind;
    private final int cx, cz, padY, axis, half, lane, plaza;
    private final Random rng;
    private final List<Piece> pieces = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Slot> vehicles = new ArrayList<>();
    private final List<int[]> taken = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int[] cache;

    /** A row of lots along a street: from {@code start} to {@code end} in {@code u} (signed by {@code su}), faces at {@code face} in {@code |v|} on side {@code sv}, {@code depth} deep away from the street. */
    private static final class Row {
        final int su, sv, face, depth, end;
        final boolean front;
        int near, far;

        Row(int su, int sv, boolean front, int face, int depth, int start, int end) {
            this.su = su;
            this.sv = sv;
            this.front = front;
            this.face = face;
            this.depth = depth;
            this.near = start;
            this.far = end;
            this.end = end;
        }
    }

    private StreetPlan(BaseKind kind, int cx, int cz, int padY, Random rng) {
        this.kind = kind;
        this.cx = cx;
        this.cz = cz;
        this.padY = padY;
        this.rng = rng;
        this.axis = rng.nextInt(2);
        this.half = kind.wall;
        this.lane = half - 4;                        // the lane's inner edge; it runs to half - 2, a grass strip under the wall
        this.plaza = kind == BaseKind.FORTRESS ? 6 : 5;
    }

    static BasePlan plan(BaseKind kind, int cx, int cz, int padY, Random rng, Terrain ground) {
        StreetPlan s = new StreetPlan(kind, cx, cz, padY, rng);
        Wall wall = new Wall(kind.wall, kind.wallHeight, s.axis, rng.nextInt(2) == 0 ? Wall.CONCRETE : Wall.HESCO);
        s.streets();
        s.towers();
        s.gates(ground);
        s.rows();
        s.buildings();
        s.lights();
        s.zones.add(new Zone(Role.WALL_PATROL, cx, padY, cz, s.half - 2, BaseLayout.square(cx, cz, s.half - 3, i -> padY), 2.0));
        return new BasePlan(kind, cx, cz, padY, wall, s.pieces, BaseLayout.outside(kind, cx, cz, padY, rng, ground, s.pieces, s.zones), s.vehicles, s.cache);
    }

    // ---------------------------------------------------------------- coordinates

    private int x(int u, int v) {
        return cx + (axis == 0 ? v : u);
    }

    private int z(int u, int v) {
        return cz + (axis == 0 ? u : v);
    }

    /** The facing (0 north, 1 east, 2 south, 3 west) that points along {@code +v} when {@code sign} is 1, {@code -v} when -1. */
    private int towardV(int sign) {
        return axis == 0 ? (sign > 0 ? 1 : 3) : (sign > 0 ? 2 : 0);
    }

    /** The cells {@code u0..u1} by {@code v0..v1} (any order) as a piece of {@code type}: centre and extents in x and z. */
    private Piece rect(Type type, int u0, int u1, int v0, int v1, int facing, int height) {
        int ua = Math.min(u0, u1), ub = Math.max(u0, u1), va = Math.min(v0, v1), vb = Math.max(v0, v1);
        int lu = ub - ua + 1, lv = vb - va + 1;
        int w = axis == 0 ? lv : lu, d = axis == 0 ? lu : lv;
        int xa = Math.min(x(ua, va), x(ub, vb)), za = Math.min(z(ua, va), z(ub, vb));
        return new Piece(type, xa + w / 2, padY, za + d / 2, w, d, facing, height, rng.nextInt(1 << 16));
    }

    private boolean free(Piece p) {
        return !BaseLayout.clash(taken, p.x(), p.z(), p.w() / 2 + 1, p.d() / 2 + 1);
    }

    private void claim(Piece p) {
        BaseLayout.take(taken, p.x(), p.z(), p.w(), p.d());
    }

    // ---------------------------------------------------------------- the plan

    /** The lane round the inside of the wall, the cross road and the main road (gate to gate, and on past the gates), the plaza. */
    private void streets() {
        int far = half - 2;
        pieces.add(rect(Type.ROAD, -far, far, -far, -lane, 3, 0));
        pieces.add(rect(Type.ROAD, -far, far, lane, far, 3, 0));
        pieces.add(rect(Type.ROAD, -far, -lane, -far, far, 3, 0));
        pieces.add(rect(Type.ROAD, lane, far, -far, far, 3, 0));
        pieces.add(rect(Type.ROAD, -CROSS, CROSS, -half - 6, half + 6, 1, 0));
        pieces.add(rect(Type.ROAD, -half - 8, half + 8, -ROAD, ROAD, 0, 0));
        pieces.add(rect(Type.ROAD, -plaza, plaza, -plaza, plaza, 2, 0));
        // the roads are kept clear of buildings: the lane and the plaza, the strips of the two roads
        for (Piece p : pieces) if (p.type() == Type.ROAD && p.facing() != 3) claim(p);
        Piece mast = rect(Type.MAST, 0, 0, 0, 0, 0, 12);
        pieces.add(mast);
    }

    /** The corners, and on a fortress either side of each main gate; every tower's door faces into the base. */
    private void towers() {
        List<int[]> at = new ArrayList<>();                                         // {u, v}
        for (int su = -1; su <= 1; su += 2) for (int sv = -1; sv <= 1; sv += 2) at.add(new int[]{su * half, sv * half});
        if (kind.towers > 4) {
            for (int su = -1; su <= 1; su += 2) for (int sv = -1; sv <= 1; sv += 2) at.add(new int[]{su * half, sv * (ROAD + 5)});
        }
        int towerH = kind.wallHeight + 5, style = rng.nextInt(1 << 16);
        for (int[] t : at.subList(0, Math.min(at.size(), kind.towers))) {
            int fu = -Integer.signum(t[0]);                                          // inward along u: where the door looks
            int facing = axis == 0 ? (fu > 0 ? 2 : 0) : (fu > 0 ? 1 : 3);
            Piece p = new Piece(Type.TOWER, x(t[0], t[1]), padY, z(t[0], t[1]), 5, 5, facing, towerH, style);
            pieces.add(p);
            claim(p);
            zones.add(new Zone(Role.TOWER, p.x(), padY + towerH, p.z(), 1, List.of(), 1.0));
        }
    }

    /** A guard booth inside each main gate and a funnel of barriers outside it. */
    private void gates(Terrain ground) {
        for (int su = -1; su <= 1; su += 2) {
            zones.add(new Zone(Role.GATE, x(su * (half - 4), 0), padY, z(su * (half - 4), 0), 3, List.of(), 1.5));
            Piece booth = rect(Type.CHECKPOINT, su * (half - 6) - 1, su * (half - 6) + 1, ROAD + 2, ROAD + 4, towardV(-1), 4);
            pieces.add(booth);
            claim(booth);
            for (int sv = -1; sv <= 1; sv += 2) {
                int u = su * (half + 6), v = sv * (ROAD + 3);
                int x = x(u, v), z = z(u, v);
                if (!BaseLayout.standable(ground, x, z)) continue;
                int y = BaseLayout.standing(ground, padY, cx, cz, kind.radius, x, z);
                Piece wing = rect(Type.BARRIER, u - 3, u + 3, v, v, axis, 2);
                pieces.add(new Piece(Type.BARRIER, wing.x(), y, wing.z(), wing.w(), wing.d(), wing.w() == 1 ? 0 : 1, 2, 0));
            }
        }
    }

    /** Two rows in each quarter: the front row on the main road, the back row on the lane. */
    private void rows() {
        int frontFace = ROAD + 1 + SETBACK, backFace = lane - 1 - SETBACK;
        int room = backFace - frontFace + 1 - 3;                                    // three between the rows: two aprons and a gap
        int frontDepth = room / 2, backDepth = room - frontDepth;
        for (int su = -1; su <= 1; su += 2) {
            for (int sv = -1; sv <= 1; sv += 2) {
                rows.add(new Row(su, sv, true, frontFace, frontDepth, plaza + 2, lane - 2));
                rows.add(new Row(su, sv, false, backFace, backDepth, CROSS + 2, lane - 2));
            }
        }
    }

    /** {@code type}, {@code len} along the street and {@code depth} away from it, at the next lot of {@code row} from its near or its far end; null when it does not fit. */
    private Piece lot(Row row, Type type, int len, int depth, boolean fromFar, int height) {
        if (depth > row.depth) return null;
        int doorFacing = towardV(row.front ? -row.sv : row.sv);
        int v0 = row.sv * row.face, v1 = row.sv * (row.front ? row.face + depth - 1 : row.face - depth + 1);
        for (int tries = 0; tries < 40; tries++) {
            int a = fromFar ? row.far - len + 1 : row.near, b = a + len - 1;
            if (a < row.near || b > row.far) return null;
            Piece p = rect(type, row.su * a, row.su * b, v0, v1, doorFacing, height);
            if (free(p)) {
                if (fromFar) row.far = a - GAP - 1;
                else row.near = b + GAP + 1;
                claim(p);
                pieces.add(p);
                path(p, row);
                return p;
            }
            if (fromFar) row.far--;
            else row.near++;
        }
        return null;
    }

    /** A path three wide from the middle of {@code p}'s street face to the road. */
    private void path(Piece p, Row row) {
        int mid = axis == 0 ? p.z() - cz : p.x() - cx;                            // the door's u
        int v0 = row.sv * (row.front ? ROAD + 1 : lane - 1), v1 = row.sv * (row.front ? row.face - 1 : row.face + 1);
        pieces.add(rect(Type.PATH, mid - 1, mid + 1, v0, v1, 0, 0));
    }

    /** Fills the rows: the command post by the plaza, bays by the gates, then the rest by where each belongs. */
    private void buildings() {
        boolean fort = kind == BaseKind.FORTRESS;
        Row first = rows.get(rng.nextInt(4) * 2);                                    // one of the four front rows
        Piece cmd = lot(first, Type.COMMAND, fort ? 13 : 11, fort ? 9 : 7, false, fort ? 5 : 4);
        zones.add(new Zone(Role.COMMAND, cmd.x(), padY, cmd.z(), Math.min(cmd.w(), cmd.d()) / 2 - 1, List.of(), 3.0));
        cache = new int[]{cmd.x() - cmd.w() / 2 + 2, padY, cmd.z() - cmd.d() / 2 + 2};

        List<Row> front = rows.stream().filter(r -> r.front).toList(), back = rows.stream().filter(r -> !r.front).toList();
        for (int i = 0; i < kind.bays; i++) {
            Piece bay = null;
            for (int k = 0; k < front.size() && bay == null; k++) bay = lot(front.get((i * 2 + k) % front.size()), Type.BAY, 9, 7, true, 4);
            if (bay != null) vehicles.add(new Slot(bay.x(), padY, bay.z(), (90f * bay.facing() + 180f) % 360f, i == 0 ? "light" : "armour"));
        }
        List<Object[]> fronts = new ArrayList<>(), backs = new ArrayList<>();      // {type, len, depth, height}
        for (int i = 0; i < kind.barracks; i++) fronts.add(new Object[]{Type.BARRACKS, 13, 7, 4});
        fronts.add(new Object[]{Type.MESS, 11, 7, 4});
        backs.add(new Object[]{Type.ARMORY, 7, 5, 4});
        backs.add(new Object[]{Type.GENERATOR, 7, 5, 4});
        backs.add(new Object[]{Type.CONTAINERS, 9, 7, 4});
        backs.add(new Object[]{Type.YARD, 7, 6, 1});
        backs.add(new Object[]{Type.DEPOT, 7, 5, 3});
        for (int i = 0; i < kind.bunkers; i++) backs.add(new Object[]{Type.BUNKER, 7, 5, 3});
        if (!fort) backs.add(new Object[]{Type.HELIPAD, 7, 7, 1});                // last: a compound has a small pad (or a silo) when there is room
        if (fort) {
            backs.add(0, new Object[]{Type.HELIPAD, 11, 11, 1});
            backs.add(new Object[]{Type.CONTAINERS, 9, 7, 4});
            backs.add(new Object[]{Type.GENERATOR, 7, 5, 4});
        }
        place(fronts, front, back);
        place(backs, back, front);
    }

    /** Each item into the first row (round the rows, from a different one each time) that has room; the other rows when none of {@code prefer} has. */
    private void place(List<Object[]> items, List<Row> prefer, List<Row> fallback) {
        int start = rng.nextInt(prefer.size());
        for (int n = 0; n < items.size(); n++) {
            Object[] o = items.get(n);
            Piece put = null;
            List<Row> order = new ArrayList<>();
            for (int k = 0; k < prefer.size(); k++) order.add(prefer.get((start + n + k) % prefer.size()));
            order.addAll(fallback);
            for (Row r : order) {
                put = lot(r, (Type) o[0], (int) o[1], (int) o[2], false, (int) o[3]);
                if (put != null) break;
            }
            if (put != null && o[0] == Type.BARRACKS && (n == 0 || kind == BaseKind.FORTRESS)) {
                zones.add(new Zone(Role.RESERVE, put.x(), padY, put.z(), 2, List.of(), 2.0));
            }
        }
    }

    /** Lamp posts at the plaza's corners and along the main road, on the strip between the road and the lots, clear of the paths. */
    private void lights() {
        List<int[]> at = new ArrayList<>();
        for (int su = -1; su <= 1; su += 2) for (int sv = -1; sv <= 1; sv += 2) at.add(new int[]{su * (plaza + 1), sv * (plaza + 1)});
        for (int u = plaza + 9; u < lane - 2; u += 10) {
            for (int su = -1; su <= 1; su += 2) at.add(new int[]{su * u, (u / 10 % 2 == 0 ? 1 : -1) * (ROAD + 1)});
        }
        for (int[] a : at) {
            int x = x(a[0], a[1]), z = z(a[0], a[1]);
            boolean onPath = pieces.stream().anyMatch(p -> p.type() == Type.PATH && Math.abs(p.x() - x) <= p.w() / 2 + 1 && Math.abs(p.z() - z) <= p.d() / 2 + 1);
            if (!onPath) pieces.add(new Piece(Type.LIGHT, x, padY, z, 1, 1, 0, 5));
        }
    }
}
