package com.wf.wflib.probe.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import com.wf.wflib.core.api.WFLib;

/** The probe's client footprint: panel, node and job layers above the crosshair, and the built-in providers. */
public final class ProbeClient {

    public static final ResourceLocation LAYER =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "probe");
    public static final ResourceLocation NODE_LAYER =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "probe_nodes");
    public static final ResourceLocation JOB_LAYER =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "probe_job");

    private ProbeClient() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, new ProbeOverlay());
        event.registerAbove(LAYER, NODE_LAYER, new ProbeNodes());
        event.registerAbove(NODE_LAYER, JOB_LAYER, new ProbeJobOverlay());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ProbeDefaults.register();
        });
    }
}
