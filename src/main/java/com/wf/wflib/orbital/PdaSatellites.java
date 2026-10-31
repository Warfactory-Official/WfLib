package com.wf.wflib.orbital;

import com.wf.wflib.orbital.api.OrbitalNet;
import com.wf.wflib.orbital.api.SatId;
import com.wf.wflib.orbital.api.SatPayloads;
import com.wf.wflib.orbital.api.SatVerb;
import com.wf.wflib.orbital.api.SatView;
import com.wf.wflib.orbital.api.SpotFeeds;
import com.wf.wflib.pda.api.Pda;
import com.wf.wflib.pda.api.Uplink;
import com.wf.wflib.recon.api.ReconOwners;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Satellites app, server end: every PDA carrier gets their own net's birds each {@link #PERIOD_TICKS} (empty without
 * uplink: status, commands and imagery all need it). Commands only to own-net birds, through {@link OrbitalNet#commandBy}.
 */
public final class PdaSatellites {
    static final int PERIOD_TICKS = 40;
    /** Next-pass search horizon / step, ticks. */
    private static final int HORIZON = 24000;
    private static final int STEP = 40;

    private PdaSatellites() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % PERIOD_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (Pda.carries(player)) {
                PacketDistributor.sendToPlayer(player, status(player));
            }
        }
    }

    public static SatStatusPacket status(ServerPlayer player) {
        long net = ReconOwners.netOf(player);
        Uplink.Link link = Uplink.of(player);
        if (link == Uplink.Link.NONE || !Uplink.allows(player, net)) {
            return new SatStatusPacket(net, link, List.of());
        }
        ServerLevel level = player.serverLevel();
        List<SatView> views = OrbitalNet.onNet(level, net);
        List<SatStatusPacket.Sat> sats = new ArrayList<>(views.size());
        for (SatView v : views) {
            List<SatStatusPacket.Verb> verbs = new ArrayList<>();
            for (SatVerb verb : OrbitalNet.verbs(level, v.id())) {
                verbs.add(new SatStatusPacket.Verb(verb.name(), verb.arity(), verb.pure(), verb.describe()));
            }
            sats.add(new SatStatusPacket.Sat(v.id().value(), v.callsign(), v.payload().toString(), v.inContact(),
                    (float) v.fuelFraction(), (float) v.powerFraction(), (int) Math.floor(v.x()),
                    (int) Math.floor(v.z()),
                    OrbitalNet.nextPass(level, v.id(), player.getX(), player.getZ(), HORIZON, STEP), v.threatTicks(),
                    OrbitalNet.reply(level, v.id()), verbs));
        }
        return new SatStatusPacket(net, link, sats);
    }

    /**
     * O8 spot: own-net IMAGERY bird in contact whose footprint covers {@code (x, z)} now => {@link SpotFeeds} feed
     * for this player; ends with the pass.
     * @return feed id, 0 = refused
     */
    public static int spot(ServerPlayer player, long satId, double x, double z) {
        ServerLevel level = player.serverLevel();
        SatId id = new SatId(satId);
        long net = ReconOwners.netOf(player);
        SatView view = OrbitalNet.view(level, id).orElse(null);
        String refused = view == null || view.netId() != net || !Pda.carries(player) || !Uplink.allows(player, net)
                ? "err: no uplink to that satellite"
                : !view.payload().equals(SatPayloads.IMAGERY) ? "err: no imaging payload"
                : !view.inContact() ? "err: out of contact"
                : view.powerFraction() <= 0 ? "err: battery flat"
                : OrbitalNet.nextPass(level, id, x, z, 0, 1) != 0 ? "err: point not under the footprint"
                : null;
        if (refused != null) {
            PacketDistributor.sendToPlayer(player, new SatReplyPacket(satId, refused));
            return 0;
        }
        int feedId = SpotFeeds.open(player, id, x, z);
        PacketDistributor.sendToPlayer(player, new SatReplyPacket(satId, String.format(java.util.Locale.ROOT,
                "spot %d %d: live while overhead", (int) Math.floor(x), (int) Math.floor(z))));
        PacketDistributor.sendToPlayer(player, new SpotOpenPacket(feedId));
        return feedId;
    }

    /** Gametest seam as well as the packet handler. */
    public static void command(ServerPlayer player, long satId, String verb, List<String> args) {
        ServerLevel level = player.serverLevel();
        SatId id = new SatId(satId);
        long net = ReconOwners.netOf(player);
        SatView view = OrbitalNet.view(level, id).orElse(null);
        if (view == null || view.netId() != net || !Pda.carries(player) || !Uplink.allows(player, net)) {
            PacketDistributor.sendToPlayer(player, new SatReplyPacket(satId,
                    "err: no uplink to that satellite"));
            return;
        }
        String reply = OrbitalNet.commandBy(level, id, player.getGameProfile().getName(), verb,
                args.toArray(String[]::new));
        PacketDistributor.sendToPlayer(player, new SatReplyPacket(satId, reply));
        PacketDistributor.sendToPlayer(player, status(player));
    }
}
