package com.wf.wflib.stream.api;

import com.wf.wflib.mixin.stream.AccessorChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Reads of the server's chunk tracking that vanilla keeps private. */
public final class ChunkMaps {
    private ChunkMaps() {
    }

    /** @return the tracker of entity {@code id} in {@code level}, or null; a {@link StreamTracked} when ours. */
    @Nullable
    public static Object trackerOf(ServerLevel level, int id) {
        return ((AccessorChunkMap) level.getChunkSource().chunkMap).wfEntityMap().get(id);
    }

    /** @return the chunk radius {@code player} is sent. */
    public static int viewDistance(ServerPlayer player) {
        return ((AccessorChunkMap) player.serverLevel().getChunkSource().chunkMap).wfPlayerViewDistance(player);
    }
}
