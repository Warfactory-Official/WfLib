package com.wf.wflib.mist.client;

import com.wf.wflib.mist.MistSystem;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class MistClient {
    private MistClient() {
    }

    /** Clouds draw nothing themselves: pure particle effects (see {@link MistClientFX}). */
    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(MistSystem.MIST.get(), NoopRenderer::new));
    }
}
