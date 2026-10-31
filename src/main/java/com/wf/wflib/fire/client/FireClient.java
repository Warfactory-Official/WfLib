package com.wf.wflib.fire.client;

import com.wf.wflib.fire.FireSystem;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class FireClient {
    private FireClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(FireSystem.FIRE_LINGERING.get(), NoopRenderer::new));
    }
}
