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
 * Lays a base out. The rules, so that a base reads as a place people live in and is worth planning an assault on:
 * <ul>
 * <li>a perimeter wall (concrete, hesco or both) with gates on opposite sides, a guard booth and hesco wings at each, towers on the corners and flanking the gates; no wall for a small post (sandbags and a watch tower instead);</li>
 * <li>the command building near the middle, barracks and the depot behind it, a vehicle bay inside a gate, a radio mast, floodlights, and a drawn-from-a-pool set of working buildings (mess, armoury, generator, containers, motor pool, aid station, landing pad);</li>
 * <li>nothing overlaps and everything keeps a margin so soldiers can walk between buildings;</li>
 * <li>outside the wall: machine-gun nests covering the gates and trench runs that make an approach a killing ground;</li>
 * <li>two patrol loops (inside the wall and outside it) and small outposts out in the approaches, each with their own beat;</li>
 * <li>overwatch (marksmen) on towers, a reserve in the barracks, guards at the gates, the command post held by the best.</li>
 * </ul>
 * Pure: it reads the ground only through {@link Terrain}, so it runs under a unit test.
 */
public final class BaseLayout {
    private static final int MARGIN = 1;

    private BaseLayout() {
    }

    public static BasePlan plan(BaseKind kind, int cx, int cz, int padY, Random rng, Terrain ground) {
        List<Piece> pieces = new ArrayList<>();
        List<Zone> zones = new ArrayList<>();
        List<Slot> vehicles = new ArrayList<>();
        List<int[]> taken = new ArrayList<>();
        boolean walled = kind.wall > 0;
        int axis = rng.nextInt(2);
        Wall wall = walled ? new Wall(kind.wall, kind.wallHeight, axis, rng.nextInt(3)) : null;
        int inner = walled ? kind.wall - 3 : kind.radius - 5;

        // the command building holds the middle; a small post gets a hut
        int cs = kind == BaseKind.FORTRESS ? 11 : walled ? 9 : 5;
        int cmx = cx + rng.nextInt(5) - 2, cmz = cz + rng.nextInt(5) - 2;
        pieces.add(new Piece(Type.COMMAND, cmx, padY, cmz, cs, cs, rng.nextInt(4), 4, rng.nextInt(1 << 16)));
        take(taken, cmx, cmz, cs, cs);
        zones.add(new Zone(Role.COMMAND, cmx, padY, cmz, cs / 2 - 1, List.of(), 3.0));
        int[] cache = {cmx - cs / 2 + 2, padY, cmz - cs / 2 + 2};

        // towers
        int towerH = kind == BaseKind.OUTPOST ? 7 : kind.wallHeight + 4;
        List<int[]> towerAt = new ArrayList<>();
        if (walled) {
            int w = kind.wall;
            int[][] corners = {{-w, -w}, {w, -w}, {w, w}, {-w, w}};
            for (int[] c : corners) if (towerAt.size() < kind.towers) towerAt.add(new int[]{cx + c[0], cz + c[1]});
            int flank = 6;                                           // either side of each gate
            for (int side = 0; side < 2 && towerAt.size() < kind.towers; side++) {
                for (int s = -1; s <= 1 && towerAt.size() < kind.towers; s += 2) {
                    int gx = axis == 0 ? s * flank : (side == 0 ? -w : w), gz = axis == 0 ? (side == 0 ? -w : w) : s * flank;
                    towerAt.add(new int[]{cx + gx, cz + gz});
                }
            }
        } else {
            double a = rng.nextDouble() * Math.PI * 2;
            int[] spot = null;
            for (int tries = 0; tries < 16 && spot == null; tries++, a += Math.PI / 8) {      // a bearing clear of the hut
                int tx = cx + (int) Math.round(Math.cos(a) * 9), tz = cz + (int) Math.round(Math.sin(a) * 9);
                boolean clear = true;
                for (int[] t : taken) clear &= !(tx + 1 + MARGIN >= t[0] && tx - 1 - MARGIN <= t[2] && tz + 1 + MARGIN >= t[1] && tz - 1 - MARGIN <= t[3]);
                if (clear) spot = new int[]{tx, tz};
            }
            if (spot == null) spot = new int[]{cx + (int) Math.round(Math.cos(a) * 9), cz + (int) Math.round(Math.sin(a) * 9)};
            towerAt.add(spot);
        }
        for (int[] t : towerAt) {
            int ts = kind == BaseKind.OUTPOST ? 3 : 5;
            pieces.add(new Piece(Type.TOWER, t[0], padY, t[1], ts, ts, 0, towerH, rng.nextInt(1 << 16)));
            take(taken, t[0], t[1], ts, ts);
            zones.add(new Zone(Role.TOWER, t[0], padY + towerH, t[1], 1, List.of(), 1.0));
        }

        // gates: guards just inside each
        if (walled) {
            for (int side = -1; side <= 1; side += 2) {
                int gx = axis == 0 ? 0 : side * (kind.wall - 3), gz = axis == 0 ? side * (kind.wall - 3) : 0;
                zones.add(new Zone(Role.GATE, cx + gx, padY, cz + gz, 3, List.of(), 1.5));
                // the guard booth stands inside the gate beside the lane; hesco wings funnel the approach outside it
                int bx = axis == 0 ? 5 : side * (kind.wall - 5), bz = axis == 0 ? side * (kind.wall - 5) : 5;
                take(taken, cx + (axis == 0 ? 0 : side * (kind.wall - 5)), cz + (axis == 0 ? side * (kind.wall - 5) : 0), axis == 0 ? 5 : 9, axis == 0 ? 9 : 5);   // the road in stays clear
                pieces.add(new Piece(Type.CHECKPOINT, cx + bx, padY, cz + bz, 3, 3, axis == 0 ? 3 : 0, 4, rng.nextInt(1 << 16)));
                take(taken, cx + bx, cz + bz, 3, 3);
                for (int lane = -1; lane <= 1; lane += 2) {
                    int wx = cx + (axis == 0 ? lane * 4 : side * (kind.wall + 5)), wz = cz + (axis == 0 ? side * (kind.wall + 5) : lane * 4);
                    if (!standable(ground, wx, wz)) continue;
                    int wy = standing(ground, padY, cx, cz, kind.radius, wx, wz);
                    pieces.add(new Piece(Type.BARRIER, wx, wy, wz, axis == 0 ? 1 : 7, axis == 0 ? 7 : 1, axis, 2, rng.nextInt(1 << 16)));
                }
            }
        }

        // vehicle bays first: they need the room, and a bay by a gate is where an alert sends the soldiers
        for (int i = 0; i < kind.bays; i++) {
            int[] at = pack(rng, taken, cx, cz, inner, 9, 7);
            if (at == null) continue;
            pieces.add(new Piece(Type.BAY, at[0], padY, at[1], 9, 7, 0, 4));
            vehicles.add(new Slot(at[0], padY, at[1], rng.nextInt(4) * 90f, i == 0 && kind != BaseKind.FORTRESS ? "light" : i == 0 ? "light" : "armour"));
        }
        // the first barracks, then the working buildings (they are the large ones and need the room), then the rest
        int barracks = walled ? kind.barracks : 2;
        for (int i = 0; i < Math.min(1, barracks); i++) barracks(pieces, zones, taken, rng, kind, walled, cx, cz, padY, inner, i);
        // what a garrison runs on, drawn from a pool so no two bases carry the same set: mess, armoury, power, containers, motor pool,
        // an aid station and, for a fortress, a landing pad
        List<Object[]> pool = new ArrayList<>();           // {type, w, d}
        if (walled) {
            pool.add(new Object[]{Type.MESS, 9, 8});
            pool.add(new Object[]{Type.ARMORY, 6, 5});
            pool.add(new Object[]{Type.GENERATOR, 6, 5});
            pool.add(new Object[]{Type.CONTAINERS, 9, 7});
            pool.add(new Object[]{Type.YARD, 7, 6});
            pool.add(new Object[]{Type.MEDICAL, 8, 6});
            if (kind == BaseKind.FORTRESS) {
                pool.add(new Object[]{Type.HELIPAD, 11, 11});
                pool.add(new Object[]{Type.CONTAINERS, 9, 7});
                pool.add(new Object[]{Type.GENERATOR, 6, 5});
            }
            java.util.Collections.shuffle(pool, rng);
        } else {
            pool.add(new Object[]{Type.YARD, 3, 3});
        }
        int want = kind == BaseKind.FORTRESS ? 6 : walled ? 3 : 1;
        for (Object[] o : pool) {
            if (want <= 0) break;
            int w = (int) o[1], d = (int) o[2];
            int[] at = pack(rng, taken, cx, cz, inner, w, d);
            if (at != null) {
                want--;
                pieces.add(new Piece((Type) o[0], at[0], padY, at[1], w, d, rng.nextInt(4), o[0] == Type.HELIPAD ? 1 : 4, rng.nextInt(1 << 16)));
            }
        }
        for (int i = 1; i < barracks; i++) barracks(pieces, zones, taken, rng, kind, walled, cx, cz, padY, inner, i);
        int[] depot = pack(rng, taken, cx, cz, inner, 7, 5);
        if (depot != null) pieces.add(new Piece(Type.DEPOT, depot[0], padY, depot[1], 7, 5, rng.nextInt(4), 3, rng.nextInt(1 << 16)));
        for (int i = 0; i < kind.bunkers; i++) {
            int[] at = pack(rng, taken, cx, cz, inner, 7, 5);
            if (at != null) pieces.add(new Piece(Type.BUNKER, at[0], padY, at[1], 7, 5, rng.nextInt(2) * 2, 3, rng.nextInt(1 << 16)));
        }
        int[] mast = pack(rng, taken, cx, cz, inner, 1, 1);
        if (mast != null) pieces.add(new Piece(Type.MAST, mast[0], padY, mast[1], 1, 1, 0, 12, rng.nextInt(1 << 16)));
        for (int i = 0; i < 4; i++) {
            int[] at = pack(rng, taken, cx, cz, inner, 1, 1);
            if (at != null) pieces.add(new Piece(Type.LIGHT, at[0], padY, at[1], 1, 1, 0, 5));
        }

        // outside: nests covering the approaches, trench runs
        int ringR = walled ? kind.wall + 10 : kind.radius - 2;
        double base = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < kind.nests; i++) {
            double a = base + i * Math.PI * 2 / kind.nests;
            int x = cx + (int) Math.round(Math.cos(a) * ringR), z = cz + (int) Math.round(Math.sin(a) * ringR);
            if (!standable(ground, x, z)) continue;
            int y = standing(ground, padY, cx, cz, kind.radius, x, z);
            int facing = (int) Math.floorMod(Math.round(Math.toDegrees(a) / 90.0) + 1, 4);
            pieces.add(new Piece(Type.NEST, x, y, z, 5, 3, facing, 2, rng.nextInt(1 << 16)));
            zones.add(new Zone(Role.NEST, x, y, z, 1, List.of(), 1.0));
        }
        for (int i = 0; i < kind.trenches; i++) {
            double a = base + Math.PI / kind.nests + i * Math.PI * 2 / Math.max(1, kind.trenches);
            int x = cx + (int) Math.round(Math.cos(a) * (ringR - 4)), z = cz + (int) Math.round(Math.sin(a) * (ringR - 4));
            if (!standable(ground, x, z)) continue;
            int y = standing(ground, padY, cx, cz, kind.radius, x, z);
            pieces.add(new Piece(Type.TRENCH, x, y, z, 14, 2, (int) Math.floorMod(Math.round(Math.toDegrees(a) / 90.0), 2) * 1, 2, rng.nextInt(1 << 16)));
        }

