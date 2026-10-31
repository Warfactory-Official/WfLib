package com.wf.wflib.explosion.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Systems that track block writes; section replacement in a blast bypasses the chunk write path they usually hook. */
public final class BlockChangeWatchers {

    public interface Watcher {
        /** Cheap and unlocked: asked before every write. */
        boolean isWatching();

        void onBlockChanged(Level level, BlockPos pos);
    }

    public static final OrderedRegistry<Watcher> WATCHERS = OrderedRegistry.create("block change watchers");

    private BlockChangeWatchers() {
    }

    public static boolean isWatching() {
        for (Watcher watcher : WATCHERS.values()) {
            if (watcher.isWatching()) {
                return true;
            }
        }
        return false;
    }

    public static void onBlockChanged(Level level, BlockPos pos) {
        for (Watcher watcher : WATCHERS.values()) {
            watcher.onBlockChanged(level, pos);
        }
    }
}
