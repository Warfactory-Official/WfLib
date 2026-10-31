package com.wf.wflib.anomaly.client;

import com.wf.wflib.particle.api.WFParticles;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring of {@code AnomalySystem}; the entry class stays loadable on a bare server. */
public final class AnomalyClientSetup {
    private AnomalyClientSetup() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(AnomalyClient.class);
        NeoForge.EVENT_BUS.register(AnomalyScreen.class);
        NeoForge.EVENT_BUS.register(AnomalyDebug.class);
        modBus.addListener(RegisterGuiLayersEvent.class, TimeSlipOverlay::onRegisterLayers);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(AnomalyClient::register));
        modBus.addListener(RegisterParticleProvidersEvent.class, event -> event.registerSpriteSet(
                WFParticles.ANOMALY_DEBRIS.get(), AnomalyDebrisParticle.Provider::new));
    }
}