        // beats: inside the wall and outside it
        if (walled) {
            zones.add(new Zone(Role.WALL_PATROL, cx, padY, cz, kind.wall - 2, square(cx, cz, kind.wall - 3, i -> padY), 2.0));
            zones.add(new Zone(Role.OUTER_PATROL, cx, padY, cz, kind.wall + 10,
                    square(cx, cz, kind.wall + 8, i -> 0), 2.0));
        } else {
            zones.add(new Zone(Role.WALL_PATROL, cx, padY, cz, 10, circle(cx, cz, 8, 6, padY), 2.0));
        }

        // outposts out in the approaches
        double oa = rng.nextDouble() * Math.PI * 2;
        for (int i = 0, made = 0; i < kind.outposts * 4 && made < kind.outposts; i++) {
            double a = oa + made * Math.PI * 2 / Math.max(1, kind.outposts) + (i - made * 4) * 0.4;
            double r = kind.radius + 16 + rng.nextInt(14);
            int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            if (!standable(ground, x, z)) continue;
            int y = ground.ground(x, z);
            pieces.add(new Piece(Type.CABIN, x, y, z, 5, 4, rng.nextInt(4), 3, rng.nextInt(1 << 16)));
            pieces.add(new Piece(Type.NEST, x + 5, ground.available(x + 5, z) ? ground.ground(x + 5, z) : y, z, 5, 3, 0, 2, rng.nextInt(1 << 16)));
            pieces.add(new Piece(Type.YARD, x - 4, ground.available(x - 4, z) ? ground.ground(x - 4, z) : y, z, 3, 3, 0, 1, rng.nextInt(1 << 16)));
            List<int[]> loop = new ArrayList<>();
            for (int k = 0; k < 4; k++) {
                int px = x + (int) Math.round(Math.cos(k * Math.PI / 2) * 6), pz = z + (int) Math.round(Math.sin(k * Math.PI / 2) * 6);
                loop.add(new int[]{px, ground.available(px, pz) ? ground.ground(px, pz) : y, pz});
            }
            zones.add(new Zone(Role.OUTPOST, x, y, z, 8, loop, 1.5));
            made++;
        }

