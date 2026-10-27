package com.wf.wflib.radiation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

// Per-dimension region files; IOWorker reads, server thread writes.
interface ChunkRadiationStorage {
    byte @Nullable [] read(ServerLevel level, ChunkPos pos);

    void write(ServerLevel level, ChunkPos pos, byte @Nullable [] payload);

    void discard(ServerLevel level, ChunkPos pos);

    void flush();

    void deleteDimension(ServerLevel level);

    void close();
}
