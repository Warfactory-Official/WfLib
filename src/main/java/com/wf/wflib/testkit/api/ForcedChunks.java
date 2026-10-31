package com.wf.wflib.testkit.api;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reference-counted {@link ServerLevel#setChunkForced}. The vanilla flag is one bit per chunk: concurrent
 * gametests whose lanes share a chunk unforce each other's, and the survivor's entities unload mid-test.
 * Server thread only.
 *
 * <p>Raw callers share the bit: GameTest arenas ({@code StructureUtils.forceLoadChunks}) => never unforce a chunk
 * found forced; batch end unforces every chunk => a hold re-forces a chunk found unforced.
 *
 * <p>Forced != entity-ticking: entity-ticking lags the hold by >= 8 ticks, and a non-chunk-loading entity crossing
 * into a not-yet-ticking chunk is discarded. Spawn only after {@link #ticking}.
 */
public final class ForcedChunks {

    private static final Map<ResourceKey<Level>, Map<Long, Integer>> HOLDS = new HashMap<>();
    /** Chunks this class forced (was unforced at the hold): only these are unforced at the last release. */
    private static final Map<ResourceKey<Level>, Set<Long>> OURS = new HashMap<>();

    private ForcedChunks() {
    }

    /** {@code forced} = take a hold, else drop one; the chunk unforces with its last hold. */
    public static void set(ServerLevel level, int cx, int cz, boolean forced) {
        Map<Long, Integer> holds = HOLDS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        Set<Long> ours = OURS.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        long key = ChunkPos.asLong(cx, cz);
        if (forced) {
            holds.merge(key, 1, Integer::sum);
            if (!level.getForcedChunks().contains(key)) {
                level.setChunkForced(cx, cz, true);
                ours.add(key);
            }
            return;
        }
        int count = holds.getOrDefault(key, 0) - 1;
        if (count > 0) {
            holds.put(key, count);
            return;
        }
        holds.remove(key);
        if (ours.remove(key)) {
            level.setChunkForced(cx, cz, false);
        }
    }

    public static void set(ServerLevel level, Collection<ChunkPos> chunks, boolean forced) {
        for (ChunkPos c : chunks) {
            set(level, c.x, c.z, forced);
        }
    }

    /** Chunks the segment {@code from -> to} crosses, in order (1-block steps). */
    public static List<ChunkPos> along(Vec3 from, Vec3 to) {
        Set<ChunkPos> out = new LinkedHashSet<>();
        int steps = (int) Math.ceil(from.distanceTo(to));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = steps == 0 ? from : from.lerp(to, i / (double) steps);
            out.add(new ChunkPos(BlockPos.containing(p)));
        }
        return new ArrayList<>(out);
    }

    /**
     * {@code GameTestSequence.thenWaitUntil} condition: every chunk entity-ticking. Failure names the first that is
     * not, with its forced bit, full status and entity-section state (the timeout reports the last).
     */
    public static Runnable ticking(ServerLevel level, Collection<ChunkPos> chunks) {
        return () -> {
            for (ChunkPos c : chunks) {
                if (!level.isPositionEntityTicking(c.getWorldPosition())) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(c.x, c.z);
                    throw new GameTestAssertException("chunk " + c + " not entity-ticking: forced "
                            + level.getForcedChunks().contains(c.toLong()) + ", status "
                            + (chunk == null ? "not loaded" : chunk.getFullStatus()) + ", entities loaded "
                            + level.areEntitiesLoaded(c.toLong()));
                }
            }
        };
    }
}
