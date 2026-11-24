package com.wf.wflib.physics.api;

import com.wf.wflib.physics.PhysicsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Engine config snapshot ({@code wflib-physics.toml}). A step captures {@link #current()} once; workers read only that
 * instance. {@link #override} wins over the file (benches, gametests).
 *
 * @param threads    step pool size; 0 = cores / 2
 * @param regionStep per-region stepping under a {@link RegionHost}
 */
public record PhysicsSettings(boolean asyncStep, boolean nativeNarrowphase, int threads, RegionStep regionStep) {

    public static final PhysicsSettings DEFAULTS = new PhysicsSettings(true, true, 0, RegionStep.AUTO);

    private static volatile PhysicsSettings override;

    /** {@code AUTO}: on iff a region host is installed. {@code ON} without a host: logged once, off. */
    public enum RegionStep {AUTO, ON, OFF}

    public static PhysicsSettings current() {
        PhysicsSettings forced = override;
        return forced != null ? forced : PhysicsConfig.get();
    }

    /** {@code null} clears. */
    public static void override(@Nullable PhysicsSettings settings) {
        override = settings;
    }

    public PhysicsSettings withAsync(boolean async) {
        return new PhysicsSettings(async, nativeNarrowphase, threads, regionStep);
    }

    public PhysicsSettings withRegionStep(RegionStep mode) {
        return new PhysicsSettings(asyncStep, nativeNarrowphase, threads, mode);
    }
}
