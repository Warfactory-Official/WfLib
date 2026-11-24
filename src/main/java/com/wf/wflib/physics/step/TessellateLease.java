package com.wf.wflib.physics.step;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;
import java.util.function.ObjLongConsumer;

/**
 * Synchronous server-thread lease on the region owning a chunk: stock Tessellate public API only queues leased work
 * ({@code executeOnMainThread}), no way to block in place. Reflection on {@code RegionTracker.index(level).leaseChunk}
 * (internal; pinned by the Tessellate version gate). Class absent => {@link #INSTANCE} null.
 */
public final class TessellateLease {

    private static final String TRACKER = "org.texboobcat.tessellate.region.RegionTracker";
    private static final String INDEX = "org.texboobcat.tessellate.region.LevelRegionIndex";

    @Nullable
    public static final TessellateLease INSTANCE = bind(TessellateLease.class.getClassLoader());

    private final Function<ServerLevel, Object> indexOf;
    private final ObjLongConsumer<Object> leaseChunk;

    private TessellateLease(Function<ServerLevel, Object> indexOf, ObjLongConsumer<Object> leaseChunk) {
        this.indexOf = indexOf;
        this.leaseChunk = leaseChunk;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static TessellateLease bind(ClassLoader loader) {
        Class<?> tracker;
        Class<?> index;
        try {
            tracker = Class.forName(TRACKER, true, loader);
            index = Class.forName(INDEX, true, loader);
        } catch (ClassNotFoundException absent) {
            return null;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType indexOf = MethodType.methodType(index, ServerLevel.class);
            MethodHandle indexHandle = lookup.findStatic(tracker, "index", indexOf);
            MethodHandle leaseHandle = lookup.findVirtual(index, "leaseChunk", MethodType.methodType(void.class, long.class));
            Function<ServerLevel, Object> indexFn = (Function<ServerLevel, Object>) LambdaMetafactory.metafactory(
                    MethodHandles.lookup(), "apply", MethodType.methodType(Function.class),
                    MethodType.methodType(Object.class, Object.class), indexHandle, indexOf).getTarget().invoke();
            ObjLongConsumer<Object> leaseFn = (ObjLongConsumer<Object>) LambdaMetafactory.metafactory(
                    MethodHandles.lookup(), "accept", MethodType.methodType(ObjLongConsumer.class),
                    MethodType.methodType(void.class, Object.class, long.class), leaseHandle,
                    MethodType.methodType(void.class, index, long.class)).getTarget().invoke();
            return new TessellateLease(indexFn, leaseFn);
        } catch (ReflectiveOperationException | LambdaConversionException mismatch) {
            throw new IllegalStateException("Tessellate lease API mismatch", mismatch);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** Server thread: blocks until the region owning {@code chunkPos} is not ticking; level without regions => no-op. */
    public void lease(ServerLevel level, long chunkPos) {
        Object index = indexOf.apply(level);
        if (index != null) {
            leaseChunk.accept(index, chunkPos);
        }
    }
}