        // outer patrol heights come from the ground there
        List<Zone> fixed = new ArrayList<>();
        for (Zone z : zones) {
            if (z.role() == Role.OUTER_PATROL) {
                List<int[]> r = new ArrayList<>();
                for (int[] p : z.route()) r.add(new int[]{p[0], ground.available(p[0], p[2]) ? ground.ground(p[0], p[2]) : padY, p[2]});
                fixed.add(new Zone(z.role(), z.x(), z.y(), z.z(), z.radius(), r, z.weight()));
            } else {
                fixed.add(z);
            }
        }
        return new BasePlan(kind, cx, cz, padY, wall, pieces, fixed, vehicles, cache);
    }

    private static void barracks(List<Piece> pieces, List<Zone> zones, List<int[]> taken, Random rng, BaseKind kind, boolean walled, int cx, int cz, int padY, int inner, int i) {
        int w = walled ? 11 : 4, d = walled ? 7 : 5;
        int[] at = pack(rng, taken, cx, cz, inner, w, d);
        if (at == null) return;
        pieces.add(new Piece(walled ? Type.BARRACKS : Type.CABIN, at[0], padY, at[1], w, d, rng.nextInt(4), 4, rng.nextInt(1 << 16)));
        if (i == 0 || kind == BaseKind.FORTRESS) zones.add(new Zone(Role.RESERVE, at[0], padY, at[1], 2, List.of(), 2.0));
    }

    private static boolean standable(Terrain t, int x, int z) {
        return t.available(x, z) && t.surface(x, z) == Terrain.Surface.SOLID && !t.wet(x, z);
    }

    private static int standing(Terrain t, int padY, int cx, int cz, int footprint, int x, int z) {
        int dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz <= footprint * footprint ? padY : t.ground(x, z);
    }

    /** A route of eight points round a square of half-size {@code half}: corners and midpoints. {@code y} of 0 is filled in from the ground later. */
    private static List<int[]> square(int cx, int cz, int half, java.util.function.IntUnaryOperator y) {
        int[][] d = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < d.length; i++) out.add(new int[]{cx + d[i][0] * half, y.applyAsInt(i), cz + d[i][1] * half});
        return out;
    }

    private static List<int[]> circle(int cx, int cz, int r, int n, int y) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            out.add(new int[]{cx + (int) Math.round(Math.cos(a) * r), y, cz + (int) Math.round(Math.sin(a) * r)});
        }
        return out;
    }

    private static boolean clash(List<int[]> taken, int x, int z, int hw, int hd) {
        for (int[] t : taken) if (x + hw >= t[0] && x - hw <= t[2] && z + hd >= t[1] && z - hd <= t[3]) return true;
        return false;
    }

    private static void take(List<int[]> taken, int x, int z, int w, int d) {
        taken.add(new int[]{x - w / 2 - MARGIN, z - d / 2 - MARGIN, x + w / 2 + MARGIN, z + d / 2 + MARGIN});
    }

    /** A spot for a {@code w x d} footprint inside the square of half-size {@code inner} round the centre that overlaps nothing taken; null when there is none. */
    private static int[] pack(Random rng, List<int[]> taken, int cx, int cz, int inner, int w, int d) {
        int hw = w / 2 + MARGIN, hd = d / 2 + MARGIN;
        if (inner < hw || inner < hd) return null;
        for (int tries = 0; tries < 400; tries++) {
            int x = cx + rng.nextInt(2 * (inner - hw) + 1) - (inner - hw), z = cz + rng.nextInt(2 * (inner - hd) + 1) - (inner - hd);
            if (!clash(taken, x, z, hw, hd)) {
                take(taken, x, z, w, d);
                return new int[]{x, z};
            }
        }
        for (int x = cx - (inner - hw); x <= cx + (inner - hw); x++) {                          // random luck ran out: first fit
            for (int z = cz - (inner - hd); z <= cz + (inner - hd); z++) {
                if (!clash(taken, x, z, hw, hd)) {
                    take(taken, x, z, w, d);
                    return new int[]{x, z};
                }
            }
        }
        return null;
    }
}
