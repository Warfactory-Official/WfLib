package com.wf.wflib.sim;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.sim.api.SimKind;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.sim.api.SimTargets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.wf.wflib.sim.api.SimScheduler;

public final class SimSystem implements WFSystem {
    @Override
    public String id() {
        return "sim";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        ServerTicks.LEVEL_PRE.register(ResourceLocation.fromNamespaceAndPath("wflib", "sim_pre"), TickOrder.SIM_PRE, SimScheduler::pre);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "sim_early"), TickOrder.SIM_EARLY, level -> SimScheduler.post(level, SimKind.Slot.EARLY));
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "sim_late"), TickOrder.SIM_LATE, level -> SimScheduler.post(level, SimKind.Slot.LATE));
        ServerTicks.STARTING.register(ResourceLocation.fromNamespaceAndPath("wflib", "sim"), TickOrder.SIM_START, server -> SimScheduler.startup());
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "sim"), TickOrder.SIM_STOP, server -> SimScheduler.shutdown(server.getAllLevels()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, SimTargets::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, SimTargets::onServerStopped);
    }
}
