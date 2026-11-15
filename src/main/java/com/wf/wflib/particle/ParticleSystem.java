package com.wf.wflib.particle;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.particle.api.WFParticles;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

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
        container.registerConfig(ModConfig.Type.CLIENT, ParticleClientConfig.SPEC, "wflib-particle-client.toml");
        modBus.addListener(ParticleClientConfig::onLoad);
        WFParticles.register(modBus);
    }
}
