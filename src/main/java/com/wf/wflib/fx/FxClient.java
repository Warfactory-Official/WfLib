package com.wf.wflib.fx;

import com.wf.wflib.fx.api.FlywheelEffectManager;
import com.wf.wflib.fx.api.WFLights;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring of {@code FxSystem}; the entry class stays loadable on a bare server. */
public final class FxClient {
    private FxClient() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(FlywheelEffectManager.class);
        NeoForge.EVENT_BUS.register(ShatterScheduler.class);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(WFLights::register));
    }
}
