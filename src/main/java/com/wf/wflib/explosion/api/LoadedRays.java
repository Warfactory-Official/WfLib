package com.wf.wflib.explosion.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collider ray test reading only loaded chunks; unloaded cell = open. {@code Level.clip} => {@code getChunk}
 * => synchronous load/worldgen on the server thread. Single owner: not thread-safe.
 */
final class LoadedRays implements BlastExposure.RayTest {

    private final Level level;
    private final ServerChunkCache server;
    private long chunkKey = Long.MIN_VALUE;
    private ChunkAccess chunk;
    private Vec3 from;
    private Vec3 to;

    LoadedRays(Level level) {
        this.level = level;
        this.server = level.getChunkSource() instanceof ServerChunkCache s ? s : null;
    }

    @Override
    public boolean blocked(Vec3 from, Vec3 to) {
        this.from = from;
        this.to = to;
        return BlockGetter.traverseBlocks(from, to, this, LoadedRays::cell, r -> Boolean.FALSE);
    }

    private static Boolean cell(LoadedRays r, BlockPos pos) {
        BlockState state = r.state(pos);
        if (state == null || state.isAir()) {
            return null;
        }
        VoxelShape shape = state.getCollisionShape(r.level, pos, CollisionContext.empty());
        return !shape.isEmpty() && shape.clip(r.from, r.to, pos) != null ? Boolean.TRUE : null;
    }

    private BlockState state(BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return null;
        }
        int cx = SectionPos.blockToSectionCoord(pos.getX());
        int cz = SectionPos.blockToSectionCoord(pos.getZ());
        long key = ChunkPos.asLong(cx, cz);
        if (key != chunkKey) {
            chunkKey = key;
            chunk = server != null ? server.getChunkNow(cx, cz) : level.getChunk(cx, cz, ChunkStatus.FULL, false);
        }
        return chunk == null ? null : chunk.getBlockState(pos);
    }
}
