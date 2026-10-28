package com.wf.wflib.anomaly.cluster;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground under one cluster while it is shaped: where the terrain was, the outline (a circle with a wobble, so
 * no two read as stamped), and the spots the shaping leaves for anomalies.
 */
final class ClusterSite {
    /** Blocks cleared above a new surface: trees, the hill that was there. */
    private static final int CLEAR_UP = 32;

    final WorldGenLevel level;
    final RandomSource random;
    final BlockPos centre;
    final int radius;
    final int tier;
    /** Ground height under the middle, the median over the outline: what designs level toward. */
    final int baseY;
    final SimplexNoise noise;
    /** Air cells resting on ground: floor anomalies go here. */
    final List<BlockPos> floor = new ArrayList<>();
    /** Air cells right under an overhang (a branch, an arch): Burnt Fuzz and Grabbers hang from here. */
    final List<BlockPos> ceiling = new ArrayList<>();
    private final float[] wobbleAmp = new float[3];
    private final float[] wobblePhase = new float[3];

    private ClusterSite(WorldGenLevel level, RandomSource random, BlockPos centre, int radius, int tier, int baseY) {
        this.level = level;
        this.random = random;
        this.centre = centre;
        this.radius = radius;
        this.tier = tier;
        this.baseY = baseY;
        this.noise = new SimplexNoise(RandomSource.create(random.nextLong()));
        for (int k = 0; k < wobbleAmp.length; k++) {
            wobbleAmp[k] = (0.03F + random.nextFloat() * 0.06F) / (k + 1);
            wobblePhase[k] = random.nextFloat() * Mth.TWO_PI;
        }
    }

    /**
     * The site at {@code centre}, or null where a cluster would look wrong: water inside the outline (lakes, rivers,
     * the sea), or ground too steep (a cliff, a mountainside).
     */
    @Nullable
    static ClusterSite open(WorldGenLevel level, RandomSource random, BlockPos centre, int radius, int tier) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int y = ground(level, centre.getX() + dx, centre.getZ() + dz);
                if (y == Integer.MIN_VALUE) {
                    return null;
                }
                heights.add(y);
            }
        }
        heights.sort(null);
        int lo = heights.get(heights.size() / 10);
        int hi = heights.get(heights.size() * 9 / 10);
        if (hi - lo > radius * 0.6F + 3) {
            return null;
        }
        return new ClusterSite(level, random, centre, radius, tier, heights.get(heights.size() / 2));
    }

    /** Top of the solid ground at a column, under trees and plants; {@code MIN_VALUE} if water or lava is on top. */
    static int ground(WorldGenLevel level, int x, int z) {
        // The _WG heightmap only exists while a chunk generates; a live chunk (a command's) keeps the plain one.
        Heightmap.Types top = level instanceof net.minecraft.server.level.WorldGenRegion
                ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.WORLD_SURFACE;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, level.getHeight(top, x, z), z);
        int bottom = level.getMinBuildHeight();
        while (at.getY() > bottom) {
            at.move(0, -1, 0);
            BlockState s = level.getBlockState(at);
            if (!s.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (!vegetation(s)) {
                return at.getY();
            }
        }
        return bottom;
    }

    /** Not ground: air, trees, plants, snow layers. */
    static boolean vegetation(BlockState s) {
        return s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() || !s.blocksMotion()
                || s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(BlockTags.SNOW) && !s.is(Blocks.SNOW_BLOCK);
    }

    int ground(int x, int z) {
        int y = ground(level, x, z);
        return y == Integer.MIN_VALUE ? baseY : y;
    }

    /**
     * How far out a column is, in outlines: 1 on the outer edge of everything the design touches. The outline
     * wobbles (2-4 slow lobes) inward only, so nothing reaches past {@link #radius}.
     */
    double u(int x, int z) {
        double dx = x - centre.getX();
        double dz = z - centre.getZ();
        double angle = Math.atan2(dz, dx);
        double edge = 1.0D;
        for (int k = 0; k < wobbleAmp.length; k++) {
            edge += wobbleAmp[k] * (Math.sin((k + 2) * angle + wobblePhase[k]) - 1.0D);
        }
        return Math.sqrt(dx * dx + dz * dz) / (radius * edge);
    }

    /** Smooth noise -1..1 over the ground, about {@code scale} blocks a feature. */
    double noise(int x, int z, double scale) {
        return noise.getValue(x / scale, z / scale);
    }

    /** No shape updates and no drops: in a live world (the command) plants beside an edit would pop off as items. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    void set(int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, FLAGS);
    }

    BlockState get(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z));
    }

    /**
     * Make a column's surface {@code top} at height {@code y}, {@code under} for {@code depth} below it (filling up
     * from the old ground if it rises), and clear everything above: the cut-away hill, trees, plants.
     */
    void column(int x, int z, int y, BlockState top, BlockState under, int depth) {
        int old = ground(x, z);
        for (int k = y + 1; k <= Math.max(old, y) + CLEAR_UP; k++) {
            if (!get(x, k, z).isAir()) {
                set(x, k, z, Blocks.AIR.defaultBlockState());
            }
        }
        set(x, y, z, top);
        for (int k = y - 1; k >= Math.min(y - depth, old + 1); k--) {
            set(x, k, z, under);
        }
    }

    /** Trees and plants off a column (left a bare surface), the ground as it was. */
    void clearVegetation(int x, int z) {
        int old = ground(x, z);
        for (int k = old + 1; k <= old + CLEAR_UP; k++) {
            BlockState s = get(x, k, z);
            if (!s.isAir() && vegetation(s)) {
                set(x, k, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    void addFloor(int x, int y, int z) {
        floor.add(new BlockPos(x, y, z));
    }

    void addCeiling(int x, int y, int z) {
        ceiling.add(new BlockPos(x, y, z));
    }

    /** One of {@code states}, picked by smooth noise so like stays with like (patches, not static). */
    BlockState patch(int x, int z, double scale, BlockState... states) {
        double v = (noise(x, z, scale) + 1.0D) * 0.5D;
        return states[Mth.clamp((int) (v * states.length), 0, states.length - 1)];
    }

    boolean chance(float p) {
        return random.nextFloat() < p;
    }
}
