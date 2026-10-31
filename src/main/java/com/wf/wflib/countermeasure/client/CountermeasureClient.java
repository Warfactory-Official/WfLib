package com.wf.wflib.countermeasure.client;

import com.wf.wflib.countermeasure.api.Countermeasures;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class CountermeasureClient {
    private CountermeasureClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(Countermeasures.FLARE.get(), FlareRenderer::new);
            event.registerEntityRenderer(Countermeasures.SMOKE_GRENADE.get(), NoopRenderer::new);
            event.registerEntityRenderer(Countermeasures.APS_GRENADE.get(), NoopRenderer::new);
        });
    }
}
