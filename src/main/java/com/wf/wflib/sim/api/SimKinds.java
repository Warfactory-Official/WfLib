package com.wf.wflib.sim.api;

import com.wf.wflib.core.api.WFRegistry;

import java.util.Collection;

/** Registered sim kinds; iteration = registration order (= prepare/resolve order within a slot). */
public final class SimKinds {

    private static final WFRegistry<SimKind<?>> KINDS = WFRegistry.create("sim_kinds");

    private SimKinds() {
    }

    /** Before the first level loads: a level's store is built from the kinds known then. */
    public static <R, K extends SimKind<R>> K register(K kind) {
        KINDS.register(kind.id(), kind);
        return kind;
    }

    public static Collection<SimKind<?>> all() {
        return KINDS.values();
    }
}
