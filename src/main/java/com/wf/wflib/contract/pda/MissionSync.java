package com.wf.wflib.contract.pda;

import com.wf.wflib.contract.ContractDef;
import com.wf.wflib.contract.Contracts;
import com.wf.wflib.contract.Session;
import com.wf.wflib.contract.SessionBook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Tells each player which contracts they are on and where, for the mission log and the map waypoint. */
public final class MissionSync {
    private MissionSync() {
    }

    public static void sendAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(server, p);
    }

    public static void send(MinecraftServer server, ServerPlayer p) {
        if (p.connection == null || p instanceof com.wf.wflib.soldier.api.SoldierPlayer) return;
        long now = server.overworld().getGameTime();
        List<PmcNet.Mission> out = new ArrayList<>();
        for (Session s : SessionBook.get(server).all()) {
            if (!s.involves(p.getUUID())) continue;
            String title = Contracts.get(s.def()).map(ContractDef::title).orElse(s.def().getPath());
            var at = s.where();
            out.add(new PmcNet.Mission(title, at.getX(), at.getY(), at.getZ(), s.dimension(), (int) Math.max(0, Math.min(Integer.MAX_VALUE, s.deadline() - now))));
        }
        PacketDistributor.sendToPlayer(p, new PmcNet.Missions(out));
    }
}
