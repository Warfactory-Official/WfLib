package com.wf.wflib.map;

import com.wf.wflib.pda.api.Pda;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.wf.wflib.map.api.Marker;
import com.wf.wflib.map.api.MarkerData;
import com.wf.wflib.core.api.WFLib;

/** Server end: edits from PDA carriers, side lists pushed to every online member on change and login. */
@EventBusSubscriber(modid = WFLib.MODID)
public final class Markers {
    private Markers() {
    }

    static void add(ServerPlayer player, ResourceLocation dimension, BlockPos pos, String label) {
        if (!Pda.carries(player)) {
            return;
        }
        String clean = label.strip();
        if (clean.isEmpty() || clean.length() > Marker.MAX_LABEL) {
            return;
        }
        UUID side = MarkerData.side(player);
        if (MarkerData.get(player.server).add(side, dimension, pos, clean)) {
            push(player.server, side);
        }
    }

    static void remove(ServerPlayer player, long id) {
        if (!Pda.carries(player)) {
            return;
        }
        UUID side = MarkerData.side(player);
        if (MarkerData.get(player.server).remove(side, id)) {
            push(player.server, side);
        }
    }

    /** Online players on {@code side}. */
    public static List<ServerPlayer> members(MinecraftServer server, UUID side) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (side.equals(MarkerData.side(player))) {
                out.add(player);
            }
        }
        return out;
    }

    private static void push(MinecraftServer server, UUID side) {
        MarkerSyncPacket packet = new MarkerSyncPacket(List.copyOf(MarkerData.get(server).of(side)));
        for (ServerPlayer player : members(server, side)) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new MarkerSyncPacket(
                    List.copyOf(MarkerData.get(player.server).of(MarkerData.side(player)))));
        }
    }
}
