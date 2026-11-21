package com.wf.wflib.contract.site;

/**
 * Whether a piece of ground can carry a base, and how good it is. A footprint is sampled on a grid and refused for water, lava,
 * wet biomes, ground that gives way (sand, gravel, ice, caves just under the surface) or slopes steeper than the archetype can
 * grade out; what is left is scored for flatness, commanding height and nearby cover. Pure rules over {@link Terrain}.
 */
public final class SiteSurvey {
    /** Samples every this many blocks across the footprint. */
    public static final int STEP = 4;

    private SiteSurvey() {
    }

    /**
     * @param ok the ground can take the base
     * @param reason why not, empty when ok
     * @param padY the level the base pad is graded to (the median of the footprint)
     * @param range highest minus lowest ground across the footprint
     * @param score higher is better; 0 when not ok
     */
    public record Result(boolean ok, String reason, int padY, int range, double score) {
        static Result no(String why) {
            return new Result(false, why, 0, 0, 0);
        }
    }

    /**
     * @param radius the footprint's radius in blocks
     * @param maxRange the steepest the ground may be across it: what the builder can grade flat
     */
    public static Result assess(Terrain t, int cx, int cz, int radius, int maxRange) {
        int n = 0, water = 0, loose = 0, hollow = 0, wet = 0, count = 0;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, centreY = 0;
        int[] heights = new int[(2 * radius / STEP + 1) * (2 * radius / STEP + 1)];
        for (int dx = -radius; dx <= radius; dx += STEP) {
            for (int dz = -radius; dz <= radius; dz += STEP) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = cx + dx, z = cz + dz;
                if (!t.available(x, z)) return Result.no("ground not available");
                var s = t.surface(x, z);
                if (s == Terrain.Surface.LAVA) return Result.no("lava");
                int y = t.ground(x, z);
                n++;
                if (s == Terrain.Surface.WATER) water++;
                else if (s != Terrain.Surface.SOLID) loose++;
                if (t.hollow(x, z)) hollow++;
                if (t.wet(x, z)) wet++;
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
                if (dx == 0 && dz == 0) centreY = y;
                heights[count++] = y;
            }
        }
        if (n == 0) return Result.no("empty footprint");
        if (wet * 3 > n) return Result.no("coast or river");
        if (water * 12 > n) return Result.no("water");
        if (loose * 6 > n) return Result.no("loose ground");
        if (hollow * 8 > n) return Result.no("hollow ground");
        int range = hi - lo;
        if (range > maxRange) return Result.no("too steep");
        java.util.Arrays.sort(heights, 0, count);
        int median = heights[count / 2];

        // cover just outside the pad is worth having; a commanding centre (above its surroundings) is worth more
        int cover = 0, ring = 0, above = 0;
        int out = radius + 8;
        for (int dx = -out; dx <= out; dx += STEP) {
            for (int dz = -out; dz <= out; dz += STEP) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= radius * radius || d2 > out * out) continue;
                int x = cx + dx, z = cz + dz;
                if (!t.available(x, z)) continue;
                ring++;
                if (t.cover(x, z)) cover++;
                if (t.ground(x, z) < centreY) above++;
            }
        }
        double score = 100 - range * 6.0 - loose * 40.0 / n - hollow * 40.0 / n - water * 80.0 / n;
        if (ring > 0) score += Math.min(cover, ring / 4) * 12.0 / Math.max(1, ring / 4) + above * 10.0 / ring;
        return new Result(true, "", median, range, Math.max(1, score));
    }
}
