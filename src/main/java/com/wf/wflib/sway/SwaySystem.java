package com.wf.wflib.sway;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.sway.client.SwayClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** Pain wobble ({@code sway.api.PainSway}) and the one camera-rotation writer ({@code sway.api.client.CameraSway}). */
public final class SwaySystem implements WFSystem {
    @Override
    public String id() {
        return "sway";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
    }

    @Override
    public void registerClient(IEventBus modBus) {
        SwayClient.register();
    }
}
