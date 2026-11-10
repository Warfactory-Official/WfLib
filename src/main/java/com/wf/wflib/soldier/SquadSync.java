package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps each owner's HUD current: their roster twice a second, and once more (empty) when the last soldier is gone. */
final class SquadSync {
    private static final Map<UUID, Boolean> HAD = new HashMap<>();

    private SquadSync() {}

    static void onServerTick(ServerTickEvent.Post e) {
        var server = e.getServer();
        if (server.getTickCount() % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof SoldierPlayer || !SquadPackets.canReceive(p)) continue;
            var roster = SquadPackets.snapshot(p);
            boolean has = !roster.members().isEmpty();
            if (has || HAD.getOrDefault(p.getUUID(), false)) PacketDistributor.sendToPlayer(p, roster);
            HAD.put(p.getUUID(), has);
        }
    }

    static void forget(UUID player) {
        HAD.remove(player);
    }
}
