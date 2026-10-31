package com.wf.wflib.barrel.client;

import com.wf.wflib.barrel.api.client.BarrelGlow;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class BarrelClient {
    private BarrelClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(BarrelGlow::register));
    }
}
