package com.wf.wflib.soldier;

import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.StancePose;
import com.wf.wflib.grenade.api.SmokeScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Terrain reasoning for scripted soldiers on one floor level: walkable cells, exposure to known threats, cover cells
 * (a kneeling body hidden from every threat) with a firing option (stand up, or side-step to a cell that sees the
 * primary threat), and danger-weighted A* routes that prefer cells the threats cannot see.
 */
final class BotTactics {
    /** Blocks within which smoke does not stop a soldier seeing someone. */
    static final double NEAR_SIGHT = 5;
    static final double KNEEL_LOW = 0.9, KNEEL_HIGH = 1.3, STAND_EYE = 1.62;
    private static final int MAX_NODES = 5000;
    private static final double EXPOSED_COST = 3.5;

    private BotTactics() {}

    /** Feet cell standable: support below (or a low block like a carpet/slab in the cell), feet low, head clear. */
    static boolean walkable(ServerLevel level, BlockPos feet) {
        VoxelShape here = level.getBlockState(feet).getCollisionShape(level, feet);
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        if (!here.isEmpty()) return here.max(Direction.Axis.Y) <= 0.6
                && level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty();
        BlockPos below = feet.below();
        VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
        return !floor.isEmpty() && floor.max(Direction.Axis.Y) >= 0.5;     // full blocks, stairs, top slabs
    }

    static boolean sees(ServerLevel level, Vec3 from, Vec3 to, Entity viewer) {
        // smoke hides what is beyond it, but anyone within a few blocks is a silhouette: nobody is blind at arm's length
        return clearRay(level, from, to, viewer) && (from.distanceToSqr(to) <= NEAR_SIGHT * NEAR_SIGHT
                || !SmokeScreens.blocked(level, from, to));
    }

