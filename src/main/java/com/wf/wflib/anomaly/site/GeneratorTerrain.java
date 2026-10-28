package com.wf.wflib.anomaly.site;

import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The ground as the noise lays it, before anything is built on it: the same wherever a site's chunks generate, so
 * each works out the same layout. Sampled every {@link #STEP} blocks and interpolated (the noise itself is
 * interpolated over 4-block cells), cached for the site's life: a site costs a few hundred noise columns, not
 * thousands.
 */
final class GeneratorTerrain implements SiteLayout.Terrain {
    static final int STEP = 4;

    private final ChunkGenerator generator;
    private final LevelHeightAccessor heights;
    private final RandomState random;
    /** Grid point -> {solid floor, top including water}. */
    private final ConcurrentHashMap<Long, int[]> samples = new ConcurrentHashMap<>();

    GeneratorTerrain(ChunkGenerator generator, LevelHeightAccessor heights, RandomState random) {
        this.generator = generator;
        this.heights = heights;
        this.random = random;
    }

    private int[] sample(int gx, int gz) {
        return samples.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(gx, gz), k -> new int[] {
                generator.getFirstOccupiedHeight(gx * STEP, gz * STEP, Heightmap.Types.OCEAN_FLOOR_WG, heights, random),
                generator.getFirstOccupiedHeight(gx * STEP, gz * STEP, Heightmap.Types.WORLD_SURFACE_WG, heights, random)});
    }

    @Override
    public int ground(int x, int z) {
        int gx = Math.floorDiv(x, STEP);
        int gz = Math.floorDiv(z, STEP);
        double fx = (x - gx * STEP) / (double) STEP;
        double fz = (z - gz * STEP) / (double) STEP;
        double a = Mth.lerp(fx, sample(gx, gz)[0], sample(gx + 1, gz)[0]);
        double b = Mth.lerp(fx, sample(gx, gz + 1)[0], sample(gx + 1, gz + 1)[0]);
        return (int) Math.round(Mth.lerp(fz, a, b));
    }

    @Override
    public boolean wet(int x, int z) {
        int[] s = sample(Math.floorDiv(x + STEP / 2, STEP), Math.floorDiv(z + STEP / 2, STEP));
        return s[1] > s[0];
    }
}
