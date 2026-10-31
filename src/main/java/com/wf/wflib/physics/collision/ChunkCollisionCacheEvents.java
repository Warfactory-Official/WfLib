package com.wf.wflib.physics.collision;

import com.wf.wflib.physics.api.event.PhysicsLevelUnloadEvent;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import com.wf.wflib.physics.api.ChunkCollisionCache;

/** Per-block invalidation: LevelChunk.setBlockState mixin, not here. */
public final class ChunkCollisionCacheEvents {

    private ChunkCollisionCacheEvents() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Reloaded chunk reuses section keys => stale snapshot without this.
        if (event.getLevel() instanceof Level level) {
            ChunkCollisionCache.invalidateChunk(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            ChunkCollisionCache.invalidateChunk(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            ChunkCollisionCache.forget(level);
            NeoForge.EVENT_BUS.post(new PhysicsLevelUnloadEvent(level));
        }
    }

}
