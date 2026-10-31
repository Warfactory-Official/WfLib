package com.wf.wflib.rail.client;

import com.wf.wflib.map.api.client.WFMaps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring of {@code RailSystem}; the entry class stays loadable on a bare server. */
public final class RailClient {
    private RailClient() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(RailClientEvents.class);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            AlignmentMapLayer.register();
            WFMaps.TOOLS.register(ResourceLocation.fromNamespaceAndPath("wflib", "rail_editor"), 0,
                    new RailMapEditor());
        }));
    }
}
