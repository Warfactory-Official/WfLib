package com.wf.wflib.artifact.client;

import com.wf.wflib.artifact.api.ArtifactContent;
import com.wf.wflib.artifact.api.ArtifactItem;
import com.wf.wflib.artifact.api.client.ArtifactRenderer;
import com.wf.wflib.artifact.api.client.DetectorClient;
import com.wf.wflib.artifact.detect.client.ZoneArtifactClient;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring of {@code ArtifactSystem}; the entry class stays loadable on a bare server. */
public final class ArtifactClientSetup {
    private ArtifactClientSetup() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(DetectorClient.class);
        NeoForge.EVENT_BUS.register(ArtifactRenderer.class);
        NeoForge.EVENT_BUS.register(ZoneArtifactClient.class);
        modBus.addListener(FMLClientSetupEvent.class, event -> {
            DetectorClient.onClientSetup(event);
            event.enqueueWork(ArtifactRenderer::register);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, DetectorClient::onRegisterRenderers);
        modBus.addListener(RegisterGuiLayersEvent.class, DetectorClient::onRegisterLayers);
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> {
            DetectorClient.onClientExtensions(event);
            for (var holder : ArtifactContent.ARTIFACTS.values()) {
                ArtifactItem artifact = (ArtifactItem) holder.get();
                event.registerItem(new IClientItemExtensions() {
                    @Override
                    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return ArtifactRenderer.itemRenderer(artifact);
                    }
                }, artifact);
            }
        });
    }
}
