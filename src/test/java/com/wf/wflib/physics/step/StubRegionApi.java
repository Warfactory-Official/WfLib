package com.wf.wflib.physics.step;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Same static surface as Tessellate's TessellateApi region calls, plus RegionThreadContext (same shape: currentBinding().region().id()). */
public final class StubRegionApi {

    static volatile StubBinding current;

    private StubRegionApi() {
    }

    public static boolean ownsCurrentRegion(ServerLevel level, BlockPos pos) {
        return true;
    }

    static final List<Runnable> QUEUED = new CopyOnWriteArrayList<>();

    public static void executeOnRegion(ServerLevel level, BlockPos pos, Runnable work) {
        QUEUED.add(work);
    }

    static final List<Runnable> LEASED = new CopyOnWriteArrayList<>();

    public static void executeOnMainThread(ServerLevel level, BlockPos pos, Runnable work) {
        LEASED.add(work);
    }

    public static final class StubRegion {
        private final int id;

        StubRegion(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }
    }

    public record StubBinding(StubRegion region, String levelKey) {
    }

    /** Context stand-in. */
    public static final class Context {
        private Context() {
        }

        public static StubBinding currentBinding() {
            return current;
        }
    }

    static void bindRegion(int id) {
        current = new StubBinding(new StubRegion(id), "level");
    }
}
