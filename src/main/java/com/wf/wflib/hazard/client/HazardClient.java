package com.wf.wflib.hazard.client;

import com.wf.wflib.WFLib;
import com.wf.wflib.hazard.HazardSyncPacket;
import com.wf.wflib.hazard.HazardSystem;
import com.wf.wflib.hazard.LivingHazardProps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Hazard client: sync receive, item tooltips, remote table build. */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
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
        HazardSystem.addHazardInfo(event.getItemStack(), event.getEntity(), event.getToolTip(), event.getFlags());
    }

    /** Integrated server already built the tables; a remote client builds them off the synced registry. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            HazardSystem.applyDataPack(event.getPlayer().registryAccess());
        }
    }

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) HazardSystem.clearCaches();
    }
}
