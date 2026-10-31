package com.wf.wflib.view;

import com.wf.wflib.core.api.WFSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class ViewSystem implements WFSystem {
    @Override
    public String id() {
        return "view";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ViewClientConfig.SPEC, "wflib-view-client.toml");
    }
}
