package com.wf.wflib.core.api;

import java.util.function.BooleanSupplier;

/** The night-vision key pressed while no goggles are worn: whoever {@link Hook#set}s this may take the press (the headlamp). Returns whether it did. */
public final class NightKey {
    public static final Hook<BooleanSupplier> UNHANDLED = Hook.of("night_key.unhandled", () -> false);

    private NightKey() {}
}
