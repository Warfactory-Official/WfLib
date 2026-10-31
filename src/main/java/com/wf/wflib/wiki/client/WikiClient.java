package com.wf.wflib.wiki.client;

import com.wf.wflib.ui.api.input.KeyInterceptors;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring of {@code WikiSystem}; the entry class stays loadable on a bare server. */
public final class WikiClient {
    private WikiClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterClientReloadListenersEvent.class, event ->
                event.registerReloadListener(new WikiLoader()));
        modBus.addListener(RegisterKeyMappingsEvent.class, WikiAccess::register);
        NeoForge.EVENT_BUS.register(WikiAccess.class);
        KeyInterceptors.INTERCEPTORS.register(ResourceLocation.fromNamespaceAndPath("wflib", "wiki"), 0,
                WikiAccess::key);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(WikiApp::register));
    }
}
