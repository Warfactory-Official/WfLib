package com.wf.wflib.soldier.api;

import com.wf.wflib.soldier.TerritoryBook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Finds open, level ground for an operation site at a distance band from a point: flat enough to stand a force on, dry,
 * not inside another territory, away from every real player. Samples candidates at random on a ring and keeps the flattest
 * that qualifies. Server thread only.
 */
public final class SiteFinder {
    private SiteFinder() {
    }

    /**
     * @param origin the ring's centre (the contract giver, the player)
     * @param minDistance inner edge of the ring, blocks
     * @param maxDistance outer edge, blocks
     * @param radius the site's own radius: the ground must be level across it
     * @param minPlayerDistance no real player closer than this to the site's centre
     * @param generateChunks load or generate the chunks the search lands on (slow); false: only candidates in loaded chunks
     * @param tries candidates to look at
     */
    public record Request(Vec3 origin, int minDistance, int maxDistance, int radius, int minPlayerDistance,
                          boolean generateChunks, int tries) {
        public Request {
            if (minDistance < 0 || maxDistance < minDistance) throw new IllegalArgumentException("Bad distance band");
            if (radius < 2 || tries < 1) throw new IllegalArgumentException("Bad radius or tries");
        }
    }

    /** Highest ground height difference across a site that still counts as level. */
    public static final int MAX_ROUGHNESS = 5;

    public static Optional<Vec3> find(ServerLevel level, Request r, RandomSource rng) {
        Vec3 best = null;
        int bestRough = Integer.MAX_VALUE;
        for (int i = 0; i < r.tries(); i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = r.minDistance() + rng.nextDouble() * (r.maxDistance() - r.minDistance());
            int x = (int) Math.floor(r.origin().x + Math.cos(angle) * dist);
            int z = (int) Math.floor(r.origin().z + Math.sin(angle) * dist);
            Vec3 at = candidate(level, x, z, r);
            if (at == null) continue;
            int rough = roughness(level, x, z, r.radius(), r.generateChunks());
            if (rough < 0 || rough > MAX_ROUGHNESS || rough >= bestRough) continue;
            if (!clear(level, at, r)) continue;
            best = at;
            bestRough = rough;
            if (rough <= 1) break;                         // as good as it gets
        }
        return Optional.ofNullable(best);
    }

    /** The standing position at {@code x, z}, or null when the chunk is not available or the ground is no good. */
    private static Vec3 candidate(ServerLevel level, int x, int z, Request r) {
        if (!available(level, x, z, r.generateChunks())) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos ground = new BlockPos(x, y - 1, z);
        BlockState state = level.getBlockState(ground);
        if (!state.getFluidState().isEmpty() || !state.isCollisionShapeFullBlock(level, ground)) return null;
        if (!level.getWorldBorder().isWithinBounds(ground)) return null;
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    private static boolean available(ServerLevel level, int x, int z, boolean generate) {
        ChunkPos c = new ChunkPos(new BlockPos(x, 0, z));
        if (!generate) return level.hasChunk(c.x, c.z);
        return level.getChunkSource().getChunk(c.x, c.z, ChunkStatus.FULL, true) != null;
    }

    /** Ground height range over the centre and a ring at 60% of the radius; -1 when any sample is unavailable. */
    private static int roughness(ServerLevel level, int cx, int cz, int radius, boolean generate) {
        int[] heights = new int[9];
        heights[0] = height(level, cx, cz, generate);
        int reach = Math.max(1, radius * 3 / 5);
        for (int k = 0; k < 8; k++) {
            int x = cx + (int) Math.round(Math.cos(k * Math.PI / 4) * reach);
            int z = cz + (int) Math.round(Math.sin(k * Math.PI / 4) * reach);
            heights[k + 1] = height(level, x, z, generate);
        }
        return roughness(heights);
    }

    private static int height(ServerLevel level, int x, int z, boolean generate) {
        return available(level, x, z, generate) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : Integer.MIN_VALUE;
    }

    /** Max minus min of the samples; -1 when one is missing (marked Integer.MIN_VALUE). */
    public static int roughness(int[] heights) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int h : heights) {
            if (h == Integer.MIN_VALUE) return -1;
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        return hi - lo;
    }

    private static boolean clear(ServerLevel level, Vec3 at, Request r) {
        String dimension = level.dimension().location().toString();
        if (!TerritoryBook.get(level.getServer()).at(dimension, at).isEmpty()) return false;
        double min = r.minPlayerDistance();
        for (ServerPlayer p : level.players()) {
            if (p instanceof SoldierPlayer) continue;
            if (p.position().distanceToSqr(at) < min * min) return false;
        }
        return true;
    }
}
