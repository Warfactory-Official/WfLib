package com.wf.wflib.laser;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.laser.client.LaserBeams;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class LaserSystem implements WFSystem {
    @Override
    public String id() {
        return "laser";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event ->
                event.registrar("laser-1").playToClient(LaserBeamPacket.TYPE, LaserBeamPacket.STREAM_CODEC,
                        (pkt, ctx) -> ctx.enqueueWork(() -> LaserBeams.accept(pkt))));
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(LaserBeams.class);
    }
}
