package com.wf.wflib.core.api;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

import java.util.Set;

/** Entry point of one system; run by the owning mod's {@link SystemSet} only when the system resolves on. */
public interface WFSystem {
    String id();

    /** Always on; config cannot switch it off. */
    default boolean mandatory() {
        return false;
    }

    /** Off in a fresh config file. */
    default boolean defaultEnabled() {
        return true;
    }

    /**
     * Ids of systems this one needs on; dist-agnostic, client-only edges included. {@code <modid>:<sys>}; bare = own
     * mod's system, else wflib's.
     */
    default Set<String> requires() {
        return Set.of();
    }

    /** Effective on state in wflib's set (bare id); a system of another mod overrides with its qualified id. */
    default boolean enabled() {
        return WFSystems.enabled(id());
    }

    void register(IEventBus modBus, ModContainer container);

    default void registerClient(IEventBus modBus) {}
}
