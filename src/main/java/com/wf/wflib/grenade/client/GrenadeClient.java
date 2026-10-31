package com.wf.wflib.grenade.client;

import com.wf.wflib.audio.api.client.AudioMixer;
import com.wf.wflib.grenade.api.Grenades;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class GrenadeClient {
    private GrenadeClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(Grenades.CHARGE.get(), ChargeRenderer::new));
        modBus.addListener(FMLClientSetupEvent.class,
                event -> event.enqueueWork(() -> AudioMixer.addHearing(Flashbang::hearingLoss)));
    }
}
