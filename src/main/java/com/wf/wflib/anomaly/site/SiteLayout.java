package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.site.AnomalyMix.Kind;
import com.wf.wflib.anomaly.site.AnomalyMix.Loot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where everything in one site goes, worked out from its plan and the untouched terrain alone, so every chunk the
 * site crosses works out the same and paints only its own part ({@link SitePainter}). Terrain shapes (bowls,
 * trenches, pads) define the new ground ({@link #height}); props are blocks set on it; spots are where anomalies may
 * go, weighted by how much they matter there (a crater's floor, a ravine's bottom, a doorway).
 */
public final class SiteLayout {

    /** The ground as generated, before the site: the noise terrain's top, and whether water lies on it. */
    public interface Terrain {
        int ground(int x, int z);

        boolean wet(int x, int z);
    }

    /** Vanilla templates' sizes (null: no such template), for ruins; tests pass one that knows none. */
    public interface Templates {
        @Nullable
        net.minecraft.core.Vec3i size(ResourceLocation id);
    }



    /** A bowl: down to {@code floorY} at the middle (a paraboloid), a rim {@code rim} high just outside it. */
    public record Bowl(double x, double z, double r, int floorY, double rim, boolean steep) {
    }

    /**
     * A trench along {@code points}: {@code half} blocks either side of its line at the middle (narrowing to the tips),
     * {@code depth} deep, its walls stepped into {@code ledges} ledges.
     */
    public record Trench(List<double[]> points, double half, int depth, int ledges, BlockState bottom, BlockState lip) {
        /** {distance to the line, how far along it 0 middle .. 1 tips}. */
        double[] nearest(double x, double z) {
            double best = Double.MAX_VALUE;
            double along = 1.0D;
            for (int i = 0; i + 1 < points.size(); i++) {
                double[] a = points.get(i);
                double[] b = points.get(i + 1);
                double vx = b[0] - a[0];
                double vz = b[1] - a[1];
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

    /** A level patch for a ruin to stand on: ground set to {@code y} over the rectangle, blending out over 3. */
    public record Pad(int minX, int minZ, int maxX, int maxZ, int y) {
    }

    /** A vanilla template set into the site as a ruin (rotted to {@code integrity}, mossed to {@code moss}). */
    public record Ruin(ResourceLocation template, BlockPos origin, Rotation rotation, float integrity, float moss) {
    }

    /** Where an anomaly may go: a floor cell (on the ground) or a ceiling cell (under an overhang). */
    public record Spot(int x, int y, int z, double weight, boolean ceiling) {
    }

    public record Placed(Kind kind, BlockPos pos) {
    }

    public record LootSpot(Loot loot, BlockPos pos, BlockPos home) {
    }

    final SitePlan plan;
    final SiteStyle style;
    final Templates templates;
    final int cx;
    final int cz;
    final Terrain terrain;
    /** Median ground over the site: what flattening pulls toward. */
    final int baseY;
    final SimplexNoise noise;
    final RandomSource random;
    /** How much the ground inside pulls toward {@link #baseY}: 0 not at all, 1 flat. */
    double flatten;
    /**
     * Share of the plain ground that goes bare (the motif's palette) rather than keeping the land's own surface; round
     * every anomaly it goes bare anyway, a scorch.
     */
    double bare = 0.3D;
    final List<Bowl> bowls = new ArrayList<>();
    final List<Trench> trenches = new ArrayList<>();
    final List<Pad> pads = new ArrayList<>();
    final List<Ruin> ruins = new ArrayList<>();
    /** Props by position (later ones over earlier). */
    final Map<BlockPos, BlockState> props = new HashMap<>();
    final List<Spot> spots = new ArrayList<>();
    final List<Placed> anomalies = new ArrayList<>();
    final List<LootSpot> loot = new ArrayList<>();
    /** Surface for the motif's own ground; bowls, trenches and pads have their own. */
    Palette palette = Palette.of(Blocks.COARSE_DIRT);
    /** A bowl's floor, if not the palette. */
    @Nullable
    Palette bowlPalette;
    BlockState under = Blocks.DIRT.defaultBlockState();
    private final float[] wobbleAmp = new float[3];
    private final float[] wobblePhase = new float[3];

    SiteLayout(SitePlan plan, int cx, int cz, Terrain terrain, int baseY, SiteStyle style, Templates templates) {
        this.plan = plan;
        this.style = style;
        this.templates = templates;
        this.cx = cx;
        this.cz = cz;
        this.terrain = terrain;
        this.baseY = baseY;
        this.random = RandomSource.create(plan.variant());
        this.noise = new SimplexNoise(RandomSource.create(plan.variant() ^ 0x9E3779B97F4A7C15L));
        for (int k = 0; k < wobbleAmp.length; k++) {
            wobbleAmp[k] = (0.03F + random.nextFloat() * 0.06F) / (k + 1);
            wobblePhase[k] = random.nextFloat() * Mth.TWO_PI;
        }
    }

    /** The whole layout for a plan at a centre: terrain shape, ruin, props, then anomalies and artefacts on them. */
    public static SiteLayout build(SitePlan plan, int cx, int cz, Terrain terrain, SiteStyle style, Templates templates) {
        SiteLayout l = new SiteLayout(plan, cx, cz, terrain, medianGround(terrain, cx, cz, plan.radius()), style,
                templates);
        SiteMotifs.shape(l);
        if (plan.overlay() != null) {
            SiteRuins.plan(l, plan.overlay());
        }
        SiteMotifs.dress(l);
        l.placeAnomalies();
        l.placeLoot();
        return l;
    }

    static int medianGround(Terrain t, int cx, int cz, int r) {
        List<Integer> h = new ArrayList<>();
        for (int dx = -r; dx <= r; dx += 4) {
            for (int dz = -r; dz <= r; dz += 4) {
                if (dx * dx + dz * dz <= r * r) {
                    h.add(t.ground(cx + dx, cz + dz));
                }
            }
        }
        h.sort(null);
        return h.get(h.size() / 2);
    }

    public List<Placed> anomalies() {
        return anomalies;
    }

    public List<LootSpot> loot() {
        return loot;
    }

    // --- geometry ---------------------------------------------------------------------------------------------------

    public int radius() {
        return plan.radius();
    }

    /** Out from the middle in outlines: 1 on the site's edge, which wobbles inward (2-4 slow lobes). */
    public double u(double x, double z) {
        double dx = x - cx;
        double dz = z - cz;
        double angle = Math.atan2(dz, dx);
        double edge = 1.0D;
        for (int k = 0; k < wobbleAmp.length; k++) {
            edge += wobbleAmp[k] * (Math.sin((k + 2) * angle + wobblePhase[k]) - 1.0D);
        }
        return Math.sqrt(dx * dx + dz * dz) / (plan.radius() * edge);
    }

    double noise(double x, double z, double scale) {
        return noise.getValue(x / scale, z / scale);
    }

    /** A hash of a position, 0..1: per-block randomness that is the same in every chunk's pass. */
    static double hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    static double smooth(double edge0, double edge1, double v) {
        double t = Mth.clamp((v - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    /** What shaped a column: the motif's plain ground, a bowl, a trench, a ruin's pad. */
    enum Surface { PLAIN, BOWL, TRENCH, PAD }

    /** The new ground's top at a column. */
    public int height(int x, int z) {
        return column(x, z).y;
    }

    record Column(int y, Surface kind, @Nullable Bowl bowl, @Nullable Trench trench) {
    }

    Column column(int x, int z) {
        double u = u(x + 0.5D, z + 0.5D);
        int g = terrain.ground(x, z);
        double y = g;
        if (u < 1.0D) {
            y = Mth.lerp(flatten * (1.0D - smooth(0.7D, 1.0D, u)), g, baseY);
        }
        Surface kind = Surface.PLAIN;
        Bowl inBowl = null;
        Trench inTrench = null;
        for (Pad p : pads) {
            double ox = Math.max(Math.max(p.minX() - x, x - p.maxX()), 0);
            double oz = Math.max(Math.max(p.minZ() - z, z - p.maxZ()), 0);
            double out = Math.sqrt(ox * ox + oz * oz);
            if (out < 3.0D) {
                y = Mth.lerp(1.0D - smooth(0.0D, 3.0D, out), y, p.y());
                if (out == 0.0D) {
                    kind = Surface.PAD;
                }
            }
        }
        for (Bowl b : bowls) {
            double d = Math.sqrt((x + 0.5D - b.x()) * (x + 0.5D - b.x()) + (z + 0.5D - b.z()) * (z + 0.5D - b.z()))
                    / b.r() + 0.06D * noise(x, z, 3.0D);
            if (d < 1.0D) {
                double depth = b.steep() ? (1.0D - Math.pow(d, 6.0D)) : (1.0D - d * d);
                double floor = b.floorY() + (y - b.floorY()) * (1.0D - depth);
                if (floor < y) {
                    y = floor;
                    kind = Surface.BOWL;
                    inBowl = b;
                }
            } else if (d < 1.4D && b.rim() > 0.0D) {
                y += b.rim() * Math.sin(Math.PI * (d - 1.0D) / 0.4D) * (0.7D + 0.3D * noise(x, z, 4.0D));
            }
        }
        for (Trench t : trenches) {
            double[] near = t.nearest(x + 0.5D, z + 0.5D);
            double half = t.half() * (1.0D - 0.7D * near[1] * near[1]);
            if (near[0] < half + 0.5D) {
                // Stepped walls: each ledge a band of the width, the bottom the middle third.
                double k = Mth.clamp(near[0] / half, 0.0D, 1.0D);
                double steps = t.ledges() + 1;
                double cut = t.depth() * (1.0D - 0.6D * near[1]) * (1.0D - Math.floor(k * steps) / steps);
                if (cut >= 1.0D) {
                    y = Math.min(y, Math.round(y) - cut);
                    kind = Surface.TRENCH;
                    inTrench = t;
                }
            }
        }
        return new Column((int) Math.round(y), kind, inBowl, inTrench);
    }

    /** Plain ground here shows the motif's palette (a scorch round an anomaly, or the motif's bare share), not grass. */
    boolean bare(int x, int z) {
        for (Placed p : anomalies) {
            double d = Math.hypot(x - p.pos().getX(), z - p.pos().getZ());
            if (d < 4.5D + 1.5D * noise(x, z, 2.0D)) {
                return true;
            }
        }
        return (noise(x - 500, z + 500, 9.0D) + 1.0D) * 0.5D < bare;
    }

    // --- props ------------------------------------------------------------------------------------------------------

    void put(int x, int y, int z, BlockState state) {
        props.put(new BlockPos(x, y, z), state);
    }

    void put(int x, int y, int z, net.minecraft.world.level.block.Block block) {
        put(x, y, z, block.defaultBlockState());
    }

    boolean hasProp(int x, int y, int z) {
        return props.containsKey(new BlockPos(x, y, z));
    }

    /** A prop there that is not air (air props are carved-out space: a pit, a bunker's room). */
    boolean propSolid(int x, int y, int z) {
        BlockState s = props.get(new BlockPos(x, y, z));
        return s != null && !s.isAir();
    }

    /** Inside a pad proper (no margin). */
    boolean inPadStrict(int x, int z) {
        for (Pad p : pads) {
            if (x >= p.minX() && x <= p.maxX() && z >= p.minZ() && z <= p.maxZ()) {
                return true;
            }
        }
        return false;
    }

    boolean inPad(int x, int z) {
        for (Pad p : pads) {
            if (x >= p.minX() - 1 && x <= p.maxX() + 1 && z >= p.minZ() - 1 && z <= p.maxZ() + 1) {
                return true;
            }
        }
        return false;
    }

    void spot(int x, int z, double weight) {
        spots.add(new Spot(x, height(x, z) + 1, z, weight, false));
    }

    void ceiling(int x, int y, int z, double weight) {
        spots.add(new Spot(x, y, z, weight, true));
    }

    // --- anomalies and artefacts ------------------------------------------------------------------------------------

    /** Anomalies at least this far apart, by tier: few, so each has room to be read and gone round. */
    private static final double[] SPACING = {8.0D, 7.0D, 6.0D, 5.0D};

    /**
     * Each anomaly, the headline first, onto the best spot left (weighted, with chance) that keeps its distance,
     * isn't in a prop, and suits it (ceiling kinds under overhangs, a field near the middle).
     */
    void placeAnomalies() {
        double spacing = SPACING[plan.tier() - 1];
        for (Kind k : plan.anomalies()) {
            Spot best = null;
            double bestScore = -1.0D;
            for (Spot s : spots) {
                boolean fits = k.spot == AnomalyMix.Spot.CEILING ? s.ceiling() : !s.ceiling();
                if (!fits || propSolid(s.x(), s.y(), s.z())) {
                    continue;
                }
                boolean clear = true;
                for (Placed p : anomalies) {
                    clear &= p.pos().distSqr(new BlockPos(s.x(), s.y(), s.z())) >= spacing * spacing;
                }
                if (!clear) {
                    continue;
                }
                double score = s.weight() * (0.5D + random.nextDouble());
                if (k.spot == AnomalyMix.Spot.FIELD) {
                    score = 1.0D / (1.0D + Math.hypot(s.x() - cx, s.z() - cz));
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
            if (best != null) {
                anomalies.add(new Placed(k, new BlockPos(best.x(), best.y(), best.z())));
            }
        }
    }

    /**
     * Artefacts lie a few blocks from the worst anomalies, one each, hardest first (STALKER's: you go in for them),
     * on the ground and never inside a prop.
     */
    void placeLoot() {
        if (anomalies.isEmpty()) {
            return;
        }
        List<Placed> order = new ArrayList<>(anomalies);
        order.sort(Comparator.comparingInt((Placed p) -> -p.kind().rank));
        int n = 0;
        for (Loot l : plan.artefacts()) {
            Placed anchor = order.get(n++ % order.size());
            BlockPos at = null;
            for (int tries = 0; tries < 12 && at == null; tries++) {
                double a = random.nextDouble() * Mth.TWO_PI;
                double out = 2.0D + random.nextDouble() * 2.0D;
                int x = anchor.pos().getX() + (int) Math.round(Math.cos(a) * out);
                int z = anchor.pos().getZ() + (int) Math.round(Math.sin(a) * out);
                int y = height(x, z) + 1;
                boolean carvedBelow = hasProp(x, y - 1, z) && !propSolid(x, y - 1, z);
                if (!hasProp(x, y, z) && !carvedBelow && u(x, z) < 1.0D && !terrain.wet(x, z)) {
                    at = new BlockPos(x, y, z);
                }
            }
            loot.add(new LootSpot(l, at != null ? at : anchor.pos(), anchor.pos()));
        }
    }

    /** A surface pick: weighted states, chosen by smooth noise so like stays with like. */
    record Palette(BlockState[] states, double scale) {
        static Palette of(net.minecraft.world.level.block.Block... blocks) {
            BlockState[] s = new BlockState[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                s[i] = blocks[i].defaultBlockState();
            }
            return new Palette(s, 7.0D);
        }

        BlockState at(SiteLayout l, int x, int z) {
            double v = (l.noise(x + 311, z - 173, scale) + 1.0D) * 0.5D;
            // A little per-block grain on top, so patch edges are ragged, not contour lines.
            v += (hash(x, z, 7) - 0.5D) * 0.15D;
            return states[Mth.clamp((int) (v * states.length), 0, states.length - 1)];
        }
    }
}
