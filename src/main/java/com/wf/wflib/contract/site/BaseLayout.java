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
 * <li>a perimeter wall with gates on opposite sides, towers on the corners and flanking the gates; no wall for a small post (sandbags and a watch tower instead);</li>
 * <li>the command building near the middle, barracks and the depot behind it, a vehicle bay inside a gate, a radio mast, a campfire, lights;</li>
 * <li>nothing overlaps and everything keeps a margin so soldiers can walk between buildings;</li>
 * <li>outside the wall: machine-gun nests covering the gates and trench runs that make an approach a killing ground;</li>
 * <li>two patrol loops (inside the wall and outside it) and small outposts out in the approaches, each with their own beat;</li>
 * <li>overwatch (marksmen) on towers, a reserve in the barracks, guards at the gates, the command post held by the best.</li>
 * </ul>
 * Pure: it reads the ground only through {@link Terrain}, so it runs under a unit test.
 */
public final class BaseLayout {
    private static final int MARGIN = 2;

    private BaseLayout() {
    }

    public static BasePlan plan(BaseKind kind, int cx, int cz, int padY, Random rng, Terrain ground) {
        List<Piece> pieces = new ArrayList<>();
        List<Zone> zones = new ArrayList<>();
        List<Slot> vehicles = new ArrayList<>();
        List<int[]> taken = new ArrayList<>();
        boolean walled = kind.wall > 0;
        int axis = rng.nextInt(2);
        Wall wall = walled ? new Wall(kind.wall, kind.wallHeight, axis) : null;
        int inner = walled ? kind.wall - 3 : kind.radius - 5;

        // the command building holds the middle; a small post gets a hut
        int cs = kind == BaseKind.FORTRESS ? 11 : walled ? 9 : 5;
        int cmx = cx + rng.nextInt(5) - 2, cmz = cz + rng.nextInt(5) - 2;
        pieces.add(new Piece(Type.COMMAND, cmx, padY, cmz, cs, cs, rng.nextInt(4), 4));
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
            towerAt.add(new int[]{cx + (int) Math.round(Math.cos(a) * 9), cz + (int) Math.round(Math.sin(a) * 9)});
        }
        for (int[] t : towerAt) {
            int ts = kind == BaseKind.OUTPOST ? 3 : 5;
            pieces.add(new Piece(Type.TOWER, t[0], padY, t[1], ts, ts, 0, towerH));
            take(taken, t[0], t[1], ts, ts);
            zones.add(new Zone(Role.TOWER, t[0], padY + towerH, t[1], 1, List.of(), 1.0));
        }

        // gates: guards just inside each
        if (walled) {
            for (int side = -1; side <= 1; side += 2) {
                int gx = axis == 0 ? 0 : side * (kind.wall - 3), gz = axis == 0 ? side * (kind.wall - 3) : 0;
                zones.add(new Zone(Role.GATE, cx + gx, padY, cz + gz, 3, List.of(), 1.5));
            }
        }

        // vehicle bays first: they need the room, and a bay by a gate is where an alert sends the soldiers
        for (int i = 0; i < kind.bays; i++) {
            int[] at = pack(rng, taken, cx, cz, inner, 9, 7);
            if (at == null) continue;
            pieces.add(new Piece(Type.BAY, at[0], padY, at[1], 9, 7, 0, 4));
            vehicles.add(new Slot(at[0], padY, at[1], rng.nextInt(4) * 90f, i == 0 && kind != BaseKind.FORTRESS ? "light" : i == 0 ? "light" : "armour"));
        }
        // buildings, packed behind the command post
        int barracks = walled ? kind.barracks : 2;
        for (int i = 0; i < barracks; i++) {
            int w = walled ? 11 : 4, d = walled ? 7 : 5;
            int[] at = pack(rng, taken, cx, cz, inner, w, d);
            if (at == null) continue;
            pieces.add(new Piece(walled ? Type.BARRACKS : Type.TENT, at[0], padY, at[1], w, d, 0, 4));
            if (i == 0 || kind == BaseKind.FORTRESS) zones.add(new Zone(Role.RESERVE, at[0], padY, at[1], 2, List.of(), 2.0));
        }
        int[] depot = pack(rng, taken, cx, cz, inner, 7, 5);
        if (depot != null) pieces.add(new Piece(Type.DEPOT, depot[0], padY, depot[1], 7, 5, 0, 3));
        for (int i = 0; i < kind.bunkers; i++) {
            int[] at = pack(rng, taken, cx, cz, inner, 7, 5);
            if (at != null) pieces.add(new Piece(Type.BUNKER, at[0], padY, at[1], 7, 5, rng.nextInt(2) * 2, 3));
        }
        int[] camp = pack(rng, taken, cx, cz, inner, 3, 3);
        if (camp != null) pieces.add(new Piece(Type.CAMP, camp[0], padY, camp[1], 3, 3, 0, 1));
        int[] mast = pack(rng, taken, cx, cz, inner, 1, 1);
        if (mast != null) pieces.add(new Piece(Type.MAST, mast[0], padY, mast[1], 1, 1, 0, 12));
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
            pieces.add(new Piece(Type.NEST, x, y, z, 5, 3, facing, 2));
            zones.add(new Zone(Role.NEST, x, y, z, 1, List.of(), 1.0));
        }
        for (int i = 0; i < kind.trenches; i++) {
            double a = base + Math.PI / kind.nests + i * Math.PI * 2 / Math.max(1, kind.trenches);
            int x = cx + (int) Math.round(Math.cos(a) * (ringR - 4)), z = cz + (int) Math.round(Math.sin(a) * (ringR - 4));
            if (!standable(ground, x, z)) continue;
            int y = standing(ground, padY, cx, cz, kind.radius, x, z);
            pieces.add(new Piece(Type.TRENCH, x, y, z, 14, 2, (int) Math.floorMod(Math.round(Math.toDegrees(a) / 90.0), 2) * 1, 2));
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
            pieces.add(new Piece(Type.TENT, x, y, z, 4, 5, 0, 3));
            pieces.add(new Piece(Type.NEST, x + 5, ground.available(x + 5, z) ? ground.ground(x + 5, z) : y, z, 5, 3, 0, 2));
            pieces.add(new Piece(Type.CAMP, x - 4, ground.available(x - 4, z) ? ground.ground(x - 4, z) : y, z, 3, 3, 0, 1));
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

    private static void take(List<int[]> taken, int x, int z, int w, int d) {
        taken.add(new int[]{x - w / 2 - MARGIN, z - d / 2 - MARGIN, x + w / 2 + MARGIN, z + d / 2 + MARGIN});
    }

    /** A spot for a {@code w x d} footprint inside the square of half-size {@code inner} round the centre that overlaps nothing taken; null when there is none. */
    private static int[] pack(Random rng, List<int[]> taken, int cx, int cz, int inner, int w, int d) {
        int hw = w / 2 + MARGIN, hd = d / 2 + MARGIN;
        if (inner < hw || inner < hd) return null;
        for (int tries = 0; tries < 60; tries++) {
            int x = cx + rng.nextInt(2 * (inner - hw) + 1) - (inner - hw), z = cz + rng.nextInt(2 * (inner - hd) + 1) - (inner - hd);
            boolean free = true;
            for (int[] t : taken) {
                if (x + hw >= t[0] && x - hw <= t[2] && z + hd >= t[1] && z - hd <= t[3]) {
                    free = false;
                    break;
                }
            }
            if (!free) continue;
            take(taken, x, z, w, d);
            return new int[]{x, z};
        }
        return null;
    }
}
