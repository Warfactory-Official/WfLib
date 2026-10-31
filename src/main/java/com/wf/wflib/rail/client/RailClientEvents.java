package com.wf.wflib.rail.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Drives the editor's autosave, and drops the routes when the connection that produced them goes away. */
public final class RailClientEvents {

    private RailClientEvents() {
    }

    /** The working route is saved from here, so closing the map does not strand the last edit. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        RailMapEditor.tickClient();
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // else: last server's railways stay drawn over the next world. Working copy is server-specific too.
        PublishedAlignments.clear();
        RouteOwnership.clear();
        RailMapEditor.resetSession();
    }
}
