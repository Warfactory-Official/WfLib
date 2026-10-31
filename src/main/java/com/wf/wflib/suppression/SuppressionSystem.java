package com.wf.wflib.suppression;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.suppression.client.SuppressionFx;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** Near misses of others' rounds -> world blur + vignette. Client-only: {@code register} is a no-op on a server. */
public final class SuppressionSystem implements WFSystem {
    @Override
    public String id() {
        return "suppression";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
    }

    @Override
    public void registerClient(IEventBus modBus) {
        SuppressionFx.register(modBus);
    }
}
