package com.wf.wflib.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.UUID;

/**
 * Chunk-ticket mutations (distance manager, forced-chunk maps) are server-thread only. Server thread: applied
 * inline (queued ops first). Workers: queued, drained at the next {@code LevelTickEvent.Pre}, before that
 * tick's chunk-source update => same activation tick as an inline add during the previous entity tick.
 * Per-op cost: server thread 0 allocations; worker 1 lambda + 1 queue node.
 */
@EventBusSubscriber(modid = WFLib.MODID)
public final class ChunkTicketOps {

    private static final MainThreadOps OPS = new MainThreadOps();

    private ChunkTicketOps() {
    }

    public static void force(TicketController controller, ServerLevel level, UUID owner,
                             int chunkX, int chunkZ, boolean add, boolean ticking) {
        if (level.getServer().isSameThread()) {
            if (OPS.pending()) {
                OPS.drain();
            }
            controller.forceChunk(level, owner, chunkX, chunkZ, add, ticking);
        } else {
            OPS.enqueue(level, () -> controller.forceChunk(level, owner, chunkX, chunkZ, add, ticking));
        }
    }

    public static void force(TicketController controller, ServerLevel level, BlockPos owner,
                             int chunkX, int chunkZ, boolean add, boolean ticking) {
        if (level.getServer().isSameThread()) {
            if (OPS.pending()) {
                OPS.drain();
            }
            controller.forceChunk(level, owner, chunkX, chunkZ, add, ticking);
        } else {
            OPS.enqueue(level, () -> controller.forceChunk(level, owner, chunkX, chunkZ, add, ticking));
        }
    }

    /** Arbitrary server-thread-only op scoped to {@code level} (dropped on its unload). */
    public static void run(ServerLevel level, Runnable op) {
        if (level.getServer().isSameThread()) {
            OPS.runOnMain(op);
        } else {
            OPS.enqueue(level, op);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (OPS.pending()) {
            OPS.drain();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        OPS.dropScope(event.getLevel());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        OPS.clear();
    }
}
