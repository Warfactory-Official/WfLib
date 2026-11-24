package com.wf.wflib.physics.step;

import com.mojang.logging.LogUtils;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.RegionHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Tessellate as {@link RegionHost}, by reflection: no compile dependency, inactive when the API class is absent.
 * Region tick end is delivered by {@code MixinLevelRegionIndexTickEnd} ({@link #regionTickEnd}); stock Tessellate has no hook.
 */
public final class TessellateBridge implements RegionHost {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final String API = "org.texboobcat.tessellate.api.TessellateApi";
    static final String CONTEXT = "org.texboobcat.tessellate.guard.RegionThreadContext";

    /** Linked lambda classes, not MethodHandle fields: handle targets are not constants for the JIT, these inline. */
    private final Supplier<Object> currentBinding;
    private final Function<Object, Object> bindingRegion;
    private final ToIntFunction<Object> regionId;
    private final BiPredicate<ServerLevel, BlockPos> ownsCurrentRegion;

    private final RegionWork executeOnRegion;
    private final RegionWork executeLeased;

    @FunctionalInterface
    interface RegionWork {
        void run(ServerLevel level, BlockPos pos, Runnable work);
    }

    private TessellateBridge(Supplier<Object> currentBinding, Function<Object, Object> bindingRegion,
                             ToIntFunction<Object> regionId, BiPredicate<ServerLevel, BlockPos> ownsCurrentRegion,
                             RegionWork executeOnRegion, RegionWork executeLeased) {
        this.currentBinding = currentBinding;
        this.bindingRegion = bindingRegion;
        this.regionId = regionId;
        this.executeLeased = executeLeased;
        this.ownsCurrentRegion = ownsCurrentRegion;
        this.executeOnRegion = executeOnRegion;
    }

    /** Mod init. */
    public static void install() {
        TessellateBridge bridge = bind(API, CONTEXT, TessellateBridge.class.getClassLoader());
        if (bridge != null) {
            RigidStep.install(bridge);
            host = bridge;
            LOGGER.info("Rigid step region host: Tessellate");
        }
    }

    private static volatile TessellateBridge host;

    /** Region thread, called by the tick-end mixin inside the region scope: steps that region's partition. */
    public static void regionTickEnd(ServerLevel level) {
        int region = host.currentRegion();
        if (region >= 0) {
            RigidStep.stepPartition(level, region);
        }
    }

    /**
     * {@code api} absent => null; signature mismatch throws. Region id: {@code context.currentBinding().region().id()}
     * (public, unannotated Tessellate classes), -1 unbound.
     */
    @Nullable
    static TessellateBridge bind(String api, String context, ClassLoader loader) {
        Class<?> type;
        try {
            type = Class.forName(api, true, loader);
        } catch (ClassNotFoundException absent) {
            return null;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> contextType = Class.forName(context, true, loader);
            Class<?> bindingType = contextType.getMethod("currentBinding").getReturnType();
            Class<?> regionType = bindingType.getMethod("region").getReturnType();
            MethodHandle binding = lookup.findStatic(contextType, "currentBinding", MethodType.methodType(bindingType));
            MethodHandle region = lookup.findVirtual(bindingType, "region", MethodType.methodType(regionType));
            MethodHandle id = lookup.findVirtual(regionType, "id", MethodType.methodType(int.class));
            MethodHandle owns = lookup.findStatic(type, "ownsCurrentRegion",
                    MethodType.methodType(boolean.class, ServerLevel.class, BlockPos.class));
            MethodHandle execute = lookup.findStatic(type, "executeOnRegion",
                    MethodType.methodType(void.class, ServerLevel.class, BlockPos.class, Runnable.class));
            MethodHandle leased = lookup.findStatic(type, "executeOnMainThread",
                    MethodType.methodType(void.class, ServerLevel.class, BlockPos.class, Runnable.class));
            MethodType work = MethodType.methodType(void.class, ServerLevel.class, BlockPos.class, Runnable.class);
            return new TessellateBridge(
                    link(Supplier.class, "get", binding, MethodType.methodType(Object.class), MethodType.methodType(bindingType)),
                    link(Function.class, "apply", region, MethodType.methodType(Object.class, Object.class),
                            MethodType.methodType(regionType, bindingType)),
                    link(ToIntFunction.class, "applyAsInt", id, MethodType.methodType(int.class, Object.class),
                            MethodType.methodType(int.class, regionType)),
                    link(BiPredicate.class, "test", owns, MethodType.methodType(boolean.class, Object.class, Object.class),
                            MethodType.methodType(boolean.class, ServerLevel.class, BlockPos.class)),
                    link(RegionWork.class, "run", execute, work, work),
                    link(RegionWork.class, "run", leased, work, work));
        } catch (ReflectiveOperationException | LambdaConversionException mismatch) {
            throw new IllegalStateException("Tessellate region API mismatch", mismatch);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <F> F link(Class<?> type, String method, MethodHandle target, MethodType erased, MethodType instantiated)
            throws Throwable {
        return (F) LambdaMetafactory.metafactory(MethodHandles.lookup(), method, MethodType.methodType(type), erased,
                target, instantiated).getTarget().invoke();
    }

    @Override
    public int currentRegion() {
        Object binding = currentBinding.get();
        return binding == null ? -1 : regionId.applyAsInt(bindingRegion.apply(binding));
    }

    /** Non-entity body => false. */
    @Override
    public boolean owns(PhysicsBody body) {
        if (!(body instanceof Entity entity)) {
            return false;
        }
        return owns((ServerLevel) entity.level(), entity.blockPosition());
    }

    @Override
    public boolean owns(ServerLevel level, BlockPos pos) {
        return ownsCurrentRegion.test(level, pos);
    }

    @Override
    public void runOnOwner(ServerLevel level, BlockPos pos, Runnable work) {
        executeOnRegion.run(level, pos, work);
    }

    @Override
    public void runLeased(ServerLevel level, BlockPos pos, Runnable work) {
        executeLeased.run(level, pos, work);
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (t instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(t);
    }
}
