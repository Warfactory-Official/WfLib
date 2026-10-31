package com.wf.wflib.fx;

import com.wf.wflib.core.api.WFSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** Shared client effect infrastructure: Flywheel effect instancing and the dynamic lights. Client-only. */
public final class FxSystem implements WFSystem {
    @Override
    public String id() {
        return "fx";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
    }

    @Override
    public void registerClient(IEventBus modBus) {
        FxClient.register(modBus);
    }
}
