package com.wf.wflib.contract.pda;

import com.wf.wflib.contract.ContractService;
import com.wf.wflib.contract.Contracts;
import com.wf.wflib.contract.heat.HeatService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Does what the PDA app asks, with the same rules as the {@code /pmc} commands, and answers with the new state. */
public final class PdaService {
    private static final int BRIBE_POINTS = 10;

    private PdaService() {
    }

    public static void send(ServerPlayer p, String notice) {
        PacketDistributor.sendToPlayer(p, new PmcNet.State(SnapshotBuilder.build(p, notice).toJson()));
    }

    /** Every player's tier for the world-stats overlay. */
    public static void sendThreat(net.minecraft.server.MinecraftServer server) {
        var difficulty = com.wf.wflib.contract.difficulty.Difficulty.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.connection != null) PacketDistributor.sendToPlayer(p, new PmcNet.Threat(com.wf.wflib.contract.difficulty.Tier.label(difficulty.tier(server, p.getUUID()))));
        }
    }

    static void act(ServerPlayer p, String verb, String arg) {
        String notice;
        try {
            notice = switch (verb) {
                case "accept" -> {
                    ResourceLocation id = ResourceLocation.tryParse(arg);
                    var def = id == null ? null : Contracts.get(id).orElse(null);
                    if (def == null) throw new ContractService.Refused("No such contract");
                    ContractService.accept(p, def);
                    yield "CONTRACT ACCEPTED";
                }
                case "abandon" -> {
                    ContractService.abandon(p);
                    yield "CONTRACT ABANDONED";
                }
                case "analyze" -> {
                    var lead = ContractService.analyze(p).orElseThrow();
                    yield "INTEL: " + lead.getValue().name() + " LEAD ON " + lead.getKey().title().toUpperCase(java.util.Locale.ROOT);
                }
                case "bribe" -> HeatService.bribe(p, arg, BRIBE_POINTS) ? "A CONTACT SMOOTHED THINGS OVER" : "NOTHING TO BUY OFF, OR YOU CANNOT AFFORD IT";
                default -> "UNKNOWN ACTION";
            };
        } catch (ContractService.Refused e) {
            notice = e.getMessage().toUpperCase(java.util.Locale.ROOT);
        }
        send(p, notice);
    }
}
