package com.wf.wflib.work;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.work.api.WorkData;
import com.wf.wflib.core.api.WFSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class WorkSystem implements WFSystem {
    @Override
    public String id() {
        return "work";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "work"), TickOrder.WORK, level -> WorkData.get(level).tick(level));
    }
}
