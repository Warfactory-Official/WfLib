package com.wf.wflib.packs;

import com.wf.wflib.core.api.WFSystem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Pack hosts: discovery, {@code pack.mcmeta} envelope, hash, client/server handshake, pack finder. */
public final class PacksSystem implements WFSystem {
    @Override
    public String id() {
        return "packs";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, PackHandshake::registerPayloads);
        modBus.addListener(EventPriority.LOW, RegisterConfigurationTasksEvent.class, PackHandshake::registerTasks);
        modBus.addListener(AddPackFindersEvent.class, PackFinder::onAddPackFinders);
    }
}
