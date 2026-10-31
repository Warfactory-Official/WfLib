package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.site.SiteLayout.Bowl;
import com.wf.wflib.anomaly.site.SiteLayout.Column;
import com.wf.wflib.anomaly.site.SiteLayout.Palette;
import com.wf.wflib.anomaly.site.SiteLayout.Surface;
import com.wf.wflib.anomaly.site.SiteLayout.Trench;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.anomaly.api.AnomalyMix;
import com.wf.wflib.anomaly.api.SitePlan;

/**
 * The motifs' work, in two passes: {@link #shape} lays the ground (bowls, trenches, how flat), before any ruin claims
 * a pad; {@link #dress} sets the props (boulders, trees, spikes) and the anomaly spots on the ground as it ends up.
 * Every size and count comes off the site's radius and its own random, so one motif has many looks.
 */
final class SiteMotifs {
    private SiteMotifs() {
    }

    static void shape(SiteLayout l) {
        SitePlan p = l.plan;
        int r = p.radius();
        switch (p.motif()) {
            case CRATER -> {
                double br = r * (0.45D + 0.2D * l.random.nextDouble());
                double off = r * 0.15D;
                l.bowls.add(new Bowl(l.cx + (l.random.nextDouble() - 0.5D) * off, l.cz + (l.random.nextDouble() - 0.5D)
                        * off, br, l.baseY - (int) Math.round(br * (0.3D + 0.2D * l.random.nextDouble())),
                        2.0D + l.random.nextDouble() * 2.0D, false));
                l.bare = 0.25D;
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.GRAVEL, Blocks.COARSE_DIRT);
                l.bowlPalette = p.tier() >= 3 ? Palette.of(Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.TUFF, Blocks.BLACKSTONE)
                        : Palette.of(Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.TUFF, Blocks.COARSE_DIRT);
                l.flatten = 0.3D;
            }
            case CRATER_FIELD -> {
                int n = 3 + l.random.nextInt(4 + p.tier());
                for (int i = 0; i < n * 4 && l.bowls.size() < n; i++) {
                    double a = l.random.nextDouble() * Mth.TWO_PI;
                    double out = Math.sqrt(l.random.nextDouble()) * r * 0.75D;
                    double x = l.cx + Math.cos(a) * out;
                    double z = l.cz + Math.sin(a) * out;
                    double br = 3.0D + l.random.nextDouble() * (3.0D + p.tier());
                    boolean clear = true;
                    for (Bowl b : l.bowls) {
                        clear &= Math.hypot(b.x() - x, b.z() - z) > b.r() + br + 1.0D;
                    }
                    if (clear) {
                        int g = l.terrain.ground(Mth.floor(x), Mth.floor(z));
                        l.bowls.add(new Bowl(x, z, br, g - (int) Math.round(br * 0.45D), 1.0D + l.random.nextDouble(),
                                false));
                    }
                }
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.DIRT, Blocks.ROOTED_DIRT);
                l.flatten = 0.2D;
                l.bare = 0.2D;
            }
            case RAVINE -> {
                l.trenches.add(trench(l, r * 0.95D, 4.0D + p.tier() * 0.6D + l.random.nextDouble() * 2.0D,
                        12 + 2 * p.tier() + l.random.nextInt(5), 2, p.tier() >= 3 ? Blocks.MAGMA_BLOCK : Blocks.GRAVEL,
                        Blocks.STONE));
                if (l.random.nextFloat() < 0.4F) {
                    l.trenches.add(trench(l, r * 0.5D, 1.5D + l.random.nextDouble(), 5 + p.tier(), 1, Blocks.GRAVEL,
                            Blocks.STONE));
                }
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.COARSE_DIRT,
                        Blocks.ANDESITE);
                l.flatten = 0.1D;
                l.bare = 0.15D;
            }
            case FISSURES -> {
                int n = 3 + l.random.nextInt(3) + p.tier() / 2;
                for (int i = 0; i < n; i++) {
                    l.trenches.add(trench(l, r * (0.4D + 0.5D * l.random.nextDouble()), 0.6D + l.random.nextDouble()
                            * 0.8D, 4 + l.random.nextInt(3 + p.tier()), 0, p.tier() >= 2 ? Blocks.MAGMA_BLOCK
                            : Blocks.BASALT, Blocks.BLACKSTONE));
                }
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.COARSE_DIRT,
                        Blocks.TUFF);
                l.flatten = 0.4D;
                l.bare = 0.65D;
            }
            case GLASS_FIELD -> {
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.TUFF, Blocks.COBBLED_DEEPSLATE, Blocks.COARSE_DIRT,
                        Blocks.SMOOTH_BASALT);
                l.flatten = 0.8D;
                l.bare = 0.75D;
            }
            case BOG -> {
                l.bowls.add(new Bowl(l.cx, l.cz, r * 0.85D, l.baseY - 2, 0.0D, false));
                l.palette = Palette.of(Blocks.MUD, Blocks.MUD, Blocks.PACKED_MUD, Blocks.MUD, Blocks.MOSS_BLOCK);
                l.flatten = 0.6D;
                l.bare = 0.85D;
            }
            case BOULDER_FIELD -> {
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.STONE, Blocks.COARSE_DIRT);
                l.flatten = 0.3D;
            }
            case SINKHOLE -> {
                double br = r * (0.35D + 0.1D * l.random.nextDouble());
                l.bowls.add(new Bowl(l.cx, l.cz, br, l.baseY - 10 - p.tier() * 2 - l.random.nextInt(4), 1.0D, true));
                l.bowlPalette = Palette.of(Blocks.GRAVEL, Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.GRAVEL, Blocks.ROOTED_DIRT);
                l.flatten = 0.25D;
            }
            case DEAD_FOREST -> {
                l.palette = Palette.of(Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL);
                l.flatten = 0.15D;
                l.bare = 0.8D;
            }
            case UPHEAVAL -> {
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.GRAVEL, Blocks.ROOTED_DIRT);
                l.flatten = 0.2D;
            }
            case ARCHES -> {
                l.bowls.add(new Bowl(l.cx, l.cz, r * 0.7D, l.baseY - 3, 0.0D, false));
                l.palette = Palette.of(Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT);
                l.flatten = 0.4D;
            }
            case PIT -> {
                l.bowls.add(new Bowl(l.cx, l.cz, 3.5D + l.random.nextDouble() * 1.5D,
                        Math.max(l.baseY - 40, -50), 0.0D, true));
                l.bowlPalette = Palette.of(Blocks.OBSIDIAN, Blocks.BLACKSTONE, Blocks.DEEPSLATE);
                l.palette = Palette.of(Blocks.TUFF, Blocks.SMOOTH_BASALT, Blocks.CALCITE, Blocks.TUFF, Blocks.DEEPSLATE);
                l.under = Blocks.STONE.defaultBlockState();
                l.flatten = 0.85D;
                l.bare = 0.9D;
            }
        }
    }

    /** A crack or cleft: through near the middle, wandering sideways. */
    private static Trench trench(SiteLayout l, double length, double half, int depth, int ledges, Block bottom,
                                 Block lip) {
        double angle = l.random.nextDouble() * Math.PI;
        double ox = l.cx + 0.5D + (l.random.nextDouble() - 0.5D) * l.plan.radius() * 0.3D;
        double oz = l.cz + 0.5D + (l.random.nextDouble() - 0.5D) * l.plan.radius() * 0.3D;
        double ax = Math.cos(angle);
        double az = Math.sin(angle);
        List<double[]> pts = new ArrayList<>();
        double side = 0.0D;
        for (double t = -length; t <= length; t += 3.0D) {
            side = Mth.clamp(side + (l.random.nextDouble() - 0.5D) * 2.4D, -half * 1.5D - 2.0D, half * 1.5D + 2.0D);
            pts.add(new double[] {ox + ax * t - az * side, oz + az * t + ax * side});
        }
        return new Trench(pts, half, depth, ledges, bottom.defaultBlockState(), lip.defaultBlockState());
    }

    // --- dress ------------------------------------------------------------------------------------------------------

    static void dress(SiteLayout l) {
        SitePlan p = l.plan;
        int r = p.radius();
        switch (p.motif()) {
            case CRATER -> {
                Bowl b = l.bowls.get(0);
                if (l.random.nextFloat() < 0.35F) {
                    spire(l, Mth.floor(b.x()), Mth.floor(b.z()), 3 + l.random.nextInt(3));
                }
                ring(l, b, 1.1D, 1.6D, 3 + p.tier(), (x, z) -> boulder(l, x, z, 1.0D + l.random.nextDouble()));
                grid(l, (x, z, c) -> c.kind() == Surface.BOWL ? 4.0D : c.y() > l.baseY ? 1.5D : 1.0D);
            }
            case CRATER_FIELD -> grid(l, (x, z, c) -> c.kind() == Surface.BOWL ? 3.0D : 1.0D);
            case RAVINE -> {
                Trench t = l.trenches.get(0);
                if (l.random.nextFloat() < 0.35F) {
                    bridge(l, t);
                }
                grid(l, (x, z, c) -> {
                    if (c.kind() != Surface.TRENCH) {
                        return 1.0D;
                    }
                    // The bottom most, then the ledges.
                    return c.y() <= l.baseY - t.depth() + 2 ? 4.0D : 3.0D;
                });
            }
            case FISSURES -> {
                int vents = 3 + l.random.nextInt(3 + p.tier());
                for (int[] v : scatter(l, vents, 5.0D, 0.85D)) {
                    vent(l, v[0], v[1]);
                }
                grid(l, (x, z, c) -> nearTrench(l, x, z, 3.0D) ? 3.0D : 1.0D);
            }
            case GLASS_FIELD -> {
                java.util.Set<Long> streak = new java.util.HashSet<>();
                for (int i = 3 + l.random.nextInt(3); i > 0; i--) {
                    lichtenberg(l, l.cx, l.cz, l.random.nextDouble() * Mth.TWO_PI, r * 0.9D, streak, 2);
                }
                for (long s : streak) {
                    int x = net.minecraft.world.level.ChunkPos.getX(s);
                    int z = net.minecraft.world.level.ChunkPos.getZ(s);
                    if (l.u(x, z) < 0.95D) {
                        l.put(x, l.height(x, z), z, Blocks.POLISHED_DEEPSLATE);
                    }
                }
                for (int[] f : scatter(l, 6 + 2 * p.tier(), 3.0D, 0.85D)) {
                    int y = l.height(f[0], f[1]);
                    for (int k = 1, h = 1 + l.random.nextInt(3); k <= h; k++) {
                        l.put(f[0], y + k, f[1], Blocks.TINTED_GLASS);
                    }
                }
                grid(l, (x, z, c) -> 2.0D);
            }
            case BOG -> {
                for (int x = l.cx - r; x <= l.cx + r; x++) {
                    for (int z = l.cz - r; z <= l.cz + r; z++) {
                        double u = l.u(x, z);
                        if (u > 0.75D || l.inPad(x, z)) {
                            continue;
                        }
                        int y = l.height(x, z);
                        if (l.noise(x + 1000, z, 5.0D) > 0.15D && contained(l, x, z, y)) {
                            l.put(x, y, z, Blocks.WATER);
                        } else if (SiteLayout.hash(x, z, 31) < 0.05D) {
                            l.put(x, y + 1, z, Blocks.DEAD_BUSH);
                        } else if (p.tier() >= 2 && SiteLayout.hash(x, z, 32) < 0.006D) {
                            bones(l, x, y, z);
                        }
                    }
                }
                for (int[] t : scatter(l, 2 + p.tier(), 6.0D, 0.8D)) {
                    deadTree(l, t[0], t[1], false);
                }
                grid(l, (x, z, c) -> l.hasProp(x, c.y(), z) ? 0.0D : 2.0D);
            }
            case BOULDER_FIELD -> {
                for (int[] b : scatter(l, 8 + 3 * p.tier(), 4.0D, 0.85D)) {
                    if (l.random.nextFloat() < 0.35F) {
                        spire(l, b[0], b[1], 3 + l.random.nextInt(3 + p.tier()));
                    } else {
                        boulder(l, b[0], b[1], 1.5D + l.random.nextDouble() * 1.5D);
                    }
                }
                // The gaps between rocks: where you have to walk, so where they wait.
                grid(l, (x, z, c) -> crowded(l, x, c.y(), z) ? 4.0D : 1.0D);
            }
            case SINKHOLE -> {
                Bowl b = l.bowls.get(0);
                for (int x = Mth.floor(b.x() - b.r()); x <= b.x() + b.r(); x++) {
                    for (int z = Mth.floor(b.z() - b.r()); z <= b.z() + b.r(); z++) {
                        double d = Math.hypot(x + 0.5D - b.x(), z + 0.5D - b.z()) / b.r();
                        if (d < 0.45D) {
                            l.put(x, l.height(x, z), z, Blocks.WATER);
                        }
                    }
                }
                rubbleRamp(l, b);
                grid(l, (x, z, c) -> c.kind() == Surface.BOWL ? 4.0D : 1.0D);
            }
            case DEAD_FOREST -> {
                for (int[] t : scatter(l, 8 + 3 * p.tier(), 4.5D, 0.9D)) {
                    if (l.random.nextFloat() < 0.15F) {
                        fallenLog(l, t[0], t[1]);
                    } else {
                        deadTree(l, t[0], t[1], true);
                    }
                }
                grid(l, (x, z, c) -> 1.5D);
            }
            case UPHEAVAL -> {
                for (int[] b : scatter(l, 3 + p.tier(), 7.0D, 0.75D)) {
                    double pr = 1.5D + l.random.nextDouble() * 1.2D;
                    int g = l.height(b[0], b[1]);
                    // The pit it came out of.
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx * dx + dz * dz <= pr * pr) {
                                l.put(b[0] + dx, g, b[1] + dz, Blocks.AIR);
                            }
                        }
                    }
                    floatingBoulder(l, b[0], g + 6 + l.random.nextInt(4 + p.tier()), b[1], pr);
                }
                for (int[] s : scatter(l, 2 + p.tier(), 5.0D, 0.85D)) {
                    slab(l, s[0], s[1]);
                }
                grid(l, (x, z, c) -> crowded(l, x, c.y(), z) ? 3.0D : 1.0D);
            }
            case ARCHES -> {
                for (int i = 1 + l.random.nextInt(2); i > 0; i--) {
                    arch(l);
                }
                grid(l, (x, z, c) -> c.kind() == Surface.BOWL ? 2.0D : 1.0D);
            }
            case PIT -> {
                Bowl b = l.bowls.get(0);
                for (int x = Mth.floor(b.x() - b.r() - 3); x <= b.x() + b.r() + 3; x++) {
                    for (int z = Mth.floor(b.z() - b.r() - 3); z <= b.z() + b.r() + 3; z++) {
                        double d = Math.hypot(x + 0.5D - b.x(), z + 0.5D - b.z());
                        if (d >= b.r() && d < b.r() + 2.5D) {
                            double h = SiteLayout.hash(x, z, 41);
                            l.put(x, l.height(x, z), z, h < 0.45D ? Blocks.CRYING_OBSIDIAN : h < 0.75D ? Blocks.OBSIDIAN
                                    : Blocks.BLACKSTONE);
                        }
                    }
                }
                l.put(Mth.floor(b.x()), b.floorY(), Mth.floor(b.z()), Blocks.CRYING_OBSIDIAN);
                grid(l, (x, z, c) -> c.kind() == Surface.BOWL ? 0.0D
                        : Math.hypot(x - b.x(), z - b.z()) < b.r() + 6.0D ? 4.0D : 1.0D);
            }
        }
    }

    @FunctionalInterface
    interface Weight {
        double at(int x, int z, Column c);
    }

    /** Anomaly spots every 2 blocks inside the outline, off pads, weighted by {@code w} (0: none). */
    static void grid(SiteLayout l, Weight w) {
        int r = l.plan.radius();
        for (int x = l.cx - r; x <= l.cx + r; x += 2) {
            for (int z = l.cz - r; z <= l.cz + r; z += 2) {
                if (l.u(x, z) > 0.88D || l.inPad(x, z) || l.terrain.wet(x, z)) {
                    continue;
                }
                Column c = l.column(x, z);
                double weight = w.at(x, z, c);
                if (weight > 0.0D && !l.hasProp(x, c.y() + 1, z) && !l.hasProp(x, c.y(), z)) {
                    l.spot(x, z, weight);
                }
            }
        }
    }

    /** Up to {@code n} columns at least {@code spacing} apart inside {@code within} of the outline, off pads. */
    static List<int[]> scatter(SiteLayout l, int n, double spacing, double within) {
        List<int[]> out = new ArrayList<>();
        int r = l.plan.radius();
        for (int tries = 0; tries < n * 25 && out.size() < n; tries++) {
            int x = l.cx + l.random.nextInt(2 * r + 1) - r;
            int z = l.cz + l.random.nextInt(2 * r + 1) - r;
            if (l.u(x, z) > within || l.inPad(x, z) || l.column(x, z).kind() == Surface.TRENCH) {
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

    /** {@code n} points round a bowl between {@code from} and {@code to} radii. */
    private static void ring(SiteLayout l, Bowl b, double from, double to, int n, java.util.function.BiConsumer<Integer,
            Integer> at) {
        for (int i = 0; i < n; i++) {
            double a = l.random.nextDouble() * Mth.TWO_PI;
            double out = b.r() * (from + (to - from) * l.random.nextDouble());
            int x = Mth.floor(b.x() + Math.cos(a) * out);
            int z = Mth.floor(b.z() + Math.sin(a) * out);
            if (l.u(x, z) < 0.95D && !l.inPad(x, z)) {
                at.accept(x, z);
            }
        }
    }

    private static boolean nearTrench(SiteLayout l, int x, int z, double within) {
        for (Trench t : l.trenches) {
            if (t.nearest(x + 0.5D, z + 0.5D)[0] < t.half() + within) {
                return true;
            }
        }
        return false;
    }

    /** Two or more props within 3 blocks: a gap between rocks. */
    private static boolean crowded(SiteLayout l, int x, int y, int z) {
        int n = 0;
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                if ((dx != 0 || dz != 0) && (l.hasProp(x + dx, y + 1, z + dz) || l.hasProp(x + dx, y + 2, z + dz))) {
                    n++;
                }
            }
        }
        return n >= 2;
    }

    // --- props ------------------------------------------------------------------------------------------------------

    static BlockState rock(SiteLayout l, int x, int y, int z) {
        double h = SiteLayout.hash(x * 7 + y, z, 51);
        return (h < 0.35D ? Blocks.ANDESITE : h < 0.55D ? Blocks.COBBLESTONE : h < 0.7D ? Blocks.TUFF : Blocks.STONE)
                .defaultBlockState();
    }

    static void boulder(SiteLayout l, int x, int z, double r) {
        int y = l.height(x, z);
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -1; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.3D + dz * dz) + SiteLayout.hash(x + dx, z + dz, dy) * 0.5D;
                    if (d <= r) {
                        l.put(x + dx, y + dy + 1, z + dz, rock(l, x + dx, y + dy, z + dz));
                    }
                }
            }
        }
    }

    static void spire(SiteLayout l, int x, int z, int h) {
        int y = l.height(x, z) + 1;
        for (int k = 0; k < h; k++) {
            l.put(x, y + k, z, rock(l, x, y + k, z));
            if (k < h / 2) {
                Direction d = Direction.from2DDataValue((int) (SiteLayout.hash(x, z + k, 61) * 4));
                l.put(x + d.getStepX(), y + k, z + d.getStepZ(), rock(l, x + 1, y + k, z));
            }
        }
    }

    /** A vent: a Steam Vent anomaly over magma ringed with basalt; its idle plume is the smoke. */
    private static void vent(SiteLayout l, int x, int z) {
        int y = l.height(x, z);
        l.put(x, y, z, Blocks.MAGMA_BLOCK);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            l.put(x + d.getStepX(), y, z + d.getStepZ(), Blocks.BASALT);
        }
        l.anomalies.add(new SiteLayout.Placed(AnomalyMix.Kind.STEAM_VENT, new net.minecraft.core.BlockPos(x, y + 1, z)));
    }

    /** A branching scorch: a random walk out, splitting now and then. */
    private static void lichtenberg(SiteLayout l, double x, double z, double angle, double length,
                                    java.util.Set<Long> out, int splits) {
        for (double t = 0.0D; t < length; t += 1.0D) {
            angle += (l.random.nextDouble() - 0.5D) * 0.7D;
            x += Math.cos(angle);
            z += Math.sin(angle);
            out.add(net.minecraft.world.level.ChunkPos.asLong(Mth.floor(x), Mth.floor(z)));
            if (splits > 0 && l.random.nextFloat() < 0.06F) {
                lichtenberg(l, x, z, angle + (l.random.nextBoolean() ? 0.8D : -0.8D), (length - t) * 0.6D, out,
                        splits - 1);
            }
        }
    }

    /** Water only where all four sides are as high, so nothing spills when it updates. */
    private static boolean contained(SiteLayout l, int x, int z, int y) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (l.height(x + d.getStepX(), z + d.getStepZ()) < y) {
                return false;
            }
        }
        return true;
    }

    private static void bones(SiteLayout l, int x, int y, int z) {
        Direction.Axis axis = SiteLayout.hash(x, z, 33) < 0.5D ? Direction.Axis.X : Direction.Axis.Z;
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        for (int k = 0; k < 3; k++) {
            l.put(axis == Direction.Axis.X ? x + k : x, y, axis == Direction.Axis.Z ? z + k : z, bone);
        }
    }

    /** A bark-stripped dead tree; with {@code ceilings}, its boughs are spots for things that hang. */
    private static void deadTree(SiteLayout l, int x, int z, boolean ceilings) {
        BlockState log = (l.random.nextFloat() < 0.6F ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG)
                .defaultBlockState();
        int y = l.height(x, z) + 1;
        int h = 5 + l.random.nextInt(4);
        for (int k = 0; k < h; k++) {
            l.put(x, y + k, z, log);
        }
        List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        net.minecraft.Util.shuffle(dirs, l.random);
        for (int i = 0, n = 2 + l.random.nextInt(2); i < n; i++) {
            Direction d = dirs.get(i);
            int by = y + h - 1 - l.random.nextInt(3);
            int len = 2 + l.random.nextInt(2);
            BlockState bough = log.setValue(RotatedPillarBlock.AXIS, d.getAxis());
            for (int k = 1; k <= len; k++) {
                int bx = x + d.getStepX() * k;
                int bz = z + d.getStepZ() * k;
                l.put(bx, by, bz, bough);
                if (ceilings && k >= 2 && by - 1 - l.height(bx, bz) >= 3) {
                    l.ceiling(bx, by - 1, bz, 3.0D);
                }
            }
            if (l.random.nextFloat() < 0.5F) {
                l.put(x + d.getStepX() * len, by + 1, z + d.getStepZ() * len, log);
            }
        }
    }

    private static void fallenLog(SiteLayout l, int x, int z) {
        Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(l.random);
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis());
        for (int k = 0, n = 4 + l.random.nextInt(4); k < n; k++) {
            int bx = x + d.getStepX() * k;
            int bz = z + d.getStepZ() * k;
            l.put(bx, l.height(bx, bz) + 1, bz, log);
        }
    }

    /** A clod of earth over stone, the grass still on it, ragged at the bottom. */
    private static void floatingBoulder(SiteLayout l, int x, int y, int z, double r) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6D + dz * dz) + SiteLayout.hash(x + dx, z + dz, dy) * 0.4D;
                    if (d > r) {
                        continue;
                    }
                    Block b = dy == ri - 1 || (dy >= 0 && d > r - 0.8D) ? Blocks.GRASS_BLOCK : dy >= 0 ? Blocks.DIRT
                            : SiteLayout.hash(x + dx, z, dy + 9) < 0.3D ? Blocks.COBBLESTONE : Blocks.STONE;
                    l.put(x + dx, y + dy, z + dz, b);
                }
            }
        }
    }

    /** A slab of ground prised up and leaning: a tilted plate of stone and earth. */
    private static void slab(SiteLayout l, int x, int z) {
        Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(l.random);
        Direction side = d.getClockWise();
        int len = 4 + l.random.nextInt(3);
        int wid = 2 + l.random.nextInt(2);
        int y = l.height(x, z);
        for (int a = 0; a < len; a++) {
            for (int b = -wid; b <= wid; b++) {
                int bx = x + d.getStepX() * a + side.getStepX() * b;
                int bz = z + d.getStepZ() * a + side.getStepZ() * b;
                int by = y + a * 2 / 3;
                l.put(bx, by, bz, rock(l, bx, by, bz));
                l.put(bx, by + 1, bz, a == len - 1 ? Blocks.STONE : Blocks.DIRT);
            }
        }
    }

    /** A natural rock bridge over a ravine's middle. */
    private static void bridge(SiteLayout l, Trench t) {
        double[] mid = t.points().get(t.points().size() / 2);
        double[] next = t.points().get(t.points().size() / 2 + 1);
        double ax = next[0] - mid[0];
        double az = next[1] - mid[1];
        double len = Math.hypot(ax, az);
        double px = -az / len;
        double pz = ax / len;
        int y = l.baseY;
        for (double s = -t.half() - 2.0D; s <= t.half() + 2.0D; s += 0.5D) {
            for (double w = -1.0D; w <= 1.0D; w += 1.0D) {
                int x = Mth.floor(mid[0] + px * s + ax / len * w);
                int z = Mth.floor(mid[1] + pz * s + az / len * w);
                l.put(x, y, z, rock(l, x, y, z));
                l.put(x, y - 1, z, rock(l, x, y - 1, z));
            }
        }
    }

    /** A slope of rubble down one side of a sinkhole: the way down. */
    private static void rubbleRamp(SiteLayout l, Bowl b) {
        double a = l.random.nextDouble() * Mth.TWO_PI;
        for (double s = 0.0D; s <= b.r() * 1.1D; s += 0.5D) {
            for (double w = -1.5D; w <= 1.5D; w += 0.5D) {
                int x = Mth.floor(b.x() + Math.cos(a) * s - Math.sin(a) * w);
                int z = Mth.floor(b.z() + Math.sin(a) * s + Math.cos(a) * w);
                int top = (int) Mth.lerp(s / (b.r() * 1.1D), b.floorY() + 1, l.baseY);
                for (int y = l.height(x, z) + 1; y <= top; y++) {
                    l.put(x, y, z, SiteLayout.hash(x, z, y) < 0.5D ? Blocks.GRAVEL : Blocks.COBBLESTONE);
                }
            }
        }
    }

    /** A rock arch across the hollow; its underside is ceiling for things that hang. */
    private static void arch(SiteLayout l) {
        double angle = l.random.nextDouble() * Math.PI;
        double ax = Math.cos(angle);
        double az = Math.sin(angle);
        double span = l.plan.radius() * (0.5D + 0.25D * l.random.nextDouble());
        int height = 7 + l.random.nextInt(4);
        double ox = l.cx + (l.random.nextDouble() - 0.5D) * l.plan.radius() * 0.4D;
        double oz = l.cz + (l.random.nextDouble() - 0.5D) * l.plan.radius() * 0.4D;
        int r = l.plan.radius();
        for (int x = l.cx - r; x <= l.cx + r; x++) {
            for (int z = l.cz - r; z <= l.cz + r; z++) {
                double along = (x - ox) * ax + (z - oz) * az;
                double across = -(x - ox) * az + (z - oz) * ax;
                if (Math.abs(along) > span || Math.abs(across) > 1.6D) {
                    continue;
                }
                int g = l.height(x, z);
                double k = along / span;
                int top = l.baseY + (int) Math.round(height * Math.sqrt(1.0D - k * k));
                int bottom = Math.abs(k) > 0.8D ? g + 1 : top - 2;
                for (int y = bottom; y <= top; y++) {
                    double h = SiteLayout.hash(x, z, y);
                    l.put(x, y, z, h < 0.4D ? Blocks.STONE : h < 0.65D ? Blocks.ANDESITE : h < 0.85D
                            ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                }
                if (bottom - 1 - g >= 3) {
                    l.ceiling(x, bottom - 1, z, 3.0D);
                }
            }
        }
    }
}
