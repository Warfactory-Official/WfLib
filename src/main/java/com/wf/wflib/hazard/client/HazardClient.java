package com.wf.wflib.hazard.client;

import com.wf.wflib.hazard.HazardSyncPacket;
import com.wf.wflib.hazard.api.HazardRules;
import com.wf.wflib.hazard.api.LivingHazardProps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Hazard client: sync receive, item tooltips, remote table build. */
public final class HazardClient {

    private HazardClient() {
    }

    public static void handleSync(HazardSyncPacket pkt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        LivingHazardProps p = LivingHazardProps.getData(player);
        p.radiation = pkt.radiation();
        p.digamma = pkt.digamma();
        p.radBuf = pkt.radBuf();
        p.asbestos = pkt.asbestos();
        p.blacklung = pkt.blacklung();
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        HazardRules.addHazardInfo(event.getItemStack(), event.getEntity(), event.getToolTip(), event.getFlags());
    }

    /** Integrated server already built the tables; a remote client builds them off the synced registry. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            HazardRules.applyDataPack(event.getPlayer().registryAccess());
        }
    }

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) HazardRules.clearCaches();
    }
}
