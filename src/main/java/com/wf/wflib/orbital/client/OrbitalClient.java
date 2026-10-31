package com.wf.wflib.orbital.client;

import com.wf.wflib.orbital.SatReplyPacket;
import com.wf.wflib.orbital.SatStatusPacket;
import com.wf.wflib.orbital.SpotOpenPacket;
import com.wf.wflib.orbital.SurveyOpenPacket;
import com.wf.wflib.orbital.api.SurveyTilePacket;
import com.wf.wflib.orbital.api.client.SurveyCache;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client wiring of {@code OrbitalSystem}; the entry class stays loadable on a bare server. */
public final class OrbitalClient {
    private OrbitalClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(SatellitesApp::register));
    }

    public static void surveyTile(SurveyTilePacket pkt) {
        SurveyCache.accept(pkt);
    }

    public static void surveyOpen(SurveyOpenPacket pkt) {
        Minecraft.getInstance().setScreen(new SurveyScreen(pkt.netId(), pkt.centreX(), pkt.centreZ()));
    }

    public static void satStatus(SatStatusPacket pkt) {
        SatellitesApp.accept(pkt);
    }

    public static void satReply(SatReplyPacket pkt) {
        SatellitesApp.accept(pkt);
    }

    public static void spot(SpotOpenPacket pkt) {
        SatellitesApp.spot(pkt.feedId());
    }
}
