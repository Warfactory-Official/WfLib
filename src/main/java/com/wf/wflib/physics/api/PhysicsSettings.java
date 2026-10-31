package com.wf.wflib.physics.api;

import com.wf.wflib.physics.PhysicsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Engine config snapshot ({@code wflib-physics.toml}). A step captures {@link #current()} once; workers read only that
 * instance. {@link #override} wins over the file (benches, gametests).
 *
 * @param threads step pool size; 0 = cores / 2
 */
public record PhysicsSettings(boolean asyncStep, boolean nativeNarrowphase, int threads) {

    public static final PhysicsSettings DEFAULTS = new PhysicsSettings(true, true, 0);

    private static volatile PhysicsSettings override;

    public static PhysicsSettings current() {
        PhysicsSettings forced = override;
        return forced != null ? forced : PhysicsConfig.get();
    }

    /** {@code null} clears. */
    public static void override(@Nullable PhysicsSettings settings) {
        override = settings;
    }

    public PhysicsSettings withAsync(boolean async) {
        return new PhysicsSettings(async, nativeNarrowphase, threads);
    }
}
