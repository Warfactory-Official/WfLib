package com.wf.wflib.contract;

import com.wf.wflib.contract.command.PmcCommands;
import com.wf.wflib.contract.item.PmcItems;
import com.wf.wflib.core.api.CreativeTabs;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.soldier.api.SoldierKillEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Private-military contracts: a board of jobs (kill, retrieve, deliver, clear) played out on a soldier site, reputation
 * and ranks, intel, smuggling, heat and hunter teams, the Black Site, engagements, and the Operations PDA app. Money
 * goes through the registered {@code Wallet} and parties through {@code Parties}, so it runs without an economy mod.
 */
public final class ContractSystem implements WFSystem {
    public static final Logger LOGGER = LoggerFactory.getLogger("wflib/contract");

    @Override
    public String id() {
        return "contract";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    /** Sites, soldiers and wallet come from soldier; the board and the sites' markers from map; the app from pda. */
    @Override
    public Set<String> requires() {
        return Set.of("soldier", "map", "pda");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        PmcItems.ITEMS.register(modBus);
        CreativeTabs.contribute("contract", 460, out -> {
            out.accept(PmcItems.DATA_DRIVE.get());
            out.accept(PmcItems.INTEL_DRIVE.get());
            out.accept(PmcItems.LAPTOP.get());
            out.accept(PmcItems.CACHE_MANIFEST.get());
            out.accept(PmcItems.NARCOTICS_BRICK.get());
        });
        container.registerConfig(ModConfig.Type.COMMON, com.wf.wflib.contract.difficulty.DifficultyConfig.SPEC, "wflib-contract.toml");
        modBus.addListener(RegisterPayloadHandlersEvent.class,
                e -> com.wf.wflib.contract.pda.PmcNet.register(e, snap -> com.wf.wflib.contract.client.PmcClient.accept(snap),
                        com.wf.wflib.contract.client.PmcClient::threat, com.wf.wflib.contract.client.PmcClient::missions));
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> e.addListener(new Contracts()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> PmcCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            if (e.getServer().getTickCount() % 20 == 0) {
                ContractService.tick(e.getServer());
                com.wf.wflib.contract.heat.HeatService.tick(e.getServer());
                com.wf.wflib.contract.blacksite.BlackSiteService.tick(e.getServer());
                com.wf.wflib.contract.daily.DailyBoard.tick(e.getServer());
                if (e.getServer().getTickCount() % 100 == 0) {
                    com.wf.wflib.contract.pda.PdaService.sendThreat(e.getServer());
                    com.wf.wflib.contract.pda.MissionSync.sendAll(e.getServer());
                }
                com.wf.wflib.contract.engagement.EngagementService.tick(e.getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p && p.getServer() != null) com.wf.wflib.contract.pda.MissionSync.send(p.getServer(), p);
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            com.wf.wflib.soldier.api.RandomForces.forget();
            com.wf.wflib.contract.daily.DailyBoard.stopped();
        });
        NeoForge.EVENT_BUS.addListener(SoldierKillEvent.class, e -> {
            if (e.killedByPlayer() && e.killer().getServer() != null) {
                ContractService.recordKill(e.killer().getServer(), e.killer().getUUID(), e.victimTeam());
                com.wf.wflib.contract.heat.HeatService.onKill(e.killer().getServer(), e.killer().getUUID(), e.victimTeam());
            }
        });
    }

    @Override
    public void registerClient(IEventBus modBus) {
        com.wf.wflib.contract.client.PmcClient.init(modBus);
    }
}
