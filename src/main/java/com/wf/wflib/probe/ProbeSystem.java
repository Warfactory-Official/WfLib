package com.wf.wflib.probe;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.probe.api.ProbeJobs;
import com.wf.wflib.probe.client.ProbeClient;
import com.wf.wflib.probe.client.ProbeConfig;
import com.wf.wflib.probe.client.ProbeInput;
import com.wf.wflib.probe.client.ProbeKeys;
import com.wf.wflib.probe.client.ProbeModels;
import com.wf.wflib.probe.client.ProbeNodes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Look-at probe. {@code register}: server + shared lists ({@code ProbeActions}, data providers); client lists
 * (providers, nodes) are filled by dependents from client setup. Not a recon probe block: see {@code ProbeKind}.
 */
public final class ProbeSystem implements WFSystem {
    @Override
    public String id() {
        return "probe";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ProbeConfig.SPEC, "wflib-probe-client.toml");
        modBus.addListener(RegisterPayloadHandlersEvent.class, ProbeNetwork::register);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, ProbeNetwork::onLogout);
        NeoForge.EVENT_BUS.register(ProbeJobs.class);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.register(ProbeClient.class);
        modBus.register(ProbeKeys.class);
        modBus.register(ProbeModels.class);
        NeoForge.EVENT_BUS.register(ProbeInput.class);
        NeoForge.EVENT_BUS.register(ProbeNodes.class);
        modBus.addListener(net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted.class,
                com.wf.wflib.probe.api.client.ObjMeshes::onBakingComplete);
    }
}
