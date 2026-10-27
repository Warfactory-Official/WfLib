package com.wf.wflib.radiation;

import com.wf.wflib.unsafe.UnsafeHolder;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Locale;

/** Loads + reads {@code jdk.incubator.vector} without a JVM flag; scalar kernels otherwise. {@code -Dwflib.vector=false} opts out. */
public final class VectorApi {

    private static final String MODULE = "jdk.incubator.vector";

    public static final boolean AVAILABLE = bootstrap();

    private VectorApi() {
    }

    public static VectorBackend backend() {
        return Holder.BACKEND;
    }

    private static VectorBackend resolveBackend() {
        if (!AVAILABLE) return VectorBackend.NONE;
        String forced = System.getProperty("wflib.vector.backend");
        if (forced != null) {
            VectorBackend backend = VectorBackend.valueOf(forced.toUpperCase(Locale.ROOT));
            LoggerFactory.getLogger("wflib").info("Vector backend forced to {}", backend);
            return backend;
        }
        VectorBackend backend = VectorBackend.detect();
        LoggerFactory.getLogger("wflib").info("Vector backend {} on {} {}", backend,
                System.getProperty("java.vm.name"), System.getProperty("java.vm.version"));
        return backend;
    }

    private static final class Holder {
        static final VectorBackend BACKEND = resolveBackend();
    }

    /** {@code Lookup.IMPL_LOOKUP} via {@code sun.misc.Unsafe} static-field read. */
    private static MethodHandles.Lookup implLookup() throws ReflectiveOperationException {
        Field f = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        @SuppressWarnings("deprecation")
        Object base = UnsafeHolder.U.staticFieldBase(f);
        @SuppressWarnings("deprecation")
        long offset = UnsafeHolder.U.staticFieldOffset(f);
        return (MethodHandles.Lookup) UnsafeHolder.U.getObject(base, offset);
    }

    private static boolean bootstrap() {
        if (!Boolean.parseBoolean(System.getProperty("wflib.vector", "true"))) {
            LoggerFactory.getLogger("wflib").info("Vector API disabled by wflib.vector; using scalar kernels");
            return false;
        }
        try {
            Module self = VectorApi.class.getModule();
            Module vector = ModuleLayer.boot().findModule(MODULE).orElse(null);
            MethodHandles.Lookup impl = null;
            if (vector == null) {
                impl = implLookup();
                MethodHandle loadModule = impl.findStatic(Class.forName("jdk.internal.module.Modules"), "loadModule",
                        MethodType.methodType(Module.class, String.class));
                vector = (Module) loadModule.invokeExact(MODULE);
            }
            if (vector == null) return false;

            if (!self.canRead(vector)) {
                if (impl == null) impl = implLookup();
                MethodHandle addReads = impl.findStatic(Class.forName("jdk.internal.module.Modules"), "addReads",
                        MethodType.methodType(void.class, Module.class, Module.class));
                addReads.invokeExact(self, vector);
            }

            Class.forName("jdk.incubator.vector.DoubleVector", false, VectorApi.class.getClassLoader());
            boolean ok = self.canRead(vector);
            LoggerFactory.getLogger("wflib").info("Vector API {}", ok ? "resolved" : "unreadable; using scalar kernels");
            return ok;
        } catch (Throwable t) {
            LoggerFactory.getLogger("wflib").info("Vector API unavailable ({}); using scalar kernels", t.toString());
            return false;
        }
    }
}
