package com.wf.wflib.particle;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.particle.api.WFParticles;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class ParticleSystem implements WFSystem {
    @Override
    public String id() {
        return "particle";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ParticleClientConfig.SPEC, "wflib-particle-client.toml");
        modBus.addListener(ParticleClientConfig::onLoad);
        WFParticles.register(modBus);
    }
}
