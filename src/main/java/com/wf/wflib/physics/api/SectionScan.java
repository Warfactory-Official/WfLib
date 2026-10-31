package com.wf.wflib.physics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/**
 * Vanilla Entity-style per-cell box scan via palettes/heightmaps. Tick thread / live level only.
 * Cost = box volume; dominant server-thread term for large hulls (PhysicsBench).
 */
public final class SectionScan {

    public interface CellVisitor {

        /** @return false to stop */
        boolean visit(BlockPos.MutableBlockPos pos, BlockState state);
    }

    private SectionScan() {
    }

    /** Palette test: false => no cell of any section overlapping [min, max] matches. Unloaded chunk => true. */
    public static boolean maybeAny(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                   Predicate<BlockState> predicate) {
        int minSY = Math.max(SectionPos.blockToSectionCoord(minY), level.getMinSection());
        int maxSY = Math.min(SectionPos.blockToSectionCoord(maxY), level.getMaxSection() - 1);
        for (int cx = SectionPos.blockToSectionCoord(minX); cx <= SectionPos.blockToSectionCoord(maxX); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(minZ); cz <= SectionPos.blockToSectionCoord(maxZ); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return true;
                }
                for (int sy = minSY; sy <= maxSY; sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (!section.hasOnlyAir() && section.maybeHas(predicate)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean maybeAny(Level level, AABB box, Predicate<BlockState> predicate) {
        return maybeAny(level, (int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ), predicate);
    }

    /**
     * Non-air cells in [min, max] via section data, stopping at WORLD_SURFACE height. A hull on the ground still
     * shares that section, so the height cutoff misses nothing. Unloaded chunk skipped; caller must have already
     * checked load, matching vanilla checkInsideBlocks.
     */
    public static void forEachNonAir(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                     CellVisitor visitor) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int floorY = Math.max(minY, level.getMinBuildHeight());
        for (int cx = SectionPos.blockToSectionCoord(minX); cx <= SectionPos.blockToSectionCoord(maxX); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(minZ); cz <= SectionPos.blockToSectionCoord(maxZ); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null || onlyAir(chunk, floorY, maxY)) {
                    continue;
                }
                int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        int top = Math.min(maxY, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
                        for (int y = floorY; y <= top; y++) {
                            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                            if (section.hasOnlyAir()) {
                                y |= 15;
                                continue;
                            }
                            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (!state.isAir() && !visitor.visit(pos.set(x, y, z), state)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean onlyAir(LevelChunk chunk, int minY, int maxY) {
        int from = Math.max(chunk.getSectionIndex(minY), 0), to = Math.min(chunk.getSectionIndex(maxY), chunk.getSectionsCount() - 1);
        for (int index = from; index <= to; index++) {
            if (!chunk.getSection(index).hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }
}
