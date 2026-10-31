package com.wf.wflib.recon.client;

import com.wf.wflib.recon.PdaRadarPacket;
import com.wf.wflib.recon.ReconMapPacket;
import com.wf.wflib.recon.ReconSystem;
import com.wf.wflib.recon.ScopeFramePacket;
import com.wf.wflib.recon.TelemetryPacket;
import com.wf.wflib.recon.api.TelemetryLogPacket;
import com.wf.wflib.recon.telemetry.client.TelemetryClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client wiring of {@link ReconSystem}; payload sinks. */
public final class ReconClientSetup {
    private ReconClientSetup() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event ->
                event.registerBlockEntityRenderer(ReconSystem.RADAR_SCOPE_BE.get(), RadarScopeRenderer::new));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            ReconMapLayer.register();
            TelemetryApp.register();
            RadarApp.register();
        }));
    }

    public static void scopeFrame(ScopeFramePacket pkt) {
        ScopeCache.accept(pkt.frame());
    }

    public static void reconMap(ReconMapPacket pkt) {
        ReconMapClient.accept(pkt.dimension(), pkt.view());
    }

    public static void telemetry(TelemetryPacket pkt) {
        TelemetryClient.accept(pkt);
    }

    public static void telemetryLog(TelemetryLogPacket pkt) {
        TelemetryApp.accept(pkt);
    }

    public static void radar(PdaRadarPacket pkt) {
        RadarApp.accept(pkt);
    }
}
