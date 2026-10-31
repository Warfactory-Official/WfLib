package com.wf.wflib.particle.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.wf.wflib.particle.api.WFParticles;
import com.wf.wflib.particle.api.client.AshParticle;
import com.wf.wflib.particle.api.client.ExplosionSmallParticle;
import com.wf.wflib.particle.api.client.MistParticle;
import com.wf.wflib.particle.api.client.RocketFlameParticle;
import com.wf.wflib.core.api.WFLib;


@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class WFParticleProviders {

    private WFParticleProviders() {
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WFParticles.EXPLOSION_SMALL.get(), ExplosionSmallParticle.Provider::new);
        event.registerSpriteSet(WFParticles.ROCKET_FLAME.get(), RocketFlameParticle.Provider::new);
        event.registerSpriteSet(WFParticles.ASH.get(), AshParticle.Provider::new);
        event.registerSpriteSet(WFParticles.MIST.get(), MistParticle.Provider::new);
        event.registerSpriteSet(WFParticles.SMOKE_PLUME.get(), SmokePlumeParticle.Provider::new);
        event.registerSpriteSet(WFParticles.SMOKE_SCREEN.get(), SmokePlumeParticle.Screen::new);
        event.registerSpriteSet(WFParticles.RAD_FOG.get(), RadFogParticle.Provider::new);
        // block_debris: registerSpecial, not registerSpriteSet; geometry comes from the block's own baked model.
        event.registerSpecial(WFParticles.BLOCK_DEBRIS.get(), new BlockDebrisParticle.Provider());
    }
}
