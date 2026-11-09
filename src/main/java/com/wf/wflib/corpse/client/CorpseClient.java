package com.wf.wflib.corpse.client;

import com.wf.wflib.corpse.api.Corpses;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class CorpseClient {
    private CorpseClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(Corpses.ENTITY.get(), CorpseRenderer::new));
        modBus.addListener(RegisterMenuScreensEvent.class, e -> e.register(Corpses.MENU.get(), CorpseScreen::new));
        // The corpse is the body from the death tick on.
        NeoForge.EVENT_BUS.addListener(RenderPlayerEvent.Pre.class, e -> {
            if (e.getEntity().isDeadOrDying()) {
                e.setCanceled(true);
            }
        });
    }
}
