package com.wf.wflib.view;

import com.wf.wflib.core.api.WFSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

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
        container.registerConfig(ModConfig.Type.CLIENT, ViewClientConfig.SPEC, "wflib-view-client.toml");
    }
}
