package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.api.AnomalyCaves.Slot;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.anomaly.api.CavePlan;

/**
 * Finds where a slot's field goes. The base noise column (cheese caves and caverns; carvers are not in it) is probed
 * on a grid round the slot's chunk for the large air volume: an air run of {@link #MIN_RUN}..{@link #MAX_RUN} blocks
 * with at least {@link #MIN_OPEN} of the 25 grid points round it open at floor level, all {@link #DEPTH} or more
 * under the surface. Best score wins (nearest the middle on a tie). None => the field is cut into the rock a
 * hashed 38-58 blocks under the surface.
 */
final class CaveProbe {
    static final int STEP = 12;
    static final int HALF = 4;
    static final int DEPTH = 24;
    static final int MIN_RUN = 8;
    static final int MAX_RUN = 28;
    static final int MIN_OPEN = 12;
    /** Air above the floor the dome may use, kept under the cave's own ceiling. */
    private static final int HEADROOM = 2;

    /** Chamber middle, its floor block's y, dome height. */
    record Site(int x, int z, int floor, int height, boolean natural) {
    }

    private CaveProbe() {
    }

    private record Run(int bottom, int top) {
        int len() {
            return top - bottom + 1;
        }
    }

    static Site find(ChunkGenerator gen, LevelHeightAccessor heights, RandomState random, Slot slot, CavePlan plan) {
        int sx = slot.chunkX() * 16 + 8;
        int sz = slot.chunkZ() * 16 + 8;
        int n = 2 * HALF + 1;
        int min = heights.getMinBuildHeight() + 12;
        @SuppressWarnings("unchecked")
        List<Run>[][] runs = new List[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = sx + (i - HALF) * STEP;
                int z = sz + (j - HALF) * STEP;
                int top = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random) - DEPTH;
                runs[i][j] = runs(gen.getBaseColumn(x, z, heights, random), Math.min(top, heights.getMaxBuildHeight() - 2), min);
            }
        }
        int bestScore = 0;
        long bestKey = Long.MAX_VALUE;
        int bi = 0;
        int bj = 0;
        Run best = null;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (Run r : runs[i][j]) {
                    if (r.len() < MIN_RUN || r.len() > MAX_RUN) {
                        continue;
                    }
                    int score = open(runs, i, j, r.bottom() + 2);
                    long dist = (long) (i - HALF) * (i - HALF) + (long) (j - HALF) * (j - HALF);
                    if (score >= MIN_OPEN && (score > bestScore || score == bestScore && dist < bestKey)) {
                        bestScore = score;
                        bestKey = dist;
                        bi = i;
                        bj = j;
                        best = r;
                    }
                }
            }
        }
        if (best != null) {
            return new Site(sx + (bi - HALF) * STEP, sz + (bj - HALF) * STEP, best.bottom() - 1,
                    Math.max(CavePlan.MIN_HEIGHT, Math.min(plan.height(), best.len() - HEADROOM)), true);
        }
        int surface = gen.getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
        int depth = 38 + (int) Math.floorMod(slot.seed() >>> 17, 21L);
        int floor = Math.max(min, surface - depth);
        return new Site(sx, sz, floor, plan.height(), false);
    }

    private static int open(List<Run>[][] runs, int ci, int cj, int y) {
        int n = 0;
        for (int i = Math.max(0, ci - 2); i <= Math.min(runs.length - 1, ci + 2); i++) {
            for (int j = Math.max(0, cj - 2); j <= Math.min(runs.length - 1, cj + 2); j++) {
                for (Run r : runs[i][j]) {
                    if (r.bottom() <= y && r.top() >= y + 2) {
                        n++;
                        break;
                    }
                }
            }
        }
        return n;
    }

    private static List<Run> runs(NoiseColumn column, int top, int min) {
        List<Run> out = new ArrayList<>();
        int start = Integer.MIN_VALUE;
        for (int y = top; y >= min; y--) {
            boolean air = column.getBlock(y).isAir();
            if (air && start == Integer.MIN_VALUE) {
                start = y;
            } else if (!air && start != Integer.MIN_VALUE) {
                out.add(new Run(y + 1, start));
                start = Integer.MIN_VALUE;
            }
        }
        if (start != Integer.MIN_VALUE) {
            out.add(new Run(min, start));
        }
        return out;
    }
}
