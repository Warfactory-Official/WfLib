package com.wf.wflib.skin;

import com.mojang.serialization.Codec;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.skin.client.SkinClient;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;

/** Choosable looks ({@code Skinnable}) per stack; the picker is client-side. */
public final class SkinSystem implements WFSystem {
    @Override
    public String id() {
        return "skin";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        DeferredRegister.DataComponents components =
                DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);
        components.<Integer>registerComponentType("skin", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
        components.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("skin-1")
                .playToServer(SkinChoose.TYPE, SkinChoose.STREAM_CODEC, SkinChoose::handle));
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.register(SkinClient.class);
    }
}
