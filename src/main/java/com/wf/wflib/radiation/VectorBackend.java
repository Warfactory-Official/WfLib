package com.wf.wflib.radiation;

import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.management.ManagementFactory;

/** JIT capability table; unsupported Vector API shapes silently use the allocating reference path. */
public enum VectorBackend {

    NONE(false, false, false, false, false, false),

    /** Graal's gather/transcendental paths box. */
    GRAAL(true, true, true, false, false, true),

    C2(true, true, true, true, true, true);

    private static final boolean HARDWARE_FMA = readUseFma();

    private final boolean arithmetic;
    private final boolean masks;
    private final boolean converts;
    private final boolean gathers;
    private final boolean transcendentals;
    private final boolean fma;

    VectorBackend(boolean arithmetic, boolean masks, boolean converts, boolean gathers, boolean transcendentals,
                  boolean fma) {
        this.arithmetic = arithmetic;
        this.masks = masks;
        this.converts = converts;
        this.gathers = gathers;
        this.transcendentals = transcendentals;
        this.fma = fma;
    }

    public boolean masks() {
        return masks;
    }

    public boolean arithmetic() {
        return arithmetic;
    }

    public boolean converts() {
        return converts;
    }

    public boolean gathers() {
        return gathers;
    }

    public boolean transcendentals() {
        return transcendentals;
    }

    /** Vector FMA requires both JIT support and hardware FMA. */
    public boolean fma() {
        return fma && HARDWARE_FMA;
    }

    boolean claimsFma() {
        return fma;
    }

    private static boolean readUseFma() {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            return Boolean.parseBoolean(bean.getVMOption("UseFMA").getValue());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Detect C2/Graal from {@code UseJVMCICompiler}, not a version string. */
    static VectorBackend detect() {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            return Boolean.parseBoolean(bean.getVMOption("UseJVMCICompiler").getValue()) ? GRAAL : C2;
        } catch (IllegalArgumentException e) {
            // No JVMCI flag means C2.
            return C2;
        } catch (RuntimeException | LinkageError e) {
            // Without diagnostics, use the narrower set.
            return GRAAL;
        }
    }
}
