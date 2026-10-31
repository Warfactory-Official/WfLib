package com.wf.wflib.warhead.client;

import com.wf.wflib.warhead.WarheadSystem;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class WarheadClient {
    private WarheadClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(WarheadSystem.BOMBLET.get(), BombletRenderer::new);
            event.registerEntityRenderer(WarheadSystem.AGENT_ORANGE.get(), NoopRenderer::new);
        });
    }
}
