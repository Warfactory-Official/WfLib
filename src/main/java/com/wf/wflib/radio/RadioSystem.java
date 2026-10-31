package com.wf.wflib.radio;


import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.radio.api.client.RadioCatalog;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;

public final class RadioSystem implements WFSystem {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WFLib.MODID);

    @Override
    public String id() {
        return "radio";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("radio", 380, out -> out.accept(com.wf.wflib.radio.api.RadioContent.LOUDSPEAKER.get()));
        ITEMS.register("loudspeaker", () -> new Item(new Item.Properties().stacksTo(1)));
        ITEMS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event ->
                event.registrar("radio-1").playToServer(RadioTunePacket.TYPE, RadioTunePacket.STREAM_CODEC,
                        (pkt, ctx) -> pkt.handle(ctx)));
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.addListener(RegisterClientReloadListenersEvent.class,
                event -> event.registerReloadListener(new RadioCatalog()));
    }
}
