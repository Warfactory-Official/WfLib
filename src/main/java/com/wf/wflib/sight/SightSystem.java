package com.wf.wflib.sight;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.sight.api.client.ReticleTextures;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public final class SightSystem implements WFSystem {
    @Override
    public String id() {
        return "sight";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.addListener(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(
                (barrier, manager, prepProfiler, reloadProfiler, background, game) -> barrier.wait(net.minecraft.Util.NIL_UUID)
                        .thenRunAsync(ReticleTextures::clear, game)));
    }
}
