package com.wf.wflib.anomaly.cluster;

import com.wf.wflib.anomaly.cluster.ClusterPlan.Design;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The designs' ground work: each reshapes the terrain inside the site's outline and leaves floor (and ceiling) spots
 * for the anomalies. Sizes scale off the radius, so a tier 4 crater is a deep bowl and a tier 1 a scuff.
 */
final class ClusterShapes {
    private ClusterShapes() {
    }

    static void shape(ClusterSite s, Design design) {
        switch (design) {
            case CRATER -> crater(s);
            case FISSURE -> fissure(s);
            case BOG -> bog(s);
            case STORM_SCAR -> stormScar(s);
            case DEAD_GROVE -> deadGrove(s);
            case UPHEAVAL -> upheaval(s);
            case ARCH -> arch(s);
            case PIT -> pit(s);
        }
        fringe(s, design == Design.BOG ? b(Blocks.MUD) : b(Blocks.COARSE_DIRT));
    }

    /** Farthest a fringe reaches from the middle: with the middle 2 off the chunk's centre, still a chunk round it. */
    private static final int FRINGE_REACH = 21;

    /**
     * A ragged band past the outline where the ground thins from the cluster's into the land's: bare patches and
     * dead plants, fewer further out, so the edge is not a stamped circle.
     */
    private static void fringe(ClusterSite s, BlockState bare) {
        int reach = Math.min(FRINGE_REACH, s.radius + 3);
        int cx = s.centre.getX();
        int cz = s.centre.getZ();
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                double u = s.u(x, z);
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (u <= 1.0D || d > reach) {
                    continue;
                }
                // Blocks past the outline here (it sits d / u out at this angle), fading over 3.
                double fade = 1.0D - Math.min(1.0D, (d - d / u) / 3.0D);
                if (s.random.nextDouble() + 0.25D * s.noise(x, z, 3.0D) > fade * 0.8D) {
                    continue;
                }
                int y = s.ground(x, z);
                BlockState top = s.get(x, y, z);
                if (top.is(net.minecraft.tags.BlockTags.DIRT) || top.is(Blocks.SAND) || top.is(Blocks.SNOW_BLOCK)) {
                    // Plants only: trees at the edge keep their trunks.
                    for (int k = 1; k <= 2; k++) {
                        if (s.get(x, y + k, z).canBeReplaced()) {
                            s.set(x, y + k, z, Blocks.AIR.defaultBlockState());
                        }
                    }
                    s.set(x, y, z, bare);
                    if (s.chance(0.08F)) {
                        s.set(x, y + 1, z, b(Blocks.DEAD_BUSH));
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface Column {
        void at(int x, int z, double u);
    }

    /** Every column inside the outline. */
    private static void each(ClusterSite s, Column c) {
        int cx = s.centre.getX();
        int cz = s.centre.getZ();
        for (int x = cx - s.radius; x <= cx + s.radius; x++) {
            for (int z = cz - s.radius; z <= cz + s.radius; z++) {
                double u = s.u(x, z);
                if (u <= 1.0D) {
                    c.at(x, z, u);
                }
            }
        }
    }

    private static double smooth(double edge0, double edge1, double v) {
        double t = Mth.clamp((v - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static BlockState b(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    /** Toward the site's level: {@code pull} 1 flattens, 0 leaves the ground as it was. */
    private static int level(ClusterSite s, int ground, double pull) {
        return (int) Math.round(Mth.lerp(pull, ground, s.baseY));
    }

    // --- crater -----------------------------------------------------------------------------------------------------

    /** Bowl to 0.7 of the outline, depth a third of the radius; ejecta heaped beyond, stones thrown out on it. */
    private static void crater(ClusterSite s) {
        double depth = Math.max(2.0D, s.radius * 0.33D);
        double bowl = 0.7D;
        each(s, (x, z, u) -> {
            int g = s.ground(x, z);
            if (u < bowl) {
                double k = u / bowl;
                int y = Math.min(g, (int) Math.round(s.baseY - depth * (1.0D - k * k)));
                BlockState top = s.tier >= 3 && s.chance(0.08F) ? b(Blocks.BLACKSTONE)
                        : s.tier == 4 && k < 0.3D && s.chance(0.05F) ? b(Blocks.MAGMA_BLOCK)
                        : s.patch(x, z, 7.0D, b(Blocks.GRAVEL), b(Blocks.COARSE_DIRT), b(Blocks.TUFF),
                        b(Blocks.COARSE_DIRT));
                s.column(x, z, y, top, b(Blocks.GRAVEL), 2);
                if (k < 0.8D) {
                    s.addFloor(x, y + 1, z);
                }
            } else {
                // Rim: a bump that rises off the bowl's lip and falls away outward.
                double t = (u - bowl) / (1.0D - bowl);
                int y = Math.max(g, s.baseY) + (int) Math.round(depth * 0.45D * Math.sin(Math.PI * Math.sqrt(t))
                        * (0.7D + 0.3D * s.noise(x, z, 3.0D)));
                y = (int) Math.round(Mth.lerp(smooth(0.6D, 1.0D, t), y, g));
                s.column(x, z, y, s.patch(x, z, 6.0D, b(Blocks.COARSE_DIRT), b(Blocks.DIRT), b(Blocks.GRAVEL)),
                        b(Blocks.DIRT), 2);
                if (t < 0.6D && s.chance(0.04F)) {
                    boulder(s, x, y + 1, z, s.chance(0.5F) ? b(Blocks.COBBLESTONE) : b(Blocks.ANDESITE));
                }
            }
        });
    }

    /** A stone or two of rubble. */
    private static void boulder(ClusterSite s, int x, int y, int z, BlockState state) {
        s.set(x, y, z, state);
        if (s.chance(0.5F)) {
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(s.random);
            s.set(x + d.getStepX(), y, z + d.getStepZ(), state);
        }
    }

    // --- fissure ----------------------------------------------------------------------------------------------------

    /** A crack: points along a line through near the middle, wandering sideways. */
    private record Crack(List<double[]> points, double width, int depth) {
        /** Distance to the crack in blocks, and how far along it (0 middle .. 1 the tips), as {d, along}. */
        double[] nearest(double x, double z) {
            double best = Double.MAX_VALUE;
            double along = 1.0D;
            for (int i = 0; i + 1 < points.size(); i++) {
                double[] a = points.get(i);
                double[] p = points.get(i + 1);
                double vx = p[0] - a[0];
                double vz = p[1] - a[1];
                double len2 = vx * vx + vz * vz;
                double t = len2 == 0.0D ? 0.0D : Mth.clamp(((x - a[0]) * vx + (z - a[1]) * vz) / len2, 0.0D, 1.0D);
                double dx = x - (a[0] + vx * t);
                double dz = z - (a[1] + vz * t);
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < best) {
                    best = d;
                    along = Math.abs((i + t) / (points.size() - 1) * 2.0D - 1.0D);
                }
            }
            return new double[] {best, along};
        }
    }

    private static Crack crack(ClusterSite s, double length) {
        double angle = s.random.nextDouble() * Math.PI;
        double ox = s.centre.getX() + 0.5D + (s.random.nextDouble() - 0.5D) * s.radius * 0.3D;
        double oz = s.centre.getZ() + 0.5D + (s.random.nextDouble() - 0.5D) * s.radius * 0.3D;
        double ax = Math.cos(angle);
        double az = Math.sin(angle);
        List<double[]> pts = new ArrayList<>();
        double side = 0.0D;
        for (double t = -length; t <= length; t += 2.0D) {
            side = Mth.clamp(side + (s.random.nextDouble() - 0.5D) * 1.6D, -2.5D, 2.5D);
            pts.add(new double[] {ox + ax * t - az * side, oz + az * t + ax * side});
        }
        return new Crack(pts, 0.6D + 0.35D * s.tier + s.random.nextDouble() * 0.4D, 3 + 2 * s.tier);
    }

    /** One to three cracks through scorched ground, magma glowing at the bottom from tier 3; anomalies line the lips. */
    private static void fissure(ClusterSite s) {
        List<Crack> cracks = new ArrayList<>();
        int n = 1 + (s.tier >= 3 ? 1 : 0) + s.random.nextInt(2);
        for (int i = 0; i < n; i++) {
            cracks.add(crack(s, s.radius * (i == 0 ? 0.95D : 0.6D)));
        }
        each(s, (x, z, u) -> {
            int g = s.ground(x, z);
            double d = Double.MAX_VALUE;
            double half = 0.0D;
            int depth = 0;
            for (Crack c : cracks) {
                double[] near = c.nearest(x + 0.5D, z + 0.5D);
                double w = c.width() * (1.0D - 0.7D * near[1]);
                if (near[0] - w < d - half) {
                    d = near[0];
                    half = w;
                    depth = (int) Math.round(c.depth() * (1.0D - 0.6D * near[1]));
                }
            }
            int y = level(s, g, 0.4D * (1.0D - smooth(0.7D, 1.0D, u)));
            if (d < half) {
                s.column(x, z, y - depth, s.tier >= 3 ? b(Blocks.MAGMA_BLOCK) : b(Blocks.BASALT), b(Blocks.BLACKSTONE), 2);
                if (s.chance(0.3F)) {
                    s.addFloor(x, y - depth + 1, z);
                }
            } else if (d < half + 1.5D) {
                // The lips: burnt rock, not grass.
                s.column(x, z, y, s.chance(0.5F) ? b(Blocks.BASALT) : b(Blocks.BLACKSTONE), b(Blocks.BLACKSTONE), 3);
            } else {
                BlockState top = s.tier >= 3 && s.chance(0.03F) ? b(Blocks.MAGMA_BLOCK)
                        : s.patch(x, z, 8.0D, b(Blocks.COARSE_DIRT), b(Blocks.BLACKSTONE), b(Blocks.COARSE_DIRT),
                        b(Blocks.GRAVEL));
                s.column(x, z, y, top, b(Blocks.DIRT), 2);
                if (d < half + 4.0D && u < 0.95D) {
                    s.addFloor(x, y + 1, z);
                } else if (u < 0.9D && s.chance(0.3F)) {
                    s.addFloor(x, y + 1, z);
                }
            }
        });
    }

    // --- bog --------------------------------------------------------------------------------------------------------

    /** A sunken hollow of mud, stagnant puddles in its low patches, dead bushes and old bones. */
    private static void bog(ClusterSite s) {
        int sink = 1 + (s.tier >= 3 ? 1 : 0);
        int cx = s.centre.getX();
        int cz = s.centre.getZ();
        int side = 2 * s.radius + 1;
        int[] top = new int[side * side];
        each(s, (x, z, u) -> {
            int g = s.ground(x, z);
            double rim = smooth(0.75D, 1.0D, u);
            int floor = s.baseY - sink + (int) Math.round(s.noise(x, z, 6.0D));
            top[(x - cx + s.radius) * side + (z - cz + s.radius)] = (int) Math.round(Mth.lerp(rim, Math.min(g, floor), g));
        });
        each(s, (x, z, u) -> {
            int i = (x - cx + s.radius) * side + (z - cz + s.radius);
            int y = top[i];
            boolean wet = u < 0.8D && s.noise(x + 1000, z, 4.0D) > 0.2D && contained(top, side, x - cx + s.radius,
                    z - cz + s.radius, y);
            BlockState surface = wet ? b(Blocks.WATER)
                    : u > 0.85D ? b(Blocks.COARSE_DIRT)
                    : s.patch(x, z, 6.0D, b(Blocks.MUD), b(Blocks.MUD), b(Blocks.PACKED_MUD), b(Blocks.MUD),
                    b(Blocks.MOSS_BLOCK));
            s.column(x, z, y, surface, wet ? b(Blocks.MUD) : b(Blocks.DIRT), wet ? 2 : 1);
            if (wet) {
                s.set(x, y - 1, z, b(Blocks.CLAY));
                return;
            }
            if (s.chance(0.06F)) {
                s.set(x, y + 1, z, b(Blocks.DEAD_BUSH));
            } else if (s.tier >= 2 && s.chance(0.012F)) {
                bones(s, x, y, z);
            } else if (u < 0.9D) {
                s.addFloor(x, y + 1, z);
            }
        });
    }

    /** Water only where every side holds it (all four neighbours as high), so nothing spills when it updates. */
    private static boolean contained(int[] top, int side, int i, int j, int y) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int a = i + d[0];
            int c = j + d[1];
            if (a < 0 || c < 0 || a >= side || c >= side || top[a * side + c] < y) {
                return false;
            }
        }
        return true;
    }

    /** A few bone blocks half in the mud: what the anomalies left of someone. */
    private static void bones(ClusterSite s, int x, int y, int z) {
        Direction.Axis axis = s.chance(0.5F) ? Direction.Axis.X : Direction.Axis.Z;
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        int n = 2 + s.random.nextInt(2);
        for (int k = 0; k < n; k++) {
            s.set(axis == Direction.Axis.X ? x + k : x, y, axis == Direction.Axis.Z ? z + k : z, bone);
        }
    }

    // --- storm scar -------------------------------------------------------------------------------------------------

    /**
     * Ground fused nearly flat by discharges: dark branching scorch streaks out from the middle, glassy fulgurite
     * spikes, and from tier 2 a ruined pylon, its copper weathered green.
     */
    private static void stormScar(ClusterSite s) {
        java.util.Set<Long> streak = new java.util.HashSet<>();
        int branches = 3 + s.random.nextInt(3);
        for (int i = 0; i < branches; i++) {
            lichtenberg(s, s.centre.getX(), s.centre.getZ(), s.random.nextDouble() * Mth.TWO_PI, s.radius * 0.9D,
                    streak, 2);
        }
        each(s, (x, z, u) -> {
            int g = s.ground(x, z);
            int y = level(s, g, 0.75D * (1.0D - smooth(0.7D, 1.0D, u)));
            BlockState top = streak.contains(net.minecraft.world.level.ChunkPos.asLong(x, z)) ? b(Blocks.POLISHED_DEEPSLATE)
                    : u > 0.85D ? b(Blocks.COARSE_DIRT)
                    : s.patch(x, z, 7.0D, b(Blocks.COARSE_DIRT), b(Blocks.TUFF), b(Blocks.COBBLED_DEEPSLATE),
                    b(Blocks.COARSE_DIRT));
            s.column(x, z, y, top, b(Blocks.DIRT), 2);
            if (u < 0.85D && s.chance(0.012F + 0.004F * s.tier)) {
                int h = 1 + s.random.nextInt(3);
                for (int k = 1; k <= h; k++) {
                    s.set(x, y + k, z, b(Blocks.TINTED_GLASS));
                }
            } else if (u < 0.9D) {
                s.addFloor(x, y + 1, z);
            }
        });
        if (s.tier >= 2) {
            double a = s.random.nextDouble() * Mth.TWO_PI;
            int px = s.centre.getX() + (int) Math.round(Math.cos(a) * s.radius * 0.5D);
            int pz = s.centre.getZ() + (int) Math.round(Math.sin(a) * s.radius * 0.5D);
            int y = s.ground(px, pz);
            s.set(px, y + 1, pz, b(Blocks.WEATHERED_COPPER));
            int h = 3 + s.random.nextInt(3);
            for (int k = 2; k < 2 + h; k++) {
                s.set(px, y + k, pz, b(Blocks.IRON_BARS));
            }
            s.set(px, y + 2 + h, pz, s.chance(0.6F) ? b(Blocks.LIGHTNING_ROD) : b(Blocks.CHAIN));
            s.floor.removeIf(p -> p.getX() == px && p.getZ() == pz);
        }
    }

    /** A branching scorch: a random walk out, splitting now and then. */
    private static void lichtenberg(ClusterSite s, double x, double z, double angle, double length,
                                    java.util.Set<Long> out, int splits) {
        for (double t = 0.0D; t < length; t += 1.0D) {
            angle += (s.random.nextDouble() - 0.5D) * 0.7D;
            x += Math.cos(angle);
            z += Math.sin(angle);
            out.add(net.minecraft.world.level.ChunkPos.asLong(Mth.floor(x), Mth.floor(z)));
            if (splits > 0 && s.chance(0.06F)) {
                lichtenberg(s, x, z, angle + (s.chance(0.5F) ? 0.8D : -0.8D), (length - t) * 0.6D, out, splits - 1);
            }
        }
    }

    // --- dead grove -------------------------------------------------------------------------------------------------

    /** Bark-stripped dead trees on scorched forest floor; their bare branches are where Burnt Fuzz hangs. */
    private static void deadGrove(ClusterSite s) {
        List<int[]> trees = spread(s, 3 + s.tier + s.random.nextInt(2), 4.0D, 0.85D);
        each(s, (x, z, u) -> {
            int g = s.ground(x, z);
            int y = level(s, g, 0.3D * (1.0D - smooth(0.7D, 1.0D, u)));
            s.column(x, z, y, s.patch(x, z, 6.0D, b(Blocks.PODZOL), b(Blocks.COARSE_DIRT), b(Blocks.ROOTED_DIRT),
                    b(Blocks.PODZOL)), b(Blocks.DIRT), 2);
            if (s.chance(0.05F)) {
                s.set(x, y + 1, z, b(Blocks.DEAD_BUSH));
            } else if (u < 0.9D) {
                s.addFloor(x, y + 1, z);
            }
        });
        for (int[] t : trees) {
            deadTree(s, t[0], s.ground(t[0], t[1]) + 1, t[1]);
        }
    }

    /** Up to {@code n} columns at least {@code spacing} apart inside {@code within} of the outline. */
    private static List<int[]> spread(ClusterSite s, int n, double spacing, double within) {
        List<int[]> out = new ArrayList<>();
        for (int tries = 0; tries < n * 20 && out.size() < n; tries++) {
            int x = s.centre.getX() + s.random.nextInt(2 * s.radius + 1) - s.radius;
            int z = s.centre.getZ() + s.random.nextInt(2 * s.radius + 1) - s.radius;
            if (s.u(x, z) > within) {
                continue;
            }
            boolean clear = true;
            for (int[] o : out) {
                clear &= (o[0] - x) * (o[0] - x) + (o[1] - z) * (o[1] - z) >= spacing * spacing;
            }
            if (clear) {
                out.add(new int[] {x, z});
            }
        }
        return out;
    }

    private static void deadTree(ClusterSite s, int x, int y, int z) {
        BlockState log = (s.chance(0.6F) ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG).defaultBlockState();
        int h = 5 + s.random.nextInt(3);
        for (int k = 0; k < h; k++) {
            s.set(x, y + k, z, log);
        }
        s.floor.removeIf(p -> p.getX() == x && p.getZ() == z);
        List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        net.minecraft.Util.shuffle(dirs, s.random);
        int branches = 2 + s.random.nextInt(2);
        for (int i = 0; i < branches; i++) {
            Direction d = dirs.get(i);
            int by = y + h - 1 - s.random.nextInt(3);
            int len = 2 + s.random.nextInt(2);
            BlockState branch = log.setValue(RotatedPillarBlock.AXIS, d.getAxis());
            for (int k = 1; k <= len; k++) {
                int bx = x + d.getStepX() * k;
                int bz = z + d.getStepZ() * k;
                s.set(bx, by, bz, branch);
                // Off the trunk, with air under it and room for the strands to hang: a ceiling spot.
                if (k >= 2 && by - 1 - s.ground(bx, bz) >= 3) {
                    s.addCeiling(bx, by - 1, bz);
                }
            }
            if (s.chance(0.5F)) {
                s.set(x + d.getStepX() * len, by + 1, z + d.getStepZ() * len, log);
            }
        }
    }

    // --- upheaval ---------------------------------------------------------------------------------------------------

    /**
     * Ground torn loose and left in the air: boulders of earth and stone hanging over the pits they came out of, and
     * jagged spires pushed up through the ground.
     */
    private static void upheaval(ClusterSite s) {
        List<int[]> rocks = spread(s, 2 + s.tier, 5.0D, 0.75D);
        double[] pitR = new double[rocks.size()];
        for (int i = 0; i < pitR.length; i++) {
            pitR[i] = 1.5D + s.random.nextDouble();
        }
        each(s, (x, z, u) -> {
            int g = s.ground(x, z);
            int y = level(s, g, 0.2D * (1.0D - smooth(0.7D, 1.0D, u)));
            for (int i = 0; i < rocks.size(); i++) {
                double dx = x - rocks.get(i)[0];
                double dz = z - rocks.get(i)[1];
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < pitR[i]) {
                    y -= (int) Math.round(2.0D * (1.0D - d / pitR[i]) + 0.5D);
                }
            }
            s.column(x, z, y, s.patch(x, z, 7.0D, b(Blocks.COARSE_DIRT), b(Blocks.DIRT), b(Blocks.GRAVEL),
                    b(Blocks.ROOTED_DIRT)), b(Blocks.DIRT), 2);
            if (u < 0.9D) {
                s.addFloor(x, y + 1, z);
            }
        });
        for (int i = 0; i < rocks.size(); i++) {
            int[] r = rocks.get(i);
            int lift = 6 + s.random.nextInt(4 + s.tier);
            floatingBoulder(s, r[0], s.ground(r[0], r[1]) + lift, r[1], pitR[i] * 0.95D);
        }
        for (int[] sp : spread(s, s.tier, 4.0D, 0.8D)) {
            spire(s, sp[0], s.ground(sp[0], sp[1]) + 1, sp[1], 3 + s.random.nextInt(2 + s.tier));
        }
    }

    /** A clod of earth over stone, the grass still on it, ragged at the bottom. */
    private static void floatingBoulder(ClusterSite s, int x, int y, int z, double r) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6D + dz * dz) + s.random.nextDouble() * 0.4D;
                    if (d > r) {
                        continue;
                    }
                    BlockState state = dy == ri - 1 || (dy >= 0 && d > r - 0.8D) ? b(Blocks.GRASS_BLOCK)
                            : dy >= 0 ? b(Blocks.DIRT)
                            : s.chance(0.3F) ? b(Blocks.COBBLESTONE) : b(Blocks.STONE);
                    s.set(x + dx, y + dy, z + dz, state);
                }
            }
        }
    }

    /** A jagged spike of rock, thinning as it rises. */
    private static void spire(ClusterSite s, int x, int y, int z, int h) {
        for (int k = 0; k < h; k++) {
            BlockState rock = s.chance(0.4F) ? b(Blocks.ANDESITE) : s.chance(0.5F) ? b(Blocks.TUFF) : b(Blocks.STONE);
            s.set(x, y + k, z, rock);
            if (k < h / 2) {
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(s.random);
                s.set(x + d.getStepX(), y + k, z + d.getStepZ(), rock);
            }
        }
        s.floor.removeIf(p -> Math.abs(p.getX() - x) <= 1 && Math.abs(p.getZ() - z) <= 1);
    }

    // --- arch -------------------------------------------------------------------------------------------------------

    /** A shallow hollow under a rock arch thrown across it; things hang from the arch's underside. */
    private static void arch(ClusterSite s) {
        each(s, (x, z, u) -> {
            int g = s.ground(x, z);
            int y = Math.min(g, s.baseY - (int) Math.round(2.0D * (1.0D - smooth(0.0D, 0.9D, u))));
            y = (int) Math.round(Mth.lerp(smooth(0.8D, 1.0D, u), y, g));
            s.column(x, z, y, s.patch(x, z, 6.0D, b(Blocks.COARSE_DIRT), b(Blocks.GRAVEL), b(Blocks.MOSS_BLOCK),
                    b(Blocks.COARSE_DIRT)), b(Blocks.DIRT), 2);
            if (u < 0.9D) {
                s.addFloor(x, y + 1, z);
            }
        });
        double angle = s.random.nextDouble() * Math.PI;
        double ax = Math.cos(angle);
        double az = Math.sin(angle);
        double span = s.radius * 0.75D;
        int height = 6 + s.random.nextInt(3);
        int cx = s.centre.getX();
        int cz = s.centre.getZ();
        for (int x = cx - s.radius; x <= cx + s.radius; x++) {
            for (int z = cz - s.radius; z <= cz + s.radius; z++) {
                double along = (x - cx) * ax + (z - cz) * az;
                double across = -(x - cx) * az + (z - cz) * ax;
                if (Math.abs(along) > span || Math.abs(across) > 1.6D) {
                    continue;
                }
                int g = s.ground(x, z);
                double k = along / span;
                int top = s.baseY + (int) Math.round(height * Math.sqrt(1.0D - k * k));
                int bottom = Math.abs(k) > 0.8D ? g + 1 : top - 2;
                for (int y = bottom; y <= top; y++) {
                    s.set(x, y, z, s.patch(x + y * 7, z, 2.0D, b(Blocks.STONE), b(Blocks.ANDESITE),
                            b(Blocks.MOSSY_COBBLESTONE), b(Blocks.COBBLESTONE)));
                }
                int fx = x;
                int fz = z;
                s.floor.removeIf(p -> p.getX() == fx && p.getZ() == fz && bottom <= g + 2);
                if (bottom - 1 - g >= 3) {
                    s.addCeiling(x, bottom - 1, z);
                }
            }
        }
    }

    // --- pit --------------------------------------------------------------------------------------------------------

    /**
     * The Zone's heart: a dead, levelled clearing, a shaft dropping out of it ringed with weeping obsidian, bleached
     * ground round it.
     */
    private static void pit(ClusterSite s) {
        double shaft = 2.5D + s.random.nextDouble() * 1.5D;
        int depth = 14 + s.random.nextInt(8);
        int bottomLimit = s.level.getMinBuildHeight() + 6;
        each(s, (x, z, u) -> {
            int g = s.ground(x, z);
            int y = level(s, g, 0.85D * (1.0D - smooth(0.75D, 1.0D, u)));
            double dx = x - s.centre.getX();
            double dz = z - s.centre.getZ();
            double d = Math.sqrt(dx * dx + dz * dz) + s.noise(x, z, 2.0D) * 0.6D;
            if (d < shaft) {
                int floor = Math.max(bottomLimit, y - depth);
                s.column(x, z, floor, b(Blocks.CRYING_OBSIDIAN), b(Blocks.OBSIDIAN), 2);
            } else if (d < shaft + 2.0D) {
                s.column(x, z, y, s.chance(0.45F) ? b(Blocks.CRYING_OBSIDIAN) : s.chance(0.5F) ? b(Blocks.OBSIDIAN)
                        : b(Blocks.BLACKSTONE), b(Blocks.OBSIDIAN), 3);
            } else {
                s.column(x, z, y, s.patch(x, z, 7.0D, b(Blocks.TUFF), b(Blocks.SMOOTH_BASALT), b(Blocks.CALCITE),
                        b(Blocks.TUFF), b(Blocks.DEEPSLATE)), b(Blocks.STONE), 3);
                if (u < 0.9D) {
                    s.addFloor(x, y + 1, z);
                }
            }
        });
    }
}
