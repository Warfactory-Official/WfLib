package com.wf.wflib.wiki;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.wiki.client.WikiClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** PDA wiki articles bound to items. Client-only: {@code register} is a no-op on a server. */
public final class WikiSystem implements WFSystem {
    @Override
    public String id() {
        return "wiki";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
    }

    @Override
    public void registerClient(IEventBus modBus) {
        WikiClient.register(modBus);
    }
}