    static boolean clearRay(ServerLevel level, Vec3 from, Vec3 to, Entity viewer) {
        SoldierProfiler.rays++;
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer))
                .getType() == HitResult.Type.MISS;
    }

    /** Some threat eye sees a body standing/kneeling at {@code cell} at height {@code h}. */
    static boolean exposed(ServerLevel level, BlockPos cell, List<Vec3> threats, double h, Entity viewer) {
        Vec3 at = Vec3.atBottomCenterOf(cell).add(0, h, 0);
        for (Vec3 t : threats) if (sees(level, t, at, viewer)) return true;
        return false;
    }

    record Peek(Vec3 feet, Posture posture, float lean) {}

    /** A hidden position and a collision-tested firing posture, including the gun mod's actual lean geometry. */
    record Cover(BlockPos cell, Peek peek, double score) {}

    /**
     * Best cover within {@code radius} of {@code center} (same floor as {@code from}) against {@code threats}
     * (eye positions, primary first). {@code toward} (nullable) rewards cells closer to an objective by at least
     * {@code minProgress}. {@code taken} = other soldiers' cover cells.
     */
    static Cover findCover(ServerLevel level, Entity viewer, BlockPos from, BlockPos center, int radius,
                           List<Vec3> threats, Vec3 toward, double minProgress, List<BlockPos> taken, boolean needPeek) {
        long start = System.nanoTime();
        try {
            return findCoverImpl(level, viewer, from, center, radius, threats, toward, minProgress, taken, needPeek);
        } finally {
            SoldierProfiler.covers++;
            SoldierProfiler.coverNanos += System.nanoTime() - start;
        }
    }

    private static Cover findCoverImpl(ServerLevel level, Entity viewer, BlockPos from, BlockPos center, int radius,
                                       List<Vec3> threats, Vec3 toward, double minProgress, List<BlockPos> taken, boolean needPeek) {
        if (threats.isEmpty()) return null;
        Vec3 primary = threats.get(0);
        double startProgress = toward == null ? 0 : horizontal(Vec3.atBottomCenterOf(from), toward);
        List<Cover> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos cell = new BlockPos(center.getX() + dx, from.getY(), center.getZ() + dz);
                if (!walkable(level, cell) || near(cell, taken, 1.5)) continue;
                double progress = toward == null ? 0 : startProgress - horizontal(Vec3.atBottomCenterOf(cell), toward);
                if (toward != null && progress < minProgress) continue;
                if (exposed(level, cell, threats, KNEEL_LOW, viewer) || exposed(level, cell, threats, KNEEL_HIGH, viewer)) {
                    continue;
                }
                Peek peek = peekFrom(level, viewer, cell, primary);
                if (peek == null && needPeek) continue;
                double travel = Math.sqrt(from.distSqr(cell));
                double score = (peek == null ? 2 : peek.lean() != 0 ? 10 : peek.feet().equals(Vec3.atBottomCenterOf(cell)) ? 8 : 6)
                        - 0.35 * travel + 0.6 * progress
                        - 0.15 * Math.abs(horizontal(Vec3.atBottomCenterOf(cell), primary) - 22); // ~22 blocks is a good fight
                candidates.add(new Cover(cell,
                        peek == null ? new Peek(Vec3.atBottomCenterOf(cell), Posture.KNEEL, 0) : peek, score));
            }
        }
        return candidates.stream().sorted(Comparator.comparingDouble((Cover c) -> -c.score())).limit(6)
                .filter(c -> from.equals(c.cell()) || !route(level, viewer, from, c.cell(), threats).isEmpty())
                .findFirst().orElse(null);
    }

    record Rest(BlockPos cell, float yaw) {}

    /**
     * A cell beside a wall (best: in a corner) within {@code radius} of {@code anchor}, facing the open side; null when
     * the area is open ground. A soldier waiting there is not standing in the middle of the street.
     */
    static Rest restSpot(ServerLevel level, Entity viewer, BlockPos anchor, int radius) {
        Rest best = null;
        double bestScore = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos cell = anchor.offset(dx, 0, dz);
                if (!walkable(level, cell)) continue;
                int walls = 0;
                double ox = 0, oz = 0;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = cell.relative(d);
                    boolean solid = !level.getBlockState(n).getCollisionShape(level, n).isEmpty()
                            && !level.getBlockState(n.above()).getCollisionShape(level, n.above()).isEmpty();
                    if (solid) {
                        walls++;
                        ox -= d.getStepX();
                        oz -= d.getStepZ();
                    }
                }
                if (walls == 0 || walls > 2) continue;                 // open ground, or a dead-end pocket
                double score = walls * 3 - Math.sqrt(dx * dx + dz * dz) * 0.5;
                if (best == null || score > bestScore) {
                    bestScore = score;
                    best = new Rest(cell, SoldierAi.yawOf(new Vec3(ox, 0, oz)));
                }
            }
        }
        return best;
    }

    /** The cell near {@code from} that the fewest threats can see (standing, and kneeling at half weight); null = stay. */
    static BlockPos leastExposed(ServerLevel level, Entity viewer, BlockPos from, int radius, List<Vec3> threats) {
        BlockPos best = null;
        double bestScore = score(level, viewer, from, threats);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos cell = new BlockPos(from.getX() + dx, from.getY(), from.getZ() + dz);
                if (!walkable(level, cell)) continue;
                double s = score(level, viewer, cell, threats) + 0.15 * Math.sqrt(dx * dx + dz * dz);
                if (s < bestScore - 0.5) {
                    bestScore = s;
                    best = cell;
                }
            }
        }
        return best;
    }

    private static double score(ServerLevel level, Entity viewer, BlockPos cell, List<Vec3> threats) {
        double s = 0;
        for (Vec3 t : threats) {
            Vec3 at = Vec3.atBottomCenterOf(cell);
            if (sees(level, t, at.add(0, STAND_EYE, 0), viewer)) s += 1;
            if (sees(level, t, at.add(0, KNEEL_HIGH, 0), viewer)) s += 0.5;
        }
        return s;
    }

    /** Prefer a lean around an edge, then standing over low cover, then an unobstructed side-step. */
    static Peek peekFrom(ServerLevel level, Entity viewer, BlockPos cell, Vec3 target) {
        Vec3 chest = target.subtract(0, 0.6, 0);
        Vec3 feet = Vec3.atBottomCenterOf(cell);
        float yaw = SoldierAi.yawOf(chest.subtract(feet));
        for (Posture posture : new Posture[]{Posture.KNEEL, Posture.STAND}) {
            double height = posture == Posture.KNEEL ? 1.0 : STAND_EYE;
            if (sees(level, feet.add(0, height, 0), chest, viewer)) continue;
            for (float lean : new float[]{-1, 1}) {
                Vec3 edge = feet.add(StancePose.localToWorld(new Vec3(0.22 * lean, 0, 0), yaw));
                Vec3 base = edge.add(0, height, 0), eye = peekEye(edge, posture, lean, yaw);
                if (level.noCollision(viewer, viewer.getBoundingBox().move(edge.subtract(viewer.position())))
                        && sees(level, base, eye.add(eye.subtract(base).normalize().scale(0.1)), viewer)
                        && sees(level, eye, chest, viewer)) return new Peek(edge, posture, lean);
            }
        }
        if (sees(level, feet.add(0, STAND_EYE, 0), chest, viewer)) return new Peek(feet, Posture.STAND, 0);
        Vec3 d = chest.subtract(feet);
        int sx = (int) Math.signum(-d.z), sz = (int) Math.signum(d.x);   // perpendicular to the threat line
        if (Math.abs(d.x) > Math.abs(d.z)) sx = 0; else sz = 0;
        if (sx == 0 && sz == 0) return null;
        for (int k : new int[]{1, -1, 2, -2}) {
            BlockPos side = cell.offset(sx * k, 0, sz * k);
            if (walkable(level, side) && straight(level, cell, side)
                    && sees(level, Vec3.atBottomCenterOf(side).add(0, STAND_EYE, 0), chest, viewer)) {
                return new Peek(Vec3.atBottomCenterOf(side), Posture.STAND, 0);
            }
        }
        return null;
    }

    static Vec3 peekEye(Vec3 feet, Posture posture, float lean, float yaw) {
        double height = posture == Posture.KNEEL ? 1.0 : STAND_EYE;
        StancePose pose = new StancePose(posture, lean, 0, 0, 0, 0, false);
        return feet.add(0, height, 0).add(StancePose.localToWorld(pose.eyeOffsetLocal(height), yaw));
    }

    /**
     * Danger-weighted A* over walkable cells, stepping up or down one block between neighbours (stairs, slabs, kerbs),
     * so routes reach other floors through staircases. Smoothed waypoints (cell centers, feet height), or empty.
     */
    static List<Vec3> route(ServerLevel level, Entity viewer, BlockPos start, BlockPos goal, List<Vec3> threats) {
        if (start.equals(goal)) return List.of();
        return route(level, viewer, start, goal, threats, List.of(), List.of());
    }

    static List<Vec3> routeAvoiding(ServerLevel level, Entity viewer, BlockPos start, BlockPos goal,
                                    List<Vec3> threats, List<BlockPos> occupied) {
        if (start.equals(goal)) return List.of();
        List<Vec3> direct = route(level, viewer, start, goal, threats, List.of(), occupied);
        if (!direct.isEmpty()) return direct;
        // A blocked/occupied exact anchor must not make a soldier run into its wall forever.
        List<BlockPos> alternatives = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos cell = goal.offset(dx, 0, dz);
            if (!cell.equals(start) && !occupied.contains(cell) && walkable(level, cell)) alternatives.add(cell);
        }
        alternatives.sort(Comparator.comparingDouble(c -> c.distSqr(goal) + c.distSqr(start) * 0.05));
        for (BlockPos cell : alternatives.stream().limit(4).toList()) {
            List<Vec3> detour = route(level, viewer, start, cell, threats, List.of(), occupied);
            if (!detour.isEmpty()) return detour;
        }
        return List.of();
    }

    /** Search for reachable safety, including a partial escape when every exit is blocked. */
    static List<Vec3> escape(ServerLevel level, Entity viewer, BlockPos start, List<Vec3> grenades) {
        if (grenades.isEmpty()) return List.of();
        return route(level, viewer, start, null, List.of(), grenades, List.of());
    }

    private static List<Vec3> route(ServerLevel level, Entity viewer, BlockPos start, BlockPos goal,
                                    List<Vec3> threats, List<Vec3> grenades, List<BlockPos> occupied) {
        long began = System.nanoTime();
        SoldierProfiler.routes++;
        try {
            return routeImpl(level, viewer, start, goal, threats, grenades, occupied);
        } finally {
            SoldierProfiler.routeNanos += System.nanoTime() - began;
        }
    }

    private static List<Vec3> routeImpl(ServerLevel level, Entity viewer, BlockPos start, BlockPos goal,
                                        List<Vec3> threats, List<Vec3> grenades, List<BlockPos> occupied) {
        boolean fleeing = goal == null;
        Map<Long, Double> cost = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Boolean> danger = new HashMap<>();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        long s = start.asLong(), g = fleeing ? Long.MIN_VALUE : goal.asLong();
        cost.put(s, 0.0);
        open.add(new double[]{0, s, 0});
        int expanded = 0;
        long found = Long.MIN_VALUE;
        double bestClearance = BotGrenades.clearance(Vec3.atBottomCenterOf(start), grenades);
        while (!open.isEmpty() && expanded < (fleeing ? 1200 : MAX_NODES)) {
            double[] entry = open.poll();
            long k = (long) entry[1];
            if (entry[2] > cost.get(k)) continue; // superseded routes must not consume the search budget
            expanded++;
            SoldierProfiler.nodes++;
            BlockPos at = BlockPos.of(k);
            double clearance = fleeing ? BotGrenades.clearance(Vec3.atBottomCenterOf(at), grenades) : 0;
            if (fleeing && clearance > bestClearance + 0.05) {
                found = k;
                bestClearance = clearance;
            }
            if (fleeing ? k != s && clearance >= BotGrenades.SAFE_DISTANCE : k == g) {
                found = k;
                break;
            }
            double base = cost.get(k);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy : new int[]{0, 1, -1}) {
                        BlockPos c = at.offset(dx, dy, dz);
                        if (occupied.contains(c) && !c.equals(goal) && !c.equals(start)) continue;
                        if (fleeing && start.distSqr(c) > 18 * 18) continue;
                        if (!walkable(level, c)) continue;
                        if (dy != 0 && (dx != 0 && dz != 0
                                || dy > 0 && !clear(level, at.above(2))          // head room to step up
                                || dy < 0 && !clear(level, c.above(2)))) continue; // and to step down into
                        if (dy == 0 && dx != 0 && dz != 0 && (!walkable(level, at.offset(dx, 0, 0))
                                || !walkable(level, at.offset(0, 0, dz)))) continue;
                        long ck = c.asLong();
                        double step = (dx != 0 && dz != 0 ? 1.414 : 1) + (dy != 0 ? 0.6 : 0)
                                + (danger.computeIfAbsent(ck, q -> exposed(level, c, threats, KNEEL_HIGH, viewer)) ? EXPOSED_COST : 0);
                        double distance = fleeing ? BotGrenades.clearance(Vec3.atBottomCenterOf(c), grenades) : 0;
                        if (fleeing) step += 3 / Math.max(1, distance)
                                + Math.max(0, clearance - distance) * 8;
                        double nc = base + step;
                        if (nc < cost.getOrDefault(ck, Double.MAX_VALUE)) {
                            cost.put(ck, nc);
                            parent.put(ck, k);
                            double remaining = fleeing ? Math.max(0, BotGrenades.SAFE_DISTANCE - distance)
                                    : Math.sqrt(goal.distSqr(c)) + 2 * Math.abs(goal.getY() - c.getY());
                            open.add(new double[]{nc + remaining, ck, nc});
                        }
                        break;                                         // one level per column
                    }
                }
            }
        }
        if (found == Long.MIN_VALUE) return List.of();
        List<BlockPos> cells = new ArrayList<>();
        for (long k = found; k != s; k = parent.get(k)) cells.add(BlockPos.of(k));
        cells.add(start);
        Collections.reverse(cells);
        // Keep escape turns: a straight shortcut can cross back through the grenade's danger area.
        if (fleeing) return cells.stream().skip(1).map(Vec3::atBottomCenterOf).toList();
        // string-pull on a level; every change of level is kept as a waypoint
        List<Vec3> out = new ArrayList<>();
        int anchor = 0;
        for (int i = 2; i < cells.size(); i++) {
            BlockPos a = cells.get(anchor), b = cells.get(i);
            if (a.getY() != b.getY() || cells.get(i - 1).getY() != b.getY() || !straight(level, a, b)
                    || !unoccupied(a, b, occupied)
                    || !threats.isEmpty() && !safeShortcut(level, viewer, a, b, threats)) {
                out.add(Vec3.atBottomCenterOf(cells.get(i - 1)));
                anchor = i - 1;
            }
        }
        out.add(Vec3.atBottomCenterOf(cells.get(cells.size() - 1)));
        return out;
    }

    private static boolean unoccupied(BlockPos a, BlockPos b, List<BlockPos> occupied) {
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(a.distSqr(b)) * 2));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            Vec3 at = Vec3.atBottomCenterOf(a).lerp(Vec3.atBottomCenterOf(b), t);
            for (BlockPos cell : occupied) if (Vec3.atBottomCenterOf(cell).distanceToSqr(at) < 1) return false;
        }
        return true;
    }

    /** String-pulling must not erase the safer detour that the exposure-weighted search just selected. */
    private static boolean safeShortcut(ServerLevel level, Entity viewer, BlockPos a, BlockPos b, List<Vec3> threats) {
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(a.distSqr(b)) * 2));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            BlockPos cell = BlockPos.containing(a.getX() + 0.5 + (b.getX() - a.getX()) * t,
                    a.getY(), a.getZ() + 0.5 + (b.getZ() - a.getZ()) * t);
            if (exposed(level, cell, threats, KNEEL_HIGH, viewer)) return false;
        }
        return true;
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Every cell a body (±0.35) crosses on the segment is walkable. */
    private static boolean straight(ServerLevel level, BlockPos a, BlockPos b) {
        double ax = a.getX() + 0.5, az = a.getZ() + 0.5, bx = b.getX() + 0.5, bz = b.getZ() + 0.5;
        int steps = (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 3);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps, x = ax + (bx - ax) * t, z = az + (bz - az) * t;
            for (double ox : new double[]{-0.35, 0.35}) {
                for (double oz : new double[]{-0.35, 0.35}) {
                    if (!walkable(level, BlockPos.containing(x + ox, a.getY(), z + oz))) return false;
                }
            }
        }
        return true;
    }

    private static boolean near(BlockPos cell, List<BlockPos> taken, double r) {
        for (BlockPos t : taken) if (t.distSqr(cell) < r * r) return true;
        return false;
    }

    static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    }
