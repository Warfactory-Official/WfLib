package com.wf.wflib.map;

import com.wf.wflib.core.api.WFSystem;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.map.api.client.WFMaps;
import com.wf.wflib.map.client.MapApp;
import com.wf.wflib.map.api.client.MarkerClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.wf.wflib.core.api.WFLib;

public final class MapSystem implements WFSystem {
    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "map";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        active = true;
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var r = event.registrar("map-1");
            r.playToClient(MarkerSyncPacket.TYPE, MarkerSyncPacket.STREAM_CODEC,
                    (pkt, ctx) -> ctx.enqueueWork(() -> MarkerClient.accept(pkt)));
            r.playToServer(MarkerEditPacket.TYPE, MarkerEditPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        });
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            MapApp.register();
            WFMaps.TOOLS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "markers"), 0, new MarkerClient());
            if (ModList.get().isLoaded("xaeroworldmap")) {
                com.wf.wflib.map.client.xaero.XaeroWorldMapHost.attach();
            }
            if (ModList.get().isLoaded("xaerominimap")) {
                com.wf.wflib.map.client.xaero.XaeroMinimapHost.attach();
            }
        }));
    }
}
