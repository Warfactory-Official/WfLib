package com.wf.wflib.soldier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A copy of the world's collision, one byte per block, that a worker can read while the tick thread goes on changing the
 * world: the solid-ness of every block in a chunk section, built on the tick thread when first asked for and thrown away
 * when any block in the section changes ({@code mixin.soldier.MixinLevelChunkSoldier}).
 *
 * <p>A cell's byte: bits 0-4 the height of its collision in sixteenths (0 = none, 16 = a full block or taller), bit 5 set
 * when the collision is a thin pane, bar or post that blocks walking but not sight.
 */
public final class SoldierSections {
    public static final int HEIGHT_MASK = 31, THIN = 32;
    /** Sections built per call before giving up until the next tick. */
    private static final int BUILD_BUDGET = 12;

    private static final Map<ResourceKey<Level>, ConcurrentHashMap<Long, byte[]>> CACHE = new ConcurrentHashMap<>();
    /** BlockState -> cell byte; tick thread only (it asks the state for its collision shape). */
    private static final Map<BlockState, Byte> CLASSES = new IdentityHashMap<>();

    private SoldierSections() {
    }

    /** A block changed: its section's copy is stale. Any thread. */
    public static void dirty(ResourceKey<Level> dimension, BlockPos pos) {
        ConcurrentHashMap<Long, byte[]> sections = CACHE.get(dimension);
        if (sections != null) sections.remove(SectionPos.asLong(pos));
    }

    /** The copy of a section, or null when it has not been built (or has changed since). Any thread. */
    static byte[] section(ResourceKey<Level> dimension, long sectionKey) {
        ConcurrentHashMap<Long, byte[]> sections = CACHE.get(dimension);
        return sections == null ? null : sections.get(sectionKey);
    }

    /** The cell byte at a block, or -1 when its section has no copy. Any thread. */
    static int cell(ResourceKey<Level> dimension, int x, int y, int z) {
        byte[] section = section(dimension, SectionPos.asLong(SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z)));
        return section == null ? -1 : section[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)];
    }

    /**
     * Makes sure every section touching the box has a copy, building the missing ones. Tick thread only.
     *
     * @return false when a chunk is not loaded or more sections are missing than one call builds (ask again next tick)
     */
    public static boolean ensure(ServerLevel level, BlockPos min, BlockPos max) {
        ConcurrentHashMap<Long, byte[]> sections = CACHE.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
        int built = 0;
        boolean complete = true;
        for (int sx = SectionPos.blockToSectionCoord(min.getX()); sx <= SectionPos.blockToSectionCoord(max.getX()); sx++) {
            for (int sz = SectionPos.blockToSectionCoord(min.getZ()); sz <= SectionPos.blockToSectionCoord(max.getZ()); sz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
                for (int sy = SectionPos.blockToSectionCoord(Math.max(min.getY(), level.getMinBuildHeight()));
                     sy <= SectionPos.blockToSectionCoord(Math.min(max.getY(), level.getMaxBuildHeight() - 1)); sy++) {
                    long key = SectionPos.asLong(sx, sy, sz);
                    if (sections.containsKey(key)) continue;
                    if (chunk == null || built >= BUILD_BUDGET) {
                        complete = false;
                        continue;
                    }
                    sections.put(key, build(chunk.getSection(chunk.getSectionIndexFromSectionY(sy))));
                    built++;
                }
            }
        }
        return complete;
    }

    private static byte[] build(LevelChunkSection section) {
        byte[] out = new byte[4096];
        if (section.hasOnlyAir()) return out;
        int i = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) out[i++] = classOf(section.getBlockState(x, y, z));
            }
        }
        return out;
    }

    static byte classOf(BlockState state) {
        Byte known = CLASSES.get(state);
        if (known != null) return known;
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        byte value = 0;
        if (!shape.isEmpty()) {
            int height = (int) Math.min(16, Math.ceil(shape.max(net.minecraft.core.Direction.Axis.Y) * 16 - 1e-9));
            value = (byte) Math.max(1, height);
            double width = Math.min(shape.max(net.minecraft.core.Direction.Axis.X) - shape.min(net.minecraft.core.Direction.Axis.X),
                    shape.max(net.minecraft.core.Direction.Axis.Z) - shape.min(net.minecraft.core.Direction.Axis.Z));
            if (width < 0.4) value |= THIN;
        }
        CLASSES.put(state, value);
        return value;
    }

    /** Drop everything for a level that is going away. */
    public static void forget(ResourceKey<Level> dimension) {
        CACHE.remove(dimension);
    }

    public static void clear() {
        CACHE.clear();
    }

    /** Sections held, for the report. */
    public static int held() {
        return CACHE.values().stream().mapToInt(Map::size).sum();
    }
}
